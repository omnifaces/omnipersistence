package org.omnifaces.persistence.service;

import static java.util.stream.Collectors.toList;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.map;
import static org.omnifaces.utils.stream.Streams.stream;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NamedQuery;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.Attribute;

import org.omnifaces.persistence.constraint.Constraint;
import org.omnifaces.persistence.constraint.Not;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.exception.NonDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.NonDeletable;
import org.omnifaces.persistence.model.TimestampedEntity;
import org.omnifaces.persistence.model.VersionedEntity;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.utils.collection.PartialResultList;

/**
 * <p>
 * Base entity service. Let your {@link Stateless} service classes extend from this. Ideally, you would not anymore have
 * the need to inject the {@link EntityManager} in your service class and it would suffice to just delegate all
 * persistence actions to methods of this abstract class.
 * <p>
 * You only need to let your entities extend from one of the following mapped super classes:
 * <ul>
 * <li>{@link BaseEntity}
 * <li>{@link TimestampedEntity}
 * <li>{@link VersionedEntity}
 * </ul>
 *
 * @param <I> The generic ID type, usually {@link Long}.
 * @param <E> The generic base entity type.
 */
public abstract class BaseEntityService<I extends Comparable<I> & Serializable, E extends BaseEntity<I>> {

	private static final Map<Class<?>, SimpleEntry<Class<?>, Class<?>>> TYPE_MAPPINGS = new ConcurrentHashMap<>();
	private static final Predicate[] PREDICATE_ARRAY = new Predicate[0];
	private final Class<I> identifierType;
	private final Class<E> entityType;

	@PersistenceContext
	private EntityManager entityManager;


	// Init -----------------------------------------------------------------------------------------------------------

	/**
	 * The constructor initializes the type mapping.
	 * The <code>I</code> and <code>E</code> will be resolved to a concrete <code>Class&lt;?&gt;</code>.
	 */
	@SuppressWarnings("unchecked")
	public BaseEntityService() {
		SimpleEntry<Class<?>, Class<?>> typeMapping = TYPE_MAPPINGS.computeIfAbsent(getClass(), BaseEntityService::computeTypeMapping);
		identifierType = (Class<I>) typeMapping.getKey();
		entityType = (Class<E>) typeMapping.getValue();
	}

	private static SimpleEntry<Class<?>, Class<?>> computeTypeMapping(Class<?> type) {
		Type actualType = type.getGenericSuperclass();
		Map<TypeVariable<?>, Type> typeMapping = new HashMap<>();

		while (!(actualType instanceof ParameterizedType) || !BaseEntityService.class.equals(((ParameterizedType) actualType).getRawType())) {
			if (actualType instanceof ParameterizedType) {
				Class<?> rawType = (Class<?>) ((ParameterizedType) actualType).getRawType();
				TypeVariable<?>[] typeParameters = rawType.getTypeParameters();

				for (int i = 0; i < typeParameters.length; i++) {
					Type typeArgument = ((ParameterizedType) actualType).getActualTypeArguments()[i];
					typeMapping.put(typeParameters[i], typeArgument instanceof TypeVariable ? typeMapping.get(typeArgument) : typeArgument);
				}

				actualType = rawType;
			}

			actualType = ((Class<?>) actualType).getGenericSuperclass();
		}

		return new SimpleEntry<>(getActualTypeArgument(actualType, 0, typeMapping), getActualTypeArgument(actualType, 1, typeMapping));
	}

	private static Class<?> getActualTypeArgument(Type type, int index, Map<TypeVariable<?>, Type> typeMapping) {
		Type actualTypeArgument = ((ParameterizedType) type).getActualTypeArguments()[index];

		if (actualTypeArgument instanceof TypeVariable) {
			actualTypeArgument = typeMapping.get(actualTypeArgument);
		}

		return (Class<?>) actualTypeArgument;
	}


	// Standard actions -----------------------------------------------------------------------------------------------

	/**
	 * Create an instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement.
	 * @param jpqlStatement The Java Persistence Query Language statement to be executed.
	 * @return An instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement.
	 */
	protected TypedQuery<E> createQuery(String jpqlStatement) {
		return entityManager.createQuery(jpqlStatement, entityType);
	}

	/**
	 * Create an instance of {@link TypedQuery} for executing a Java Persistence Query Language statement identified
	 * by the given name.
	 * @param name The name of the Java Persistence Query Language statement defined in metadata, which can be either
	 * a {@link NamedQuery} or a <code>&lt;persistence-unit&gt;&lt;mapping-file&gt;</code>.
	 * @return An instance of {@link TypedQuery} for executing a Java Persistence Query Language statement identified
	 * by the given name.
	 */
	protected TypedQuery<E> createNamedQuery(String name) {
		return entityManager.createNamedQuery(name, entityType);
	}

	/**
	 * Find entity by given ID.
	 * @param id Entity ID to find entity for.
	 * @return Found entity, if any.
	 */
	public Optional<E> findById(I id) {
		return Optional.ofNullable(getById(id));
	}

	/**
	 * Get entity by given ID.
	 * @param id Entity ID to get entity by.
	 * @return Found entity, or <code>null</code> if there is none.
	 */
	public E getById(I id) {
		return entityManager.find(entityType, id);
	}

	/**
	 * Get all entities. The default ordering is by ID, descending.
	 * @return All entities.
	 */
	public List<E> getAll() {
		return createQuery("SELECT e FROM " + entityType.getSimpleName() + " e ORDER BY id DESC").getResultList();
	}

	/**
	 * Persist given entity.
	 * @param entity Entity to persist.
	 * @return Entity ID.
	 * @throws IllegalEntityStateException When entity has an ID.
	 */
	public I persist(E entity) {
		if (entity.getId() != null) {
			throw new IllegalEntityStateException(entity, "Entity has an ID. Use update() instead.");
		}

		entityManager.persist(entity);
		return entity.getId();
	}

	/**
	 * Update given entity.
	 * @param entity Entity to update.
	 * @return Updated entity.
	 * @throws IllegalEntityStateException When entity has no ID.
	 */
	public E update(E entity) {
		if (entity.getId() == null) {
			throw new IllegalEntityStateException(entity, "Entity has no ID. Use persist() instead.");
		}

		return entityManager.merge(entity);
	}

	/**
	 * Save given entity. This will automatically determine based on presence of entity ID whether to
	 * {@link #persist(BaseEntity)} or to {@link #update(BaseEntity)}.
	 * @param entity Entity to save.
	 * @return Saved entity.
	 */
	public E save(E entity) {
		if (entity.getId() == null) {
			return getById(persist(entity));
		}
		else {
			return update(entity);
		}
	}

	/**
	 * Refresh given entity. This will discard any changes in given entity.
	 * @param entity Entity to refresh.
	 * @throws IllegalEntityStateException When entity has no ID or has in meanwhile been deleted.
	 */
	public void refresh(E entity) {
		if (entity.getId() == null) {
			throw new IllegalEntityStateException(entity, "Entity has no ID.");
		}

		E managed = getById(entity.getId());

		if (managed == null) {
			throw new IllegalEntityStateException(entity, "Entity has in meanwhile been deleted.");
		}

		entityManager.getMetamodel().entity(managed.getClass()).getAttributes().forEach(a -> map(a.getJavaMember(), managed, entity));
	}

	/**
	 * Delete given entity.
	 * @param entity Entity to delete.
	 * @throws NonDeletableEntityException When entity has {@link NonDeletable} annotation set.
	 * @throws IllegalEntityStateException When entity has no ID or has in meanwhile been deleted.
	 */
	public void delete(E entity) {
		if (entity.getClass().isAnnotationPresent(NonDeletable.class)) {
			throw new NonDeletableEntityException(entity);
		}

		entityManager.remove(manage(entity));
	}

	/**
	 * Make given entity managed.
	 * @param entity Entity to manage.
	 * @return The managed entity.
	 * @throws IllegalEntityStateException When entity has no ID or has in meanwhile been deleted.
	 */
	protected final E manage(E entity) {
		if (entity.getId() == null) {
			throw new IllegalEntityStateException(entity, "Entity has no ID.");
		}

		if (entityManager.contains(entity)) {
			return entity;
		}

		E managed = getById(entity.getId());

		if (managed == null) {
			throw new IllegalEntityStateException(entity, "Entity has in meanwhile been deleted.");
		}

		return managed;
	}


	// Lazy fetching actions ------------------------------------------------------------------------------------------

	/**
	 * Fetch lazy collections of given entity on given getters.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch lazy collections on.
	 * @param getters Getters of those lazy collections.
	 */
	@SafeVarargs
	protected final void fetchLazyCollections(E entity, Function<E, Collection<?>>... getters) {
		stream(getters).forEach(getter -> getter.apply(entity).size());
	}

	/**
	 * Fetch lazy maps of given entity on given getters.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch lazy maps on.
	 * @param getters Getters of those lazy collections.
	 */
	@SafeVarargs
	protected final void fetchLazyMaps(E entity, Function<E, Map<?, ?>>... getters) {
		stream(getters).forEach(getter -> getter.apply(entity).size());
	}

	/**
	 * Fetch all lazy blobs of given entity.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch all blobs on.
	 */
	protected final void fetchLazyBlobs(E entity) {
		E managed = entityManager.merge(entity);

		for (Attribute<?, ?> a : entityManager.getMetamodel().entity(managed.getClass()).getDeclaredSingularAttributes()) {
			if (a.getJavaType() == byte[].class) {
				String name = Character.toUpperCase(a.getName().charAt(0)) + a.getName().substring(1);
				byte[] blob = (byte[]) invokeMethod(managed, "get" + name);
				invokeMethod(entity, "set" + name, blob);
			}
		}
	}


	// Paging actions -------------------------------------------------------------------------------------------------

	/**
	 * Functional interface to fine-grain a JPA criteria query for any of {@link #getPage(Page, boolean)} methods.
	 * You do not need it directly. Just supply a lambda. Below is an usage example:
	 * <pre>
	 * &#64;Stateless
	 * public class YourEntityService extends BaseEntityService&lt;YourEntity&gt; {
	 *
	 *     public void getPageOfFooType(Page page, boolean count) {
	 *         return getPage(page, count, (criteriaBuilder, criteriaQuery, root) -&gt; {
	 *             criteriaQuery.where(criteriaBuilder.equals(root.get("type"), Type.FOO));
	 *         });
	 *     }
	 *
	 * }
	 * </pre>
	 * @param <E> The generic base entity type.
	 */
	@FunctionalInterface
	protected static interface QueryBuilder<E> {
		void build(CriteriaBuilder criteriaBuilder, CriteriaQuery<E> criteriaQuery, Root<E> root);
	}

	/**
	 * Here you can in your subclass define the callback method which needs to be invoked before any of
	 * {@link #getPage(Page, boolean)} methods is called. For example, to set an {@link EntityManager} property.
	 * The default implementation returns a no-op callback.
	 * @return The callback method which is invoked before any of {@link #getPage(Page, boolean)} methods is called.
	 */
	protected Consumer<EntityManager> beforePage() {
		return entityManager -> noop();
	}

	/**
	 * Here you can in your subclass define the callback method which needs to be invoked after any of
	 * {@link #getPage(Page, boolean)} methods is called. For example, to remove an {@link EntityManager} property.
	 * The default implementation returns a no-op callback.
	 * @return The callback method which is invoked after any of {@link #getPage(Page, boolean)} methods is called.
	 */
	protected Consumer<EntityManager> afterPage() {
		return entityManager -> noop();
	}

	/**
	 * Returns a partial result list based on given {@link Page}. This will by default cache the results.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @return A partial result list based on given {@link Page}.
	 */
	public PartialResultList<E> getPage(Page page, boolean count) {
		return getPage(page, count, true);
	}

	/**
	 * Returns a partial result list based on given {@link Page}.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param cacheable Whether the results should be cacheable.
	 * @return A partial result list based on given {@link Page}.
	 */
	protected PartialResultList<E> getPage(Page page, boolean count, boolean cacheable) {
		return getPage(page, count, cacheable, (builder, query, root) -> noop());
	}

	/**
	 * Returns a partial result list based on given {@link Page} and {@link QueryBuilder}. This will by default cache
	 * the results.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param queryBuilder This allows fine-graining the JPA criteria query.
	 * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
	 */
	protected PartialResultList<E> getPage(Page page, boolean count, QueryBuilder<E> queryBuilder) {
		return getPage(page, count, true, queryBuilder);
	}

	/**
	 * Returns a partial result list based on given {@link Page} and {@link QueryBuilder}.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param cacheable Whether the results should be cacheable.
	 * @param queryBuilder This allows fine-graining the JPA criteria query.
	 * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
	 */
	protected PartialResultList<E> getPage(Page page, boolean count, boolean cacheable, QueryBuilder<E> queryBuilder) {
		beforePage().accept(entityManager);

		try {
			CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
			CriteriaQuery<E> criteriaQuery = criteriaBuilder.createQuery(entityType);
			Root<E> root = criteriaQuery.from(entityType);
			queryBuilder.build(criteriaBuilder, criteriaQuery, root);
			buildOrderBy(page, criteriaBuilder, criteriaQuery, root);
			Map<String, Object> parameterValues = buildRestrictions(page, criteriaBuilder, criteriaQuery, root);
			String cacheRegion = page.toString();

			TypedQuery<E> typedQuery = entityManager
				.createQuery(criteriaQuery)
				.setFirstResult(page.getOffset())
				.setMaxResults(page.getLimit())
				.setHint("org.hibernate.cacheable", cacheable) // TODO: EclipseLink? JPA 2.0?
				.setHint("org.hibernate.cacheRegion", cacheRegion);

			parameterValues.entrySet().forEach(parameter -> typedQuery.setParameter(parameter.getKey(), parameter.getValue()));
			List<E> entities = typedQuery.getResultList();

			int estimatedTotalNumberOfResults = -1;

			if (count) {
				CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
				Subquery<E> subQuery = countQuery.subquery(entityType);
				root = countQuery.from(entityType);
				subQuery.select(subQuery.from(entityType)).distinct(true);
				Predicate allRestrictions = criteriaQuery.getRestriction();

				if (allRestrictions != null) {
					subQuery.where(allRestrictions);
				}

				countQuery.select(criteriaBuilder.count(root)).where(criteriaBuilder.in(root).value(subQuery)); // https://stackoverflow.com/a/12076584/157882

				TypedQuery<Long> typedCountQuery = entityManager
					.createQuery(countQuery)
					.setHint("org.hibernate.cacheable", cacheable) // TODO: EclipseLink? JPA 2.0?
					.setHint("org.hibernate.cacheRegion", cacheRegion + "count");

				parameterValues.entrySet().forEach(parameter -> typedCountQuery.setParameter(parameter.getKey(), parameter.getValue()));
				estimatedTotalNumberOfResults = typedCountQuery.getSingleResult().intValue();
			}

			return new PartialResultList<>(entities, page.getOffset(), estimatedTotalNumberOfResults);
		}
		finally {
			afterPage().accept(entityManager);
		}
	}


	// Sorting actions ------------------------------------------------------------------------------------------------

	private void buildOrderBy(Page page, CriteriaBuilder criteriaBuilder, CriteriaQuery<E> criteriaQuery, Root<E> root) {
		Map<String, Boolean> ordering = page.getOrdering();

		if (ordering.isEmpty() || page.getLimit() - page.getOffset() == 1) {
			return;
		}

		criteriaQuery.orderBy(stream(ordering).map(order -> {
			Path<Object> path = resolvePath(root, order.getKey());
			return order.getValue() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path);
		}).collect(toList()));
	}


	// Searching actions -----------------------------------------------------------------------------------------------

	private Map<String, Object> buildRestrictions(Page page, CriteriaBuilder criteriaBuilder, CriteriaQuery<E> criteriaQuery, Root<E> root) {
		Map<String, Object> parameterValues = new HashMap<>(page.getRequiredCriteria().size() + page.getOptionalCriteria().size());
		List<Predicate> requiredPredicates = buildPredicates(page.getRequiredCriteria(), criteriaBuilder, root, parameterValues);
		List<Predicate> optionalPredicates = buildPredicates(page.getOptionalCriteria(), criteriaBuilder, root, parameterValues);
		Predicate restriction = null;

		if (!optionalPredicates.isEmpty()) {
			Predicate[] optionalRestrictions = optionalPredicates.toArray(PREDICATE_ARRAY);
			restriction = criteriaBuilder.or(optionalRestrictions);
		}

		if (!requiredPredicates.isEmpty()) {
			Predicate requiredRestrictions = criteriaBuilder.and(requiredPredicates.toArray(PREDICATE_ARRAY));
			restriction = (restriction != null) ? criteriaBuilder.and(requiredRestrictions, restriction) : requiredRestrictions;
		}

		if (restriction != null) {
			Predicate originalRestriction = criteriaQuery.getRestriction();

			if (originalRestriction != null) {
				restriction = criteriaBuilder.and(originalRestriction, restriction);
			}

			criteriaQuery.where(restriction);
		}

		return parameterValues;
	}

	private List<Predicate> buildPredicates(Map<String, Object> criteria, CriteriaBuilder criteriaBuilder, Root<E> root, Map<String, Object> parameterValues) {
		return stream(criteria).map(parameter -> {
			String key = parameter.getKey();
			Path<?> path;

			try {
				path = resolvePath(root, key);
			}
			catch (IllegalArgumentException ignore) {
				return null; // Likely custom search key referring non-existent property.
			}

			/*
			if (Collection.class.isAssignableFrom(type)) {
				predicate = buildIn(root.join(key), searchKey, Arrays.asList(value), criteriaBuilder, parameterValues); // TODO
			}*/


			Class<?> type = ID.equals(key) ? identifierType : path.getJavaType();
			return buildPredicate(path, type, key, parameter.getValue(), criteriaBuilder, root, parameterValues);
		}).filter(Objects::nonNull).collect(toList());
	}

	@SuppressWarnings("unchecked")
	private Predicate buildPredicate(Path<?> path, Class<?> type, String key, Object criteria, CriteriaBuilder criteriaBuilder, Root<E> root, Map<String, Object> parameterValues) {
		String searchKey = key.replace(".", "_") + "Search" + parameterValues.size();
		Object value = criteria;
		boolean negated = value instanceof Not;
		Predicate predicate;

		if (negated) {
			value = ((Not) value).getValue();
		}

		if (value instanceof Constraint<?> && ((Constraint<?>) value).getValue() == null) {
			value = null;
		}

		if (value == null) {
			predicate = criteriaBuilder.isNull(path);
		}
		else if (value instanceof Constraint) {
			predicate = ((Constraint<?>) value).build(searchKey, criteriaBuilder, path, parameterValues);
		}
		else if (value instanceof Iterable<?> || value.getClass().isArray()) {
			List<Predicate> predicates = stream(value)
				.map(item -> buildPredicate(path, type, key, item, criteriaBuilder, root, parameterValues))
				.filter(Objects::nonNull)
				.collect(toList());

			if (predicates.isEmpty()) {
				return null; // Likely invalid custom search value.
			}

			predicate = criteriaBuilder.or(predicates.toArray(PREDICATE_ARRAY));
		}
		else if (type.isEnum()) {
			try {
				predicate = buildEqual(path, searchKey, parseEnum((Path<Enum<?>>) path, value), criteriaBuilder, parameterValues);
			}
			catch (IllegalArgumentException ignore) {
				return null; // Likely custom search value referring non-existent enum value.
			}
		}
		else if (Number.class.isAssignableFrom(type)) {
			try {
				predicate = buildEqual(path, searchKey, parseNumber((Path<Number>) path, value), criteriaBuilder, parameterValues);
			}
			catch (NumberFormatException ignore) {
				return null; // Likely custom search value referring non-numeric value.
			}
		}
		else if (Boolean.class.isAssignableFrom(type)) {
			try {
				predicate = buildEqual(path, searchKey, parseBoolean((Path<Boolean>) path, value), criteriaBuilder, parameterValues);
			}
			catch (IllegalArgumentException ignore) {
				return null; // Likely custom search value referring non-boolean value.
			}
		}
		else if (String.class.isAssignableFrom(type) || value instanceof String) {
			predicate = buildLike(path, searchKey, value.toString(), criteriaBuilder, parameterValues);
		}
		else {
			predicate = buildUnsupportedPredicate(path, searchKey, value, criteriaBuilder, parameterValues);

			if (predicate == null) {
				throw new UnsupportedOperationException("You may not return null from buildUnsupportedPredicate().");
			}
		}

		if (negated) {
			predicate = criteriaBuilder.not(predicate);
		}

		return predicate;
	}

	private Predicate buildEqual(Path<?> path, String key, Object value, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
		parameterValues.put(key, value);
		return criteriaBuilder.equal(path, criteriaBuilder.parameter(path.getJavaType(), key));
	}

	private Predicate buildLike(Path<?> path, String key, Object value, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
		parameterValues.put(key, value);
		return criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.function("str", String.class, path)), criteriaBuilder.parameter(String.class, key));
	}

	private Predicate buildIn(Join<?, ?> join, String key, Collection<?> values, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
		List<Expression<?>> in = new ArrayList<>(values.size());

		for (Object value : values) {
			String searchKey = key + value;
			parameterValues.put(searchKey, value);
			in.add(criteriaBuilder.parameter(value.getClass(), searchKey));
		}

		return join.in(in.toArray(new Expression[in.size()]));
	}

	/**
	 * You can override this method if you want more fine grained control over how enums values are parsed for predicates.
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param value Value to be parsed to enum.
	 * @return The parsed enum value.
	 * @throws IllegalArgumentException When value cannot be parsed as enum.
	 */
	protected Enum<?> parseEnum(Path<Enum<?>> path, Object value) throws IllegalArgumentException {
		if (value instanceof Enum) {
			return (Enum<?>) value;
		}
		else {
			for (Enum<?> enumConstant : path.getJavaType().getEnumConstants()) {
				if (enumConstant.name().equalsIgnoreCase(value.toString())) {
					return enumConstant;
				}
			}
		}

		throw new IllegalArgumentException(value.toString());
	}

	/**
	 * You can override this method if you want more fine grained control over how number values are parsed for predicates.
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param value Value to be parsed to number.
	 * @return The parsed number value.
	 * @throws NumberFormatException When value cannot be parsed as number.
	 */
	protected Number parseNumber(Path<Number> path, Object value) throws NumberFormatException {
		if (value instanceof Number) {
			return (Number) value;
		}
		else if (BigDecimal.class.isAssignableFrom(path.getJavaType())) {
			return new BigDecimal(value.toString());
		}
		else if (BigInteger.class.isAssignableFrom(path.getJavaType())) {
			return new BigInteger(value.toString());
		}
		else if (Integer.class.isAssignableFrom(path.getJavaType())) {
			return Integer.valueOf(value.toString());
		}
		else {
			return Long.valueOf(value.toString());
		}
	}

	/**
	 * You can override this method if you want more fine grained control over how boolean values are parsed for predicates.
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param value Value to be parsed to boolean.
	 * @return The parsed boolean value.
	 * @throws IllegalArgumentException When value cannot be parsed as boolean.
	 */
	protected Boolean parseBoolean(Path<Boolean> path, Object value) {
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		else if (value instanceof Number) {
			return ((Number) value).intValue() > 0;
		}
		else {
			return Boolean.parseBoolean(value.toString());
		}
	}

	/**
	 * You can override this method if you want to deal with an unsupported predicate and want to return a custom predicate.
	 * At least, following predicates are supported, in this scanning order:
	 * <ul>
	 * <li>value = <code>null</code>
	 * <li>value = {@link Constraint}
	 * <li>value = {@link Iterable}
	 * <li>value = {@link Array}
	 * <li>type = {@link Enum}
	 * <li>type = {@link Number}
	 * <li>type = {@link Boolean}
	 * <li>type = {@link Collection}
	 * <li>type = {@link String}
	 * <li>value = {@link String}
	 * </ul>
	 * So if you want to support e.g. a {@link Map} value, then you could consider overriding this method.
	 *
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param key Search key. Use this as key of <code>parameterValues</code>.
	 * @param value Search value. You can handle this here. Ultimately it must be put as value of <code>parameterValues</code>.
	 * @param criteriaBuilder So you can build a predicate with a {@link CriteriaBuilder#parameter(Class, String)}.
	 * @param parameterValues This holds all search parameter values collected so far.
	 * @return The custom predicate.
	 * @throws UnsupportedOperationException When this is not overridden yet.
	 */
	protected Predicate buildUnsupportedPredicate(Path<?> path, String key, Object value, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
		throw new UnsupportedOperationException("Predicate for " + key + "=" + value + " is not supported."
			+ " Consider overriding buildUnsupportedPredicate() in your BaseEntityService subclass if you want to deal with it.");
	}


	// Helpers --------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private <T> Path<T> resolvePath(Root<E> root, String field) {
		if (!field.contains(".")) {
			return root.get(field);
		}

		Path<?> path = root;

		for (String property : field.split("\\.")) {
			path = path.get(property);
		}

		return (Path<T>) path;
	}

	private static void noop() {
		// NOOP.
	}

}
