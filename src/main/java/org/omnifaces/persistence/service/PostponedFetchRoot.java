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

import static jakarta.persistence.criteria.JoinType.LEFT;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.reflect.Reflections.invokeGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeSetter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.Page;

/**
 * Root which postpones all issued fetches until after the paged query has run. The Jakarta Persistence specification leaves the effect of
 * {@code setFirstResult()} and {@code setMaxResults()} on a query with fetch joins over collections undefined (section "Query Execution"). This root keeps the
 * fetch joins out of the paged query, so that the range always applies to root entities.
 * <p>
 * BaseEntityService then runs one secondary query per top level fetch path on the returned root entities. The nested fetch paths of a top level fetch path are
 * fetched by the same query, so two nested collections of one top level fetch path form a cartesian product. Finally, this root filters and sorts each fetched
 * collection in memory according to the criteria and ordering of the page, if applicable. This applies to the direct properties of the items of a top level
 * fetch path only. It clears the persistence context before it sets the filtered and sorted collections, so that the provider does not flush them. All entities
 * which were managed by the {@link EntityManager} are then detached.
 * <p>
 * The join type of a fetch is ignored. The secondary query always uses a left fetch join.
 * <p>
 * On EclipseLink, BaseEntityService uses this root also when the page has no limit. EclipseLink returns a cartesian product for a fetch join whenever the query
 * has a range, and BaseEntityService always sets a range when the query has joins.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see PostponedFetch
 */
class PostponedFetchRoot<X> extends RootWrapper<X> {

    private final Set<String> postponedFetches;

    public PostponedFetchRoot(Root<X> wrapped) {
        super(wrapped);
        postponedFetches = new HashSet<>(2);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        return new PostponedFetch<>(postponedFetches, attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType joinType) {
        return fetch(attributeName);
    }

    @Override
    public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute) {
        return fetch(attribute.getName());
    }

    @Override
    public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute, JoinType joinType) {
        return fetch(attribute.getName());
    }

    @Override
    public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute) {
        return fetch(attribute.getName());
    }

    @Override
    public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute, JoinType joinType) {
        return fetch(attribute.getName());
    }

    public boolean hasPostponedFetches() {
        return !postponedFetches.isEmpty();
    }

    Set<String> getPostponedFetches() {
        return Set.copyOf(postponedFetches);
    }

    public <E extends BaseEntity<?>, T extends E> List<T> runPostponedFetches(Page page, EntityManager entityManager, Class<E> entityType, List<T> entities) {
        var fetchPaths = postponedFetches.stream().filter(path -> !path.contains(".")).sorted().toList();
        var distinctEntities = entities.stream().distinct().toList(); // May return duplicate entities when a collection join is used for filtering.
        var ids = distinctEntities.stream().map(BaseEntity::getId).toList();
        var fetchedEntities = new HashMap<String, Map<Object, Object>>(fetchPaths.size());

        for (var fetchPath : fetchPaths) {
            var fetched = entityManager.createQuery(buildFetchQuery(entityManager.getCriteriaBuilder(), entityType, fetchPath, ids)).getResultList();
            fetchedEntities.put(fetchPath, fetched.stream().collect(toMap(BaseEntity::getId, Object.class::cast, (l, r) -> l)));
        }

        initializeNestedFetches(distinctEntities, fetchedEntities);
        var fetchedEntityProperties = new ArrayList<List<Object>>();
        distinctEntities = buildAndSortPostponedFetches(page, distinctEntities, fetchPaths, fetchedEntities, fetchedEntityProperties);
        entityManager.clear(); // Clear before applying the copies, or the provider flushes them.
        applyPostponedFetches(distinctEntities, fetchPaths, fetchedEntityProperties); // Set filtered+sorted copies onto the now-detached entities.
        return distinctEntities;
    }

    /**
     * Builds a query which selects the entities with given IDs, with a left fetch join for given top level fetch path and for each postponed fetch nested in
     * it.
     */
    private <E> CriteriaQuery<E> buildFetchQuery(CriteriaBuilder criteriaBuilder, Class<E> entityType, String fetchPath, List<?> ids) {
        var query = criteriaBuilder.createQuery(entityType);
        var root = query.from(entityType);
        var fetches = new HashMap<String, FetchParent<?, ?>>();
        fetches.put("", root);

        for (var path : postponedFetches.stream().filter(path -> path.equals(fetchPath) || path.startsWith(fetchPath + ".")).sorted().toList()) {
            var lastDot = path.lastIndexOf('.');
            fetches.put(path, fetches.get(lastDot < 0 ? "" : path.substring(0, lastDot)).fetch(path.substring(lastDot + 1), LEFT));
        }

        return query.select(root).distinct(true).where(root.get(ID).in(ids));
    }

    /**
     * Loads each nested fetch path via its getters while the entities are still managed. OpenJPA does not load a nested fetch of a criteria query, and a
     * detached entity has no access to unloaded properties. On other providers the nested fetch paths are already loaded.
     */
    private void initializeNestedFetches(List<? extends BaseEntity<?>> entities, Map<String, Map<Object, Object>> fetchedEntities) {
        for (var path : postponedFetches) {
            var properties = path.split("\\.");

            if (properties.length > 1) {
                var fetched = fetchedEntities.get(properties[0]);
                entities.forEach(entity -> initializeFetchPath(fetched.get(entity.getId()), properties, 0));
            }
        }
    }

    private static void initializeFetchPath(Object object, String[] properties, int index) {
        if (object instanceof Collection<?> collection) {
            collection.forEach(item -> initializeFetchPath(item, properties, index));
        }
        else if (object != null && index < properties.length) {
            initializeFetchPath(invokeGetter(object, properties[index]), properties, index + 1);
        }
    }

    /**
     * Processes "postponed" fetches by copying, filtering, and sorting child collections/entities. Finally, re-sorts the parent entities based on the first
     * element of their sorted child lists.
     */
    private static <T extends BaseEntity<?>> List<T> buildAndSortPostponedFetches(
        Page page, List<T> entities, List<String> fetchPaths, Map<String, Map<Object, Object>> fetchedEntities, List<List<Object>> fetchedEntityProperties
    )
    {
        var rows = entities.stream().map(entity -> processEntityFetches(entity, fetchPaths, fetchedEntities, page)).collect(toList());
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

    private record EntityRow<T>(T entity, List<Object> fetchedEntityProperties) {
    }

    private static <T extends BaseEntity<?>> EntityRow<T> processEntityFetches(
        T entity, List<String> paths, Map<String, Map<Object, Object>> fetchedEntities, Page page
    )
    {
        var copies = paths.stream().map(path -> {
            var raw = invokeGetter(fetchedEntities.get(path).getOrDefault(entity.getId(), entity), path);

            if (!(raw instanceof Collection<?> col)) {
                return raw; // @OneToOne / @ManyToOne
            }

            var filtered = col.stream().filter(item -> matchesFetchFilters(item, collectFiltersForFetch(path, page.getRequiredCriteria())))
                .collect(toCollection(ArrayList::new));
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

    @SuppressWarnings("unchecked")
    private static Comparator<Object> buildFetchItemComparator(String fetchPath, Map<String, Boolean> ordering) {
        return ordering.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(fetchPath + "."))
            .map(entry -> {
                var subField = entry.getKey().substring(fetchPath.length() + 1);
                var ascending = entry.getValue();
                var fieldComp = comparing(obj -> (Comparable<Object>) invokeGetter(obj, subField), nullsLast(naturalOrder()));
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
                invokeSetter(entities.get(i), fetchPaths.get(j), fetchedEntityProperties.get(i).get(j));
            }
        }
    }

}
