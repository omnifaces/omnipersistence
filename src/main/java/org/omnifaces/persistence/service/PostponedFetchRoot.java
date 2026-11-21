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

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.criteria.JoinType.LEFT;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.Comparator.nullsLast;
import static java.util.Objects.requireNonNullElse;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static org.omnifaces.persistence.criteria.Criteria.isMultiValued;
import static org.omnifaces.persistence.service.BaseEntityService.createTypedCriteria;
import static org.omnifaces.persistence.service.BaseEntityService.isEmptyMultiValue;
import static org.omnifaces.utils.reflect.Reflections.invokeGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeSetter;
import static org.omnifaces.utils.stream.Streams.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.query.Page;

/**
 * Root which postpones all issued fetches, except for singular associations, until after the paged query has run. The Jakarta Persistence specification leaves
 * the effect of {@code setFirstResult()} and {@code setMaxResults()} on a query with fetch joins over collections undefined (section "Query Execution"). This
 * root keeps the fetch joins over collections out of the paged query, so that the range always applies to root entities.
 * <p>
 * Fetches of singular associations are actual fetches in the paged query, via {@link SingularFetch}, because they do not multiply the rows. Their fetched
 * columns are then also available to the ordering of a distinct query. A postponed fetch nested in a singular association postpones all its ancestor paths as
 * well, so these are then fetched once more by the secondary query.
 * <p>
 * BaseEntityService then runs one secondary query per top level fetch path on the returned root entities. The nested fetch paths of a top level fetch path are
 * fetched by the same query, so two nested collections of one top level fetch path form a cartesian product. Finally, this root filters and sorts each fetched
 * collection in memory according to the criteria and ordering of the page, if applicable. This applies to each fetched collection, also one nested in a
 * singular association such as <code>owner.phones</code>, and to the direct properties of its items. A collection nested in a collection is left as loaded. The
 * entity elements of a fetched collection which is not a <code>Set</code> are kept once, because a provider may repeat a nested fetched collection per root
 * row. It clears the persistence context before it sets the filtered and sorted collections, so that the provider does not flush them. All entities which were
 * managed by the {@link EntityManager} are then detached. Without postponed fetches there is no secondary query, and the entities stay managed.
 * <p>
 * The join type of a fetch is ignored. An actual fetch and the secondary query always use a left fetch join.
 * <p>
 * BaseEntityService uses this root also when the page has no limit, so that a fetch has the same effect on every page and provider: it never restricts the
 * entities of the page, and the required criteria of the page filter the fetched collections. EclipseLink returns a cartesian product for a fetch join whenever
 * the query has a range, and BaseEntityService always sets a range when the query has joins.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see PostponedFetch
 */
class PostponedFetchRoot<X> extends RootWrapper<X> {

    private static final ClassValue<Map<String, Boolean>> ENUMERATED_BY_STRING = new ClassValue<>() {

        @Override
        protected Map<String, Boolean> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }

    };

    private static final Pattern PATH_SEPARATOR = Pattern.compile("\\.");
    private static final Object UNREACHABLE = new Object();

    private final Provider provider;
    private final Set<String> postponedFetches;

    public PostponedFetchRoot(Root<X> wrapped, Provider provider) {
        super(wrapped);
        this.provider = provider;
        postponedFetches = new HashSet<>(2);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        return fetchOrPostpone(getWrapped(), getModel(), postponedFetches, null, attributeName);
    }

    /**
     * Fetches given attribute of given fetch parent with a left fetch join when it is a singular association. Else postpones it along with all paths of the
     * fetch parent, so that the secondary query of the top level fetch path can build the fetch joins down to it.
     *
     * @param <X> The source type of the returned fetch.
     * @param <Y> The target type of the returned fetch.
     * @param parent The fetch parent to fetch given attribute on.
     * @param parentType The managed type of the fetch parent.
     * @param postponedFetches The postponed fetch paths of the root.
     * @param parentPath The fetch path of the fetch parent, or <code>null</code> when it is the root.
     * @param attributeName The name of the attribute to fetch.
     * @return The {@link SingularFetch} or the {@link PostponedFetch} of given attribute.
     */
    static <X, Y> Fetch<X, Y> fetchOrPostpone(
        FetchParent<?, ?> parent, ManagedType<?> parentType, Set<String> postponedFetches, String parentPath, String attributeName
    )
    {
        var path = parentPath == null ? attributeName : parentPath + "." + attributeName;
        var attribute = parentType.getAttribute(attributeName);

        if (attribute.isAssociation() && !attribute.isCollection()) {
            return new SingularFetch<>(parent.<X, Y>fetch(attributeName, LEFT), postponedFetches, path);
        }

        for (var dot = path.indexOf('.'); dot > 0; dot = path.indexOf('.', dot + 1)) {
            postponedFetches.add(path.substring(0, dot));
        }

        return new PostponedFetch<>(postponedFetches, path);
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

    public <E, T extends E> List<T> runPostponedFetches(
        Page page, EntityManager entityManager, Class<E> entityType, BiFunction<Root<E>, List<?>, Predicate> idRestriction, Function<Object, ?> idOf,
        java.util.function.Predicate<Object> isEntity, boolean nullsLow, List<T> entities
    )
    {
        var fetchPaths = postponedFetches.stream().filter(path -> !path.contains(".")).sorted().toList();
        var distinctEntities = entities.stream().distinct().toList(); // May return duplicate entities when a collection join is used for filtering.
        var ids = distinctEntities.stream().map(idOf).toList();
        var fetchedEntities = new HashMap<String, Map<Object, Object>>(fetchPaths.size());
        var processedPaths = postponedFetches.stream().sorted().toList(); // A parent path sorts before its nested paths.
        var filters = processedPaths.stream().collect(
            toMap(
                identity(),
                path -> collectFiltersForFetch(path, page.getRequiredCriteria(), isEntity, idOf)
            )
        );

        for (var fetchPath : fetchPaths) {
            var fetchFields = new ArrayList<Entry<Class<?>, String>>();
            var criteriaQuery = buildFetchQuery(
                entityManager.getCriteriaBuilder(), entityType, idRestriction, fetchPath, ids, filters.get(fetchPath), fetchFields
            );
            var query = entityManager.createQuery(criteriaQuery);
            fetchFields.forEach(field -> provider.configureFetchField(query, field.getKey(), field.getValue()));
            var fetchedById = query.getResultList().stream().collect(toMap(idOf, Object.class::cast, (l, r) -> l));
            var fetchedByEntity = new IdentityHashMap<Object, Object>(distinctEntities.size());
            distinctEntities.forEach(entity -> fetchedByEntity.put(entity, fetchedById.get(idOf.apply(entity))));
            fetchedEntities.put(fetchPath, fetchedByEntity);
        }

        initializeNestedFetches(distinctEntities, fetchedEntities);
        var fetchedEntityProperties = new ArrayList<List<Object>>();
        buildPostponedFetches(page, nullsLow, filters, isEntity, distinctEntities, processedPaths, fetchedEntities, fetchedEntityProperties);
        entityManager.clear(); // Clear before applying the copies, or the provider flushes them.
        applyPostponedFetches(distinctEntities, processedPaths, fetchedEntityProperties); // Set filtered+sorted copies onto the now-detached entities.
        return distinctEntities;
    }

    /**
     * Builds a query which selects the entities with given IDs, with a left fetch join for given top level fetch path and for each postponed fetch nested in
     * it. It adds an entry to given fetch fields for each nested postponed fetch. It adds an entry for each attribute along each given filter as well, because
     * the filters read them from the fetched elements. The query must load these, see {@link Provider#configureFetchField(Query, Class, String)}.
     */
    private <E> CriteriaQuery<E> buildFetchQuery(
        CriteriaBuilder criteriaBuilder, Class<E> entityType, BiFunction<Root<E>, List<?>, Predicate> idRestriction, String fetchPath, List<?> ids,
        Map<String, Object> filters, List<Entry<Class<?>, String>> fetchFields
    )
    {
        var query = criteriaBuilder.createQuery(entityType);
        var root = query.from(entityType);
        var fetches = new HashMap<String, FetchParent<?, ?>>();
        fetches.put("", root);

        for (var path : postponedFetches.stream().filter(path -> path.equals(fetchPath) || path.startsWith(fetchPath + ".")).sorted().toList()) {
            var lastDot = path.lastIndexOf('.');
            var parent = fetches.get(lastDot < 0 ? "" : path.substring(0, lastDot));
            var attribute = path.substring(lastDot + 1);

            if (parent instanceof Fetch<?, ?> parentFetch && elementTypeOf(parentFetch.getAttribute()) instanceof ManagedType<?> parentType) {
                fetchFields.add(Map.entry(parentType.getJavaType(), attribute));
            }

            fetches.put(path, parent.fetch(attribute, LEFT));
        }

        var elementType = elementTypeOf(((Fetch<?, ?>) fetches.get(fetchPath)).getAttribute());
        filters.keySet().forEach(field -> addFetchFields(elementType, PATH_SEPARATOR.split(field), fetchFields));
        return query.select(root).distinct(true).where(idRestriction.apply(root, ids));
    }

    /**
     * Returns the type of the elements of given attribute when it is plural, else the type of given attribute.
     */
    private static Type<?> elementTypeOf(Attribute<?, ?> attribute) {
        return attribute instanceof PluralAttribute<?, ?, ?> pluralAttribute
            ? pluralAttribute.getElementType()
            : ((SingularAttribute<?, ?>) attribute).getType();
    }

    /**
     * Adds an entry to given fetch fields for each attribute along given path from given type, as far as the path goes through managed types.
     */
    private static void addFetchFields(Type<?> type, String[] path, List<Entry<Class<?>, String>> fetchFields) {
        var currentType = type;

        for (var attributeName : path) {
            if (!(currentType instanceof ManagedType<?> managedType)) {
                return;
            }

            fetchFields.add(Map.entry(managedType.getJavaType(), attributeName));

            try {
                currentType = elementTypeOf(managedType.getAttribute(attributeName));
            }
            catch (IllegalArgumentException unknownAttribute) {
                return;
            }
        }
    }

    /**
     * Loads each nested fetch path via its getters. OpenJPA does not load a nested fetch of a criteria query, and on a detached entity an unloaded property is
     * <code>null</code>, so on OpenJPA the fetch query also loads them via {@link Provider#configureFetchField(Query, Class, String)}. On other providers the
     * nested fetch paths are already loaded.
     */
    private void initializeNestedFetches(List<?> entities, Map<String, Map<Object, Object>> fetchedEntities) {
        for (var path : postponedFetches) {
            var properties = PATH_SEPARATOR.split(path);

            if (properties.length > 1) {
                var fetched = fetchedEntities.get(properties[0]);
                entities.forEach(entity -> initializeFetchPath(fetched.get(entity), properties, 0));
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
     * Processes "postponed" fetches by copying, filtering, and sorting child collections/entities, also those nested in singular associations, such as
     * <code>owner.phones</code>. The order of the entities is left as the paged query returned it.
     */
    private static <T> void buildPostponedFetches(
        Page page, boolean nullsLow, Map<String, Map<String, Object>> filters, java.util.function.Predicate<Object> isEntity, List<T> entities,
        List<String> paths, Map<String, Map<Object, Object>> fetchedEntities, List<List<Object>> fetchedEntityProperties
    )
    {
        entities.forEach(
            entity -> fetchedEntityProperties
                .add(processEntityFetches(entity, paths, fetchedEntities, page, nullsLow, filters, isEntity).fetchedEntityProperties())
        );
    }

    private record EntityRow<T>(T entity, List<Object> fetchedEntityProperties) {
    }

    private static <T> EntityRow<T> processEntityFetches(
        T entity, List<String> paths, Map<String, Map<Object, Object>> fetchedEntities, Page page, boolean nullsLow, Map<String, Map<String, Object>> filters,
        java.util.function.Predicate<Object> isEntity
    )
    {
        var copies = paths.stream().map(path -> {
            var properties = PATH_SEPARATOR.split(path);
            var raw = getViaSingulars(requireNonNullElse(fetchedEntities.get(properties[0]).get(entity), entity), properties);

            if (!(raw instanceof Collection<?> col)) {
                return raw; // @OneToOne / @ManyToOne, or UNREACHABLE.
            }

            var pathFilters = filters.get(path);
            var filtered = (raw instanceof Set ? col.stream() : distinctEntityElements(col, isEntity)).filter(item -> matchesFetchFilters(item, pathFilters))
                .collect(toCollection(ArrayList::new));
            var itemComp = buildFetchItemComparator(path, page.getOrdering(), nullsLow);

            if (itemComp != null) {
                filtered.sort(itemComp);
            }

            return (raw instanceof Set) ? new LinkedHashSet<>(filtered) : filtered;
        }).toList();

        return new EntityRow<>(entity, copies);
    }

    @SuppressWarnings("unchecked")
    private static Comparator<Object> buildFetchItemComparator(String fetchPath, Map<String, Boolean> ordering, boolean nullsLow) {
        Comparator<Comparable<Object>> nullsOrder = nullsLow ? nullsFirst(naturalOrder()) : nullsLast(naturalOrder());
        return ordering.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(fetchPath + "."))
            .map(entry -> {
                var subField = entry.getKey().substring(fetchPath.length() + 1);
                var ascending = entry.getValue();
                var fieldComp = comparing(obj -> sortKeyOf(obj, subField), nullsOrder);
                return ascending ? fieldComp : fieldComp.reversed();
            })
            .reduce(Comparator::thenComparing)
            .orElse(null);
    }

    /**
     * Returns the value of given field of given fetched element to sort it by. This is the name of an enum which is {@link Enumerated} by name, because the
     * database sorts such an enum by name.
     */
    @SuppressWarnings("unchecked")
    private static Comparable<Object> sortKeyOf(Object element, String field) {
        var value = invokeGetter(element, field);

        if (value instanceof Enum<?> enumValue) {
            var lastDot = field.lastIndexOf('.');
            Object owner = lastDot < 0 ? element : invokeGetter(element, field.substring(0, lastDot));

            if (isEnumeratedByString(owner.getClass(), field.substring(lastDot + 1))) {
                return (Comparable<Object>) (Comparable<?>) enumValue.name();
            }
        }

        return (Comparable<Object>) value;
    }

    private static boolean isEnumeratedByString(Class<?> type, String property) {
        return ENUMERATED_BY_STRING.get(type).computeIfAbsent(property, key -> findEnumerated(type, property).map(e -> e.value() == STRING).orElse(false));
    }

    /**
     * Returns the {@link Enumerated} of given property of given type or of its superclasses, on the field as well as on the getter.
     */
    private static Optional<Enumerated> findEnumerated(Class<?> type, String property) {
        var capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        var getterNames = Set.of("get" + capitalized, "is" + capitalized);

        for (var cls = type; cls != null; cls = cls.getSuperclass()) {
            var members = Stream.concat(
                Stream.of(cls.getDeclaredFields()).filter(field -> field.getName().equals(property)),
                Stream.of(cls.getDeclaredMethods()).filter(method -> method.getParameterCount() == 0 && getterNames.contains(method.getName()))
            );
            var enumerated = members.map(member -> member.getAnnotation(Enumerated.class)).filter(Objects::nonNull).findFirst();

            if (enumerated.isPresent()) {
                return enumerated;
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the criteria on the fields of the elements of given fetch path, mapped by field. An entity value is replaced by an {@link EntityIdentity}, so
     * that it matches the fetched entity with the same identifier, also when the entity does not implement <code>equals()</code>.
     */
    private static Map<String, Object> collectFiltersForFetch(
        String fetchPath, Map<String, Object> requiredCriteria, java.util.function.Predicate<Object> isEntity, Function<Object, ?> idOf
    )
    {
        var filters = new LinkedHashMap<String, Object>();

        for (var entry : requiredCriteria.entrySet()) {
            if (entry.getKey().startsWith(fetchPath + ".")) {
                filters.put(entry.getKey().substring(fetchPath.length() + 1), toEntityIdentityIfEntity(entry.getValue(), isEntity, idOf));
            }
        }

        return filters;
    }

    private static Object toEntityIdentityIfEntity(Object value, java.util.function.Predicate<Object> isEntity, Function<Object, ?> idOf) {
        if (isMultiValued(value)) {
            return stream(value).map(item -> toEntityIdentityIfEntity(item, isEntity, idOf)).toList();
        }
        else if (value != null && isEntity.test(value)) {
            return new EntityIdentity(idOf.apply(value), idOf);
        }
        else {
            return value;
        }
    }

    /**
     * Matches a fetched entity by its identifier.
     */
    private record EntityIdentity(Object id, Function<Object, ?> idOf) {

        boolean matches(Object entity) {
            return entity != null && Objects.equals(id, idOf.apply(entity));
        }

    }

    private static boolean matchesFetchFilters(Object item, Map<String, Object> filters) {
        for (var entry : filters.entrySet()) {
            if (!isEmptyMultiValue(entry.getValue()) && !appliesFetchFilter(entry.getValue(), invokeGetter(item, entry.getKey()))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns whether given fetched element value applies to given criteria. A negated criteria, also as item of a multi-value, applies to every element,
     * because it restricts the entities only: an entity matching <code>Not.value(List.of(a, b))</code> may still have an element <code>a</code>. A plain value
     * applies as the typed criteria which {@link BaseEntityService} builds the SQL predicate from.
     */
    private static boolean appliesFetchFilter(Object criteria, Object fieldValue) {
        if (criteria instanceof Not) {
            return true;
        }
        else if (criteria instanceof Criteria<?> c) {
            return c.applies(fieldValue);
        }
        else if (criteria instanceof EntityIdentity entityIdentity) {
            return entityIdentity.matches(fieldValue);
        }
        else if (isMultiValued(criteria)) {
            return stream(criteria).anyMatch(item -> appliesFetchFilter(item, fieldValue));
        }
        else {
            var typedCriteria = fieldValue == null ? null : createTypedCriteria(typeOf(fieldValue), criteria);
            return typedCriteria != null ? typedCriteria.applies(fieldValue) : Objects.equals(criteria, fieldValue);
        }
    }

    private static Class<?> typeOf(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.getDeclaringClass() : value.getClass();
    }

    /**
     * Returns the elements of given collection, with each entity element once by identity. Other elements are kept as is, because a list may hold a value more
     * than once.
     */
    private static Stream<?> distinctEntityElements(Collection<?> collection, java.util.function.Predicate<Object> isEntity) {
        var entityElements = Collections.newSetFromMap(new IdentityHashMap<>(collection.size()));
        return collection.stream().filter(item -> !isEntity.test(item) || entityElements.add(item));
    }

    /**
     * Returns the value of given property path of given object, or {@link #UNREACHABLE} when the path passes a collection or <code>null</code> before its last
     * property, such as <code>categories.parent</code>.
     */
    private static Object getViaSingulars(Object object, String[] properties) {
        var value = object;

        for (var i = 0; i < properties.length; i++) {
            if (value == null || value instanceof Collection) {
                return UNREACHABLE;
            }

            value = invokeGetter(value, properties[i]);
        }

        return value;
    }

    private static void applyPostponedFetches(List<?> entities, List<String> paths, List<List<Object>> fetchedEntityProperties) {
        for (var i = 0; i < entities.size(); i++) {
            for (var j = 0; j < paths.size(); j++) {
                var value = fetchedEntityProperties.get(i).get(j);

                if (value != UNREACHABLE) {
                    invokeSetter(entities.get(i), paths.get(j), value);
                }
            }
        }
    }

}
