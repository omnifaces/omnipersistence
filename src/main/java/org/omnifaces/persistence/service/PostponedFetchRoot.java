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
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static org.omnifaces.persistence.criteria.Criteria.isMultiValued;
import static org.omnifaces.persistence.service.BaseEntityService.isEmptyMultiValue;
import static org.omnifaces.utils.reflect.Reflections.invokeGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeSetter;
import static org.omnifaces.utils.stream.Streams.stream;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import org.omnifaces.persistence.Provider;
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
 * collection which is reached via singular associations only, also one nested in a singular association such as <code>owner.phones</code>, see
 * {@link FetchedCollection}. The database filters and orders the elements by the required criteria and the ordering of the page, so that its collation and its
 * placement of <code>NULL</code> apply. A negated criteria does not filter the elements. A collection nested in a collection is left as loaded. The entity
 * elements of a fetched collection which is not a <code>Set</code> are kept once, because a provider may repeat a nested fetched collection per root row. It
 * clears the persistence context before it sets the filtered and sorted collections, so that the provider does not flush them. All entities which were managed
 * by the {@link EntityManager} are then detached. Without postponed fetches there is no secondary query, and the entities stay managed.
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

    private static final ClassValue<Map<String, Boolean>> MAPPED_ORDER = new ClassValue<>() {

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

    /**
     * Selects the elements of a fetched collection as the database filters and orders them.
     */
    @FunctionalInterface
    interface ElementRanking {

        /**
         * Returns a row per element of given collection path of the root entities with given IDs which matches given criteria, in given ordering. Each row
         * holds the ID of the root entity, followed by the value of each given field of the element. A field equal to given collection path stands for the
         * element itself.
         *
         * @param collectionPath The path of the collection, reached via singular associations only.
         * @param ids The IDs of the root entities.
         * @param criteria The required criteria on the fields of the elements, keyed by their full path.
         * @param ordering The ordering on the fields of the elements, keyed by their full path.
         * @param fields The full paths of the fields to select.
         * @return The rows of the matching elements.
         */
        List<Object[]> rank(String collectionPath, List<?> ids, Map<String, Object> criteria, Map<String, Boolean> ordering, List<String> fields);

    }

    /**
     * Runs the secondary query per top level fetch path on given entities, and sets the filtered and sorted fetched collections onto them, see the class
     * javadoc.
     */
    public <E, T extends E> List<T> runPostponedFetches(
        Page page, EntityManager entityManager, Class<E> entityType, BiFunction<Root<E>, List<?>, Predicate> idRestriction, ElementRanking ranking,
        Function<Object, ?> idOf, java.util.function.Predicate<Object> isEntity, List<T> entities
    )
    {
        var fetchPaths = postponedFetches.stream().filter(path -> !path.contains(".")).sorted().toList();
        var distinctEntities = entities.stream().distinct().toList(); // May return duplicate entities when a collection join is used for filtering.
        var ids = distinctEntities.stream().map(idOf).toList();
        var fetchedEntities = new HashMap<String, Map<Object, Object>>(fetchPaths.size());
        var processedPaths = postponedFetches.stream().sorted().toList(); // A parent path sorts before its nested paths.
        var model = entityManager.getMetamodel().entity(entityType);
        var collections = new HashMap<String, FetchedCollection>();

        for (var path : processedPaths) {
            var collection = FetchedCollection.of(model, path, page, idOf, isEntity);

            if (collection != null) {
                collections.put(path, collection);
            }
        }

        for (var fetchPath : fetchPaths) {
            var fetchFields = new ArrayList<Entry<Class<?>, String>>();
            var collection = collections.get(fetchPath);
            var elementFields = collection != null ? collection.getElementFields() : List.<String>of();
            var criteriaQuery = buildFetchQuery(entityManager.getCriteriaBuilder(), entityType, idRestriction, fetchPath, ids, elementFields, fetchFields);
            var query = entityManager.createQuery(criteriaQuery);
            fetchFields.forEach(field -> provider.configureFetchField(query, field.getKey(), field.getValue()));
            var fetchedById = query.getResultList().stream().collect(toMap(idOf, Object.class::cast, (l, r) -> l));
            var fetchedByEntity = new IdentityHashMap<Object, Object>(distinctEntities.size());
            distinctEntities.forEach(entity -> fetchedByEntity.put(entity, fetchedById.get(idOf.apply(entity))));
            fetchedEntities.put(fetchPath, fetchedByEntity);
        }

        collections.values().forEach(collection -> collection.rank(ranking, ids));
        initializeNestedFetches(distinctEntities, fetchedEntities);
        var fetchedEntityProperties = new ArrayList<List<Object>>();
        buildPostponedFetches(collections, idOf, isEntity, distinctEntities, processedPaths, fetchedEntities, fetchedEntityProperties);
        entityManager.clear(); // Clear before applying the copies, or the provider flushes them.
        applyPostponedFetches(distinctEntities, processedPaths, fetchedEntityProperties); // Set filtered+sorted copies onto the now-detached entities.
        return distinctEntities;
    }

    /**
     * Builds a query which selects the entities with given IDs, with a left fetch join for given top level fetch path and for each postponed fetch nested in
     * it. It adds an entry to given fetch fields for each nested postponed fetch. It adds an entry for each attribute along each given element field as well,
     * because {@link FetchedCollection} reads them from the fetched elements. The query must load these, see
     * {@link Provider#configureFetchField(Query, Class, String)}.
     */
    private <E> CriteriaQuery<E> buildFetchQuery(
        CriteriaBuilder criteriaBuilder, Class<E> entityType, BiFunction<Root<E>, List<?>, Predicate> idRestriction, String fetchPath, List<?> ids,
        List<String> elementFields, List<Entry<Class<?>, String>> fetchFields
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
        elementFields.forEach(field -> addFetchFields(elementType, PATH_SEPARATOR.split(field), fetchFields));
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
        Map<String, FetchedCollection> collections, Function<Object, ?> idOf, java.util.function.Predicate<Object> isEntity, List<T> entities,
        List<String> paths, Map<String, Map<Object, Object>> fetchedEntities, List<List<Object>> fetchedEntityProperties
    )
    {
        entities.forEach(entity -> fetchedEntityProperties.add(processEntityFetches(entity, paths, fetchedEntities, collections, idOf, isEntity)));
    }

    private static <T> List<Object> processEntityFetches(
        T entity, List<String> paths, Map<String, Map<Object, Object>> fetchedEntities, Map<String, FetchedCollection> collections, Function<Object, ?> idOf,
        java.util.function.Predicate<Object> isEntity
    )
    {
        return paths.stream().map(path -> {
            var properties = PATH_SEPARATOR.split(path);
            var raw = getViaSingulars(requireNonNullElse(fetchedEntities.get(properties[0]).get(entity), entity), properties);

            if (!(raw instanceof Collection<?> col)) {
                return raw; // @OneToOne / @ManyToOne, or UNREACHABLE.
            }

            var elements = (raw instanceof Set ? col.stream() : distinctEntityElements(col, isEntity)).collect(toCollection(ArrayList<Object>::new));
            var collection = collections.get(path);

            if (collection != null) {
                collection.arrange(elements, idOf.apply(entity));
            }

            return (raw instanceof Set) ? new LinkedHashSet<>(elements) : elements;
        }).toList();
    }

    /**
     * Returns the attribute of given path from given type, or <code>null</code> when the path passes a plural attribute before its last attribute.
     *
     * @throws IllegalArgumentException When an attribute of given path does not exist.
     */
    private static Attribute<?, ?> findAttributeViaSingulars(ManagedType<?> type, String[] path) {
        var currentType = type;
        Attribute<?, ?> attribute = null;

        for (var attributeName : path) {
            if (currentType == null || attribute != null && attribute.isCollection()) {
                return null;
            }

            attribute = currentType.getAttribute(attributeName);
            currentType = elementTypeOf(attribute) instanceof ManagedType<?> managedType ? managedType : null;
        }

        return attribute;
    }

    /**
     * Returns whether given property of given type is annotated with <code>@OrderBy</code> or <code>@OrderColumn</code>.
     */
    private static boolean hasMappedOrder(Class<?> type, String property) {
        return MAPPED_ORDER.get(type).computeIfAbsent(property, key -> isAnnotated(type, property, OrderBy.class, OrderColumn.class));
    }

    /**
     * Returns whether given property of given type or of its superclasses has any of given annotations, on the field or on the getter.
     */
    @SafeVarargs
    private static boolean isAnnotated(Class<?> type, String property, Class<? extends Annotation>... annotationTypes) {
        var capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        var getterNames = Set.of("get" + capitalized, "is" + capitalized);

        for (var cls = type; cls != null; cls = cls.getSuperclass()) {
            var members = Stream.<AnnotatedElement>concat(
                Stream.of(cls.getDeclaredFields()).filter(field -> field.getName().equals(property)),
                Stream.of(cls.getDeclaredMethods()).filter(method -> method.getParameterCount() == 0 && getterNames.contains(method.getName()))
            );

            if (members.anyMatch(member -> Stream.of(annotationTypes).anyMatch(member::isAnnotationPresent))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether given required criteria restricts the fetched elements. A negated criteria, also as item of a multi-value, does not, because it restricts
     * the entities only: an entity matching <code>Not.value(List.of(a, b))</code> may still have an element <code>a</code>.
     */
    private static boolean restrictsElements(Object criteria) {
        if (criteria instanceof Not) {
            return false;
        }
        else if (isMultiValued(criteria)) {
            return !isEmptyMultiValue(criteria) && stream(criteria).filter(item -> !isEmptyMultiValue(item)).allMatch(PostponedFetchRoot::restrictsElements);
        }
        else {
            return true;
        }
    }

    /**
     * A fetched collection reached via singular associations only, such as <code>phones</code> or <code>owner.phones</code>. When the page has required
     * criteria or an ordering on the fields of its elements, the database selects the matching elements in the ordering of the page, see
     * {@link ElementRanking}, and this keeps and orders the fetched elements accordingly. An entity element is identified by its ID, an embeddable element by
     * the values of the fields which the criteria and the ordering reference, and a basic element by its value. A collection which the ordering of the page
     * does not reference is sorted by the natural order of its elements, unless it is annotated with <code>@OrderBy</code> or <code>@OrderColumn</code>, or its
     * elements are not <code>Comparable</code>.
     */
    private static final class FetchedCollection {

        private final String path;
        private final Map<String, Object> criteria;
        private final Map<String, Boolean> ordering;
        private final List<String> fields;
        private final boolean entityElements;
        private final boolean mappedOrder;
        private final Function<Object, ?> idOf;
        private final java.util.function.Predicate<Object> isEntity;
        private final Map<Object, Map<List<Object>, Integer>> positions = new HashMap<>();

        private FetchedCollection(
            String path, Map<String, Object> criteria, Map<String, Boolean> ordering, Type<?> elementType, boolean mappedOrder, Function<Object, ?> idOf,
            java.util.function.Predicate<Object> isEntity
        )
        {
            this.path = path;
            this.criteria = criteria;
            this.ordering = ordering;
            this.entityElements = elementType instanceof EntityType;
            this.mappedOrder = mappedOrder;
            this.idOf = idOf;
            this.isEntity = isEntity;
            this.fields = entityElements ? List.of(path) : findElementFields(elementType);
        }

        /**
         * Returns the fetched collection of given path from given root type, or <code>null</code> when the path is no collection reached via singular
         * associations only.
         */
        static FetchedCollection of(
            ManagedType<?> rootType, String path, Page page, Function<Object, ?> idOf, java.util.function.Predicate<Object> isEntity
        )
        {
            var attribute = findAttributeViaSingulars(rootType, PATH_SEPARATOR.split(path));

            if (attribute == null || !attribute.isCollection() || attribute instanceof MapAttribute) {
                return null;
            }

            var prefix = path + ".";
            var criteria = new LinkedHashMap<String, Object>();
            page.getRequiredCriteria().forEach((field, value) -> {
                if (field.startsWith(prefix) && restrictsElements(value)) {
                    criteria.put(field, value);
                }
            });
            var ordering = new LinkedHashMap<String, Boolean>();
            page.getOrdering().forEach((field, ascending) -> {
                if (field.equals(path) || field.startsWith(prefix)) {
                    ordering.put(field, ascending);
                }
            });
            var mappedOrder = hasMappedOrder(attribute.getDeclaringType().getJavaType(), attribute.getName());
            return new FetchedCollection(path, criteria, ordering, elementTypeOf(attribute), mappedOrder, idOf, isEntity);
        }

        /**
         * Returns the fields which the criteria and the ordering reference on given type of embeddable elements, as far as they are reached via singular
         * attributes. A basic element is its own field when the ordering references the collection itself.
         */
        private List<String> findElementFields(Type<?> elementType) {
            if (!(elementType instanceof ManagedType<?> managedType)) {
                return ordering.containsKey(path) ? List.of(path) : List.of();
            }

            var referencedFields = new LinkedHashSet<>(criteria.keySet());
            referencedFields.addAll(ordering.keySet());
            referencedFields.remove(path);
            var elementFields = new ArrayList<String>(referencedFields.size());

            for (var field : referencedFields) {
                var attribute = findAttributeViaSingulars(managedType, PATH_SEPARATOR.split(field.substring(path.length() + 1)));

                if (attribute != null && !attribute.isCollection()) {
                    elementFields.add(field);
                }
            }

            return List.copyOf(elementFields);
        }

        private boolean isRanked() {
            return !fields.isEmpty() && (!criteria.isEmpty() || !ordering.isEmpty());
        }

        /**
         * Returns the fields of the elements which this collection reads from the fetched elements, relative to the element.
         */
        List<String> getElementFields() {
            return entityElements || !isRanked()
                ? List.of()
                : fields.stream().filter(field -> !field.equals(path)).map(field -> field.substring(path.length() + 1)).toList();
        }

        /**
         * Runs given ranking on the root entities with given IDs, if the criteria or the ordering of the page reference this collection.
         */
        void rank(ElementRanking ranking, List<?> ids) {
            if (!isRanked()) {
                return;
            }

            for (var row : ranking.rank(path, ids, criteria, ordering, fields)) {
                var key = new ArrayList<>(row.length - 1);

                for (var i = 1; i < row.length; i++) {
                    key.add(toKeyValue(row[i]));
                }

                var rootPositions = positions.computeIfAbsent(row[0], id -> new HashMap<>());
                rootPositions.putIfAbsent(key, rootPositions.size());
            }
        }

        /**
         * Filters and sorts given fetched elements of the root entity with given ID.
         */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        void arrange(List<Object> elements, Object rootId) {
            if (isRanked()) {
                var rootPositions = positions.getOrDefault(rootId, Map.of());
                var elementPositions = new IdentityHashMap<Object, Integer>(elements.size());
                elements.forEach(element -> elementPositions.put(element, rootPositions.get(keyOf(element))));

                if (!criteria.isEmpty()) {
                    elements.removeIf(element -> elementPositions.get(element) == null);
                }

                if (!ordering.isEmpty()) {
                    elements.sort(comparing(element -> requireNonNullElse(elementPositions.get(element), Integer.MAX_VALUE)));
                    return;
                }
            }

            if (!mappedOrder && elements.stream().allMatch(Comparable.class::isInstance)) {
                elements.sort((Comparator) naturalOrder());
            }
        }

        private List<Object> keyOf(Object element) {
            if (entityElements) {
                return List.of(idOf.apply(element));
            }

            var key = new ArrayList<>(fields.size());

            for (var field : fields) {
                var value = field.equals(path) ? element : getViaSingulars(element, PATH_SEPARATOR.split(field.substring(path.length() + 1)));
                key.add(toKeyValue(value == UNREACHABLE ? null : value));
            }

            return key;
        }

        private Object toKeyValue(Object value) {
            return value != null && isEntity.test(value) ? idOf.apply(value) : value;
        }

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
