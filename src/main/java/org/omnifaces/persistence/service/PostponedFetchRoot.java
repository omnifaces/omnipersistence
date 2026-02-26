/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.service;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.omnifaces.utils.reflect.Reflections.invokeGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeSetter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Root;

import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.Page;

/**
 * Both OpenJPA and EclipseLink stubbornly apply the range (offset/limit) to join rows rather than root entity rows when a fetch join is present, resulting in fewer root entities returned than the requested limit.
 * This root will postpone all issued fetches so BaseEntityService can ultimately execute them as a secondary JPQL query to initialize the fetched collections on the already-returned root entities.
 * After the postponed fetches run, this root performs an in-memory filter and sort of each loaded collection according to the active page criteria and ordering, if applicable.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see PostponedFetch
 */
class PostponedFetchRoot<X> extends RootWrapper<X> {

    private Set<String> postponedFetches;

    public PostponedFetchRoot(Root<X> wrapped) {
        super(wrapped);
        postponedFetches = new HashSet<>(2);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        return new PostponedFetch<>(postponedFetches, attributeName);
    }

    public boolean hasPostponedFetches() {
        return !postponedFetches.isEmpty();
    }

    public Set<String> getPostponedFetches() {
        return postponedFetches;
    }

    public <E extends BaseEntity<?>, T extends E> List<T> runPostponedFetches(Page page, EntityManager entityManager, Class<E> entityType, List<T> entities) {
        var fetchPaths = List.copyOf(getPostponedFetches());
        var ids = entities.stream().map(BaseEntity::getId).toList();

        for (var fetchPath : fetchPaths) {
            entityManager.createQuery("SELECT DISTINCT e FROM " + entityType.getSimpleName() + " e JOIN FETCH e." + fetchPath + " WHERE e.id IN :ids", entityType).setParameter("ids", ids).getResultList();
            // No need to explicitly set in root entities; 1st level cache will sort out this while still inside the same transaction.
        }

        var distinctEntities = entities.stream().distinct().toList(); // May return duplicate entities when a collection join is used for filtering.
        var fetchedEntityProperties = new ArrayList<List<Object>>();
        distinctEntities = buildAndSortPostponedFetches(page, distinctEntities, fetchPaths, fetchedEntityProperties);
        entityManager.clear(); // Detach all managed entities to prevent JPA provider from flushing spurious UPDATEs for entities loaded by the postponed fetch.
        applyPostponedFetches(distinctEntities, fetchPaths, fetchedEntityProperties); // Set filtered+sorted copies onto the now-detached entities.
        return distinctEntities;
    }

    /**
     * Processes "postponed" fetches by copying, filtering, and sorting child collections/entities.
     * Finally, re-sorts the parent entities based on the first element of their sorted child lists.
     */
    private static <T> List<T> buildAndSortPostponedFetches(Page page, List<T> entities, List<String> fetchPaths, List<List<Object>> fetchedEntityProperties) {
        var rows = entities.stream().map(entity -> processEntityFetches(entity, fetchPaths, page)).collect(toList());
        Comparator<EntityRow<T>> rowComparator = buildRowComparator(page, fetchPaths);

        if (rowComparator != null) {
            rows.sort(rowComparator);
        }

        var sortedEntities = new ArrayList<T>(entities.size());

        for (var row : rows) {
            sortedEntities.add(row.entity);
            fetchedEntityProperties.add(row.fetchedEntityProperties);
        }

        return sortedEntities;
    }

    private static record EntityRow<T>(T entity, List<Object> fetchedEntityProperties) {}

    private static <T> EntityRow<T> processEntityFetches(T entity, List<String> paths, Page page) {
        var copies = paths.stream().map(path -> {
            var raw = invokeGetter(entity, path);

            if (!(raw instanceof Collection<?> col)) {
                return raw; // @OneToOne / @ManyToOne
            }

            var filtered = col.stream().filter(item -> matchesFetchFilters(item, collectFiltersForFetch(path, page.getRequiredCriteria()))).collect(toCollection(ArrayList::new));
            var itemComp = buildFetchItemComparator(path, page.getOrdering());

            if (itemComp != null) {
                filtered.sort(itemComp);
            }

            return (raw instanceof Set) ? new LinkedHashSet<>(filtered) : filtered;
        }).toList();

        return new EntityRow<>(entity, copies);
    }

    private static <T> Comparator<EntityRow<T>> buildRowComparator(Page page, List<String> paths) {
        Comparator<EntityRow<T>> rowComparator = null;

        for (int i = 0; i < paths.size(); i++) {
            var itemComp = buildFetchItemComparator(paths.get(i), page.getOrdering());

            if (itemComp == null) {
                continue;
            }

            final int fetchIndex = i;
            Comparator<EntityRow<T>> propertyComparator = (rowA, rowB) -> {
                var propertyA = rowA.fetchedEntityProperties.get(fetchIndex);
                var propertyB = rowB.fetchedEntityProperties.get(fetchIndex);

                if (propertyA instanceof List<?> listA && propertyB instanceof List<?> listB) {
                    var firstA = listA.isEmpty() ? null : listA.get(0);
                    var firstB = listB.isEmpty() ? null : listB.get(0);
                    return itemComp.compare(firstA, firstB);
                }

                return 0;
            };

            rowComparator = (rowComparator == null) ? propertyComparator : rowComparator.thenComparing(propertyComparator);
        }

        return rowComparator;
    }

    @SuppressWarnings("rawtypes")
    private static Comparator<Object> buildFetchItemComparator(String fetchPath, Map<String, Boolean> ordering) {
        return ordering.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(fetchPath + "."))
                .map(entry -> {
                    var subField = entry.getKey().substring(fetchPath.length() + 1);
                    var ascending = entry.getValue();
                    var fieldComp = comparing(obj -> (Comparable) invokeGetter(obj, subField), nullsLast(naturalOrder()));
                    return ascending ? fieldComp : fieldComp.reversed();
                })
                .reduce(Comparator::thenComparing)
                .orElse(null);
    }

    private static Map<String, Object> collectFiltersForFetch(String fetchPath, Map<String, Object> requiredCriteria) {
        var filters = new LinkedHashMap<String, Object>();

        for (var entry : requiredCriteria.entrySet()) {
            if (entry.getKey().startsWith(fetchPath + ".")) {
                filters.put(entry.getKey().substring(fetchPath.length() + 1), entry.getValue());
            }
        }

        return filters;
    }

    private static boolean matchesFetchFilters(Object item, Map<String, Object> filters) {
        for (var entry : filters.entrySet()) {
            if (!appliesFetchFilter(entry.getValue(), invokeGetter(item, entry.getKey()))) {
                return false;
            }
        }

        return true;
    }

    private static boolean appliesFetchFilter(Object criteria, Object fieldValue) {
        if (criteria instanceof Criteria<?> c) {
            return c.applies(fieldValue);
        }

        if (criteria instanceof Set<?> set) {
            return set.contains(fieldValue);
        }

        return Objects.equals(criteria, fieldValue);
    }

    private static void applyPostponedFetches(List<?> entities, List<String> fetchPaths, List<List<Object>> fetchedEntityProperties) {
        for (var i = 0; i < entities.size(); i++) {
            for (var j = 0; j < fetchPaths.size(); j++) {
                invokeSetter(entities.get(i), fetchPaths.get(j), fetchedEntityProperties.get(i).get(j)); // Set copy onto the detached entity, bypassing JPA tracking.
            }
        }
    }
}
