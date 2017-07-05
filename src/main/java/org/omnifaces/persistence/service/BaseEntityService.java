package org.omnifaces.persistence.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.persistence.metamodel.PluralAttribute.CollectionType.MAP;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.Lang.coalesce;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.map;
import static org.omnifaces.utils.stream.Collectors.toMap;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NamedQuery;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.AbstractQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.PluralAttribute.CollectionType;

import org.omnifaces.persistence.constraint.Constraint;
import org.omnifaces.persistence.constraint.Constraint.ParameterBuilder;
import org.omnifaces.persistence.constraint.Not;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.exception.NonDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.NonDeletable;
import org.omnifaces.persistence.model.TimestampedEntity;
import org.omnifaces.persistence.model.VersionedEntity;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;

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
	protected final TypedQuery<E> createQuery(String jpqlStatement) {
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
	protected final TypedQuery<E> createNamedQuery(String name) {
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
	 * Fetch lazy collections of given entity on given getters. If no getters are supplied, then it will fetch every
	 * single {@link PluralAttribute} not of type {@link CollectionType#MAP}.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch lazy collections on.
	 * @param getters Getters of those lazy collections.
	 */
	@SafeVarargs
	protected final void fetchLazyCollections(E entity, Function<E, Collection<?>>... getters) {
		if (!isEmpty(getters)) {
			stream(getters).forEach(getter -> getter.apply(entity).size());
		}
		else {
			fetchEveryPluralAttribute(entity, type -> type != MAP);
		}
	}

	/**
	 * Fetch lazy maps of given entity on given getters. If no getters are supplied, then it will fetch every single
	 * {@link PluralAttribute} of type {@link CollectionType#MAP}.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch lazy maps on.
	 * @param getters Getters of those lazy collections.
	 */
	@SafeVarargs
	protected final void fetchLazyMaps(E entity, Function<E, Map<?, ?>>... getters) {
		if (!isEmpty(getters)) {
			stream(getters).forEach(getter -> getter.apply(entity).size());
		}
		else {
			fetchEveryPluralAttribute(entity, type -> type == MAP);
		}
	}

	private void fetchEveryPluralAttribute(E entity, java.util.function.Predicate<CollectionType> ofType) {
		for (PluralAttribute<?, ?, ?> a : entityManager.getMetamodel().entity(entity.getClass()).getPluralAttributes()) {
			if (ofType.test(a.getCollectionType())) {
				String name = Character.toUpperCase(a.getName().charAt(0)) + a.getName().substring(1);
				invokeMethod(invokeMethod(entity, "get" + name), "size");
			}
		}
	}

	/**
	 * Fetch all lazy blobs of given entity.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch all blobs on.
	 */
	protected final void fetchLazyBlobs(E entity) {
		E managed = entityManager.merge(entity);

		for (Attribute<?, ?> a : entityManager.getMetamodel().entity(managed.getClass()).getSingularAttributes()) {
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
	 * <p>
	 * You do not need this interface directly. Just supply a lambda. Below is an usage example:
	 * <pre>
	 * &#64;Stateless
	 * public class YourEntityService extends BaseEntityService&lt;YourEntity&gt; {
	 *
	 *     public void getPageOfFooType(Page page, boolean count) {
	 *         return getPage(page, count, (criteriaBuilder, query, root) -&gt; {
	 *             query.where(criteriaBuilder.equals(root.get("type"), Type.FOO));
	 *         });
	 *     }
	 *
	 *     public void getPageWithLazyChildren(Page page, boolean count) {
	 *         return getPage(page, count, (criteriaBuilder, query, root) -&gt; {
	 *             root.fetch("lazyChildren");
	 *         });
	 *     }
	 *
	 * }
	 * </pre>
	 * @param <E> The generic base entity type.
	 */
	@FunctionalInterface
	protected static interface QueryBuilder<E> {
		void build(CriteriaBuilder criteriaBuilder, AbstractQuery<E> query, Root<E> root);
	}

	/**
	 * Functional interface to fine-grain a JPA criteria query for any of {@link #getPage(Page, boolean)} methods taking
	 * a specific result type, such as an entity subclass (DTO). You must return a {@link LinkedHashMap} with
	 * {@link Getter} as key and {@link Expression} as value. The mapping must be in exactly the same order as
	 * constructor arguments of your DTO.
	 * <p>
	 * You do not need this interface directly. Just supply a lambda. Below is an usage example:
	 * <pre>
	 * public class YourEntityDTO extends YourEntity {
	 *
	 *     private BigDecimal totalPrice;
	 *
	 *     public YourEntityDTO(Long id, String name, BigDecimal totalPrice) {
	 *         setId(id);
	 *         setName(name);
	 *         this.totalPrice = totalPrice;
	 *     }
	 *
	 *     public BigDecimal getTotalPrice() {
	 *         return totalPrice;
	 *     }
	 *
	 * }
	 * </pre>
	 * <pre>
     * &#64;Stateless
     * public class YourEntityService extends BaseEntityService&lt;YourEntity&gt; {
     *
     *     public void getPageOfYourEntityDTO(Page page, boolean count) {
     *         return getPage(page, count, YourEntityDTO.class (criteriaBuilder, query, root) -&gt; {
     *             Join&lt;YourEntityDTO, YourChildEntity&gt; child = root.join("child");
     *
     *             LinkedHashMap&lt;Getter&lt;YourEntityDTO&gt;, Expression&lt;?&gt;&gt; mapping = new LinkedHashMap&lt;&gt;();
     *             mapping.put(YourEntityDTO::getId, root.get("id"));
     *             mapping.put(YourEntityDTO::getName, root.get("name"));
     *             mapping.put(YourEntityDTO::getTotalPrice, builder.sum(child.get("price")));
     *
     *             return mapping;
     *         });
     *     }
     *
     * }
	 * </pre>
	 * @param <T> The generic base entity type or from a DTO subclass thereof.
	 */
	@FunctionalInterface
	protected static interface MappedQueryBuilder<T> {
		LinkedHashMap<Getter<T>, Expression<?>> build(CriteriaBuilder criteriaBuilder, AbstractQuery<T> query, Root<? super T> root);
	}

	/**
	 * Here you can in your DTO subclass define the callback method which needs to be invoked before any of
	 * {@link #getPage(Page, boolean)} methods is called. For example, to set a vendor specific {@link EntityManager} hint.
	 * The default implementation returns a no-op callback.
	 * @return The callback method which is invoked before any of {@link #getPage(Page, boolean)} methods is called.
	 */
	protected Consumer<EntityManager> beforePage() {
		return entityManager -> noop();
	}

	/**
	 * Here you can in your DTO subclass define the callback method which needs to be invoked when any query involved in
	 * {@link #getPage(Page, boolean)} is about to be executed. For example, to set a vendor specific {@link Query} hint.
	 * The default implementation sets the Hibernate <code>cacheable</code> and <code>cacheRegion</code> hints.
	 * @param page The page on which this query is based.
	 * @param cacheable Whether the results should be cacheable.
	 * @return The callback method which is invoked when any query involved in {@link #getPage(Page, boolean)} is about
	 * to be executed.
	 */
	protected Consumer<TypedQuery<?>> onPage(Page page, boolean cacheable) {
		return typedQuery -> {
			typedQuery
				.setHint("org.hibernate.cacheable", cacheable) // TODO: EclipseLink? JPA 2.0?
				.setHint("org.hibernate.cacheRegion", page.toString());
		};
	}

	/**
	 * Here you can in your DTO subclass define the callback method which needs to be invoked after any of
	 * {@link #getPage(Page, boolean)} methods is called. For example, to remove a vendor specific {@link EntityManager} hint.
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
		return getPage(page, count, true, entityType, (builder, query, root) -> noop());
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
		return getPage(page, count, cacheable, entityType, (builder, query, root) -> noop());
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
	@SuppressWarnings("unchecked")
	protected PartialResultList<E> getPage(Page page, boolean count, QueryBuilder<E> queryBuilder) {
		return getPage(page, count, true, entityType, (builder, query, root) -> {
			queryBuilder.build(builder, query, (Root<E>) root);
			return noop();
		});
	}

	/**
	 * Returns a partial result list based on given {@link Page}, entity type and {@link QueryBuilder}.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param cacheable Whether the results should be cacheable.
	 * @param queryBuilder This allows fine-graining the JPA criteria query.
	 * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
	 */
	@SuppressWarnings("unchecked")
	protected PartialResultList<E> getPage(Page page, boolean count, boolean cacheable, QueryBuilder<E> queryBuilder) {
		return getPage(page, count, cacheable, entityType, (builder, query, root) -> {
			queryBuilder.build(builder, query, (Root<E>) root);
			return noop();
		});
	}

	/**
	 * Returns a partial result list based on given {@link Page}, result type and {@link MappedQueryBuilder}. This will
	 * by default cache the results.
	 * @param <T> The generic type of the entity or a DTO subclass thereof.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param resultType The result type which can be the entity type itself or a DTO subclass thereof.
	 * @param mappedQueryBuilder This allows fine-graining the JPA criteria query and must return a mapping of
	 * getters-paths.
	 * @return A partial result list based on given {@link Page} and {@link MappedQueryBuilder}.
	 * @throws IllegalArgumentException When the result type does not equal entity type and mapping is empty.
	 */
	protected <T extends E> PartialResultList<T> getPage(Page page, boolean count, Class<T> resultType, MappedQueryBuilder<T> mappedQueryBuilder) {
		return getPage(page, count, true, resultType, mappedQueryBuilder);
	}

	/**
	 * Returns a partial result list based on given {@link Page}, entity type and {@link QueryBuilder}.
	 * @param <T> The generic type of the entity or a DTO subclass thereof.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param cacheable Whether the results should be cacheable.
	 * @param resultType The result type which can be the entity type itself or a DTO subclass thereof.
	 * @param queryBuilder This allows fine-graining the JPA criteria query and must return a mapping of
	 * getters-paths when result type does not equal entity type.
	 * @return A partial result list based on given {@link Page} and {@link MappedQueryBuilder}.
	 * @throws IllegalArgumentException When the result type does not equal entity type and mapping is empty.
	 */
	protected <T extends E> PartialResultList<T> getPage(Page page, boolean count, boolean cacheable, Class<T> resultType, MappedQueryBuilder<T> queryBuilder) {
		beforePage().accept(entityManager);

		try {
			CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
			CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(resultType);
			Root<E> root = criteriaQuery.from(entityType);

			PathResolver pathResolver = buildSelection(criteriaBuilder, criteriaQuery, root, resultType, queryBuilder);
			buildOrderBy(page, criteriaBuilder, criteriaQuery, pathResolver);
			Map<String, Object> parameterValues = buildRestrictions(page, criteriaBuilder, criteriaQuery, pathResolver);

			TypedQuery<T> typedQuery = entityManager.createQuery(criteriaQuery);
			onPage(page, cacheable).accept(typedQuery);
			parameterValues.entrySet().forEach(parameter -> typedQuery.setParameter(parameter.getKey(), parameter.getValue()));
			List<T> entities = typedQuery.setFirstResult(page.getOffset()).setMaxResults(page.getLimit()).getResultList();

			int estimatedTotalNumberOfResults = -1;

			if (count) {
				CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
				Root<E> countRoot = countQuery.from(entityType);
				countQuery.select(criteriaBuilder.count(countRoot));

				if (hasRestrictions(criteriaQuery)) {
					// SELECT COUNT(e) FROM E e WHERE e.id IN (SELECT DISTINCT t.id FROM T t WHERE [restrictions])
					// See also https://stackoverflow.com/a/12076584/157882

					Subquery<T> subQuery = countQuery.subquery(resultType);
					Root<E> subQueryRoot = subQuery.from(entityType);
					pathResolver = buildSelection(criteriaBuilder, subQuery, subQueryRoot, resultType, queryBuilder);

					if (!hasJoins(root)) {
						copyRestrictions(criteriaQuery, subQuery); // No need to rebuild restrictions as they are the same anyway.
					}
					else {
						parameterValues = buildRestrictions(page, criteriaBuilder, subQuery, pathResolver);
					}

					countQuery.where(criteriaBuilder.in(countRoot).value(subQuery.select(subQueryRoot.get(ID)).distinct(true)));
				}

				TypedQuery<Long> typedCountQuery = entityManager.createQuery(countQuery);
				onPage(page, cacheable).accept(typedCountQuery);
				parameterValues.entrySet().forEach(parameter -> typedCountQuery.setParameter(parameter.getKey(), parameter.getValue()));
				estimatedTotalNumberOfResults = typedCountQuery.getSingleResult().intValue();
			}

			return new PartialResultList<>(entities, page.getOffset(), estimatedTotalNumberOfResults);
		}
		finally {
			afterPage().accept(entityManager);
		}
	}


	// Selection actions ----------------------------------------------------------------------------------------------

	private <T extends E> PathResolver buildSelection(CriteriaBuilder criteriaBuilder, AbstractQuery<T> query, Root<E> root, Class<T> resultType, MappedQueryBuilder<T> queryBuilder) {
		LinkedHashMap<Getter<T>, Expression<?>> mapping = queryBuilder.build(criteriaBuilder, query, query instanceof Subquery ? new SubQueryRoot<>(root) : root);

		if (!isEmpty(mapping)) {
			if (query instanceof CriteriaQuery) {
				((CriteriaQuery<?>) query).multiselect(mapping.values().toArray(new Selection[mapping.size()]));
			}

			Map<String, Expression<?>> paths = stream(mapping).collect(toMap(e -> e.getKey().getPropertyName(), e -> e.getValue()));
			PathResolver pathResolver = field -> (field == null) ? root : paths.get(field);

			if (paths.values().stream().anyMatch(BaseEntityService::needsGroupBy)) {
				groupByIfNecessary(query, root);
			}

			return pathResolver;
		}
		else if (resultType == entityType) {
			return new RootPathResolver(root);
		}
		else {
			throw new IllegalArgumentException("You must return a getter-path mapping from MappedQueryBuilder");
		}
	}


	// Sorting actions ------------------------------------------------------------------------------------------------

	private <T> void buildOrderBy(Page page, CriteriaBuilder criteriaBuilder, CriteriaQuery<T> criteriaQuery, PathResolver pathResolver) {
		Map<String, Boolean> ordering = page.getOrdering();

		if (ordering.isEmpty() || page.getLimit() - page.getOffset() == 1) {
			return;
		}

		criteriaQuery.orderBy(stream(ordering).map(order -> buildOrder(order, criteriaBuilder, pathResolver)).collect(toList()));
	}

	private Order buildOrder(Entry<String, Boolean> order, CriteriaBuilder criteriaBuilder, PathResolver pathResolver) {
		Expression<?> path = pathResolver.get(order.getKey());

		if (isElementCollection(path.getJavaType())) {
			path = pathResolver.get(pathResolver.forElementCollection(order.getKey()));
		}

		return order.getValue() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path);
	}


	// Searching actions -----------------------------------------------------------------------------------------------

	private <T> Map<String, Object> buildRestrictions(Page page, CriteriaBuilder criteriaBuilder, AbstractQuery<T> query, PathResolver pathResolver) {
		Map<String, Object> parameterValues = new HashMap<>(page.getRequiredCriteria().size() + page.getOptionalCriteria().size());
		List<Predicate> requiredPredicates = buildPredicates(page.getRequiredCriteria(), criteriaBuilder, pathResolver, parameterValues);
		List<Predicate> optionalPredicates = buildPredicates(page.getOptionalCriteria(), criteriaBuilder, pathResolver, parameterValues);
		Predicate restriction = null;

		if (!optionalPredicates.isEmpty()) {
			restriction = criteriaBuilder.or(optionalPredicates.toArray(PREDICATE_ARRAY));
		}

		if (!requiredPredicates.isEmpty()) {
			List<Predicate> wherePredicates = requiredPredicates.stream().filter(Alias::isWhere).collect(toList());

			if (!wherePredicates.isEmpty()) {
				restriction = conjunctRestrictionsIfNecessary(criteriaBuilder, restriction, wherePredicates);
			}

			List<Predicate> inPredicates = wherePredicates.stream().filter(Alias::isIn).collect(toList());

			for (Predicate inPredicate : inPredicates) {
				Predicate countPredicate = buildCountPredicateIfNecessary(inPredicate, criteriaBuilder, query, pathResolver);

				if (countPredicate != null) {
					requiredPredicates.add(countPredicate);
				}
			}

			List<Predicate> havingPredicates = requiredPredicates.stream().filter(Alias::isHaving).collect(toList());

			if (!havingPredicates.isEmpty()) {
				groupByIfNecessary(query, pathResolver.get(null));
				query.having(conjunctRestrictionsIfNecessary(criteriaBuilder, query.getGroupRestriction(), havingPredicates));
			}
		}

		if (restriction != null) {
			query.where(conjunctRestrictionsIfNecessary(criteriaBuilder, query.getRestriction(), restriction));
		}

		return parameterValues;
	}

	private <T> List<Predicate> buildPredicates(Map<String, Object> criteria, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameterValues) {
		return stream(criteria)
			.map(parameter -> buildPredicate(parameter, criteriaBuilder, pathResolver, parameterValues))
			.filter(Objects::nonNull)
			.collect(toList());
	}

	private Predicate buildPredicate(Entry<String, Object> parameter, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameterValues) {
		String field = parameter.getKey();
		Expression<?> path;

		try {
			path = pathResolver.get(field);
		}
		catch (IllegalArgumentException ignore) {
			return null; // Likely custom search key referring non-existent property.
		}

		Class<?> type = ID.equals(field) ? identifierType : path.getJavaType();
		Object value = parameter.getValue();

		if (isElementCollection(type)) {
			path = pathResolver.get(pathResolver.inElementCollection(field));
		}

		return buildTypedPredicate(path, type, field, value, criteriaBuilder, new UncheckedParameterBuilder(field, criteriaBuilder, parameterValues));
	}

	@SuppressWarnings({ "unchecked", "null" })
	private Predicate buildTypedPredicate(Expression<?> path, Class<?> type, String field, Object criteria, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		String alias = Alias.create(path, field);
		Object value = criteria;
		boolean negated = value instanceof Not;
		Predicate predicate;

		if (negated) {
			value = ((Not) value).getValue();
		}

		if (value instanceof Constraint && ((Constraint<?>) value).getValue() == null) {
			value = null;
		}

		try {
			if (value == null) {
				predicate = criteriaBuilder.isNull(path);
			}
			else if (isElementCollection(type)) {
				predicate = buildInPredicate(path, alias, value, parameterBuilder);
			}
			else if (value instanceof Constraint) {
				predicate = ((Constraint<?>) value).build(path, criteriaBuilder, parameterBuilder);
			}
			else if (value instanceof Iterable || value.getClass().isArray()) {
				predicate = buildArrayPredicate(path, type, field, value, criteriaBuilder, parameterBuilder);
			}
			else if (type.isEnum()) {
				predicate = buildEqualPredicate(path, parseEnum((Expression<Enum<?>>) path, value), criteriaBuilder, parameterBuilder);
			}
			else if (Number.class.isAssignableFrom(type)) {
				predicate = buildEqualPredicate(path, parseNumber((Expression<Number>) path, value), criteriaBuilder, parameterBuilder);
			}
			else if (Boolean.class.isAssignableFrom(type)) {
				predicate = buildEqualPredicate(path, parseBoolean((Expression<Boolean>) path, value), criteriaBuilder, parameterBuilder);
			}
			else if (String.class.isAssignableFrom(type) || value instanceof String) {
				predicate = buildLikePredicate(path, value.toString(), criteriaBuilder, parameterBuilder);
			}
			else {
				predicate = buildUnsupportedPredicate(path, alias, value, criteriaBuilder, parameterBuilder);
			}
		}
		catch (IllegalArgumentException e) {
			return null; // Likely custom search value referring illegal value.
		}

		if (predicate == null) {
			throw new UnsupportedOperationException("You may not return null from buildUnsupportedPredicate().");
		}

		alias = coalesce(predicate.getAlias(), alias);

		if (negated) {
			predicate = criteriaBuilder.not(predicate);
		}

		predicate.alias(alias);
		return predicate;
	}

	private Predicate buildInPredicate(Expression<?> path, String alias, Object value, ParameterBuilder parameterBuilder) {
		List<Expression<?>> in = stream(value).map(parameterBuilder::create).collect(toList());

		if (in.isEmpty()) {
			throw new IllegalArgumentException(value.toString());
		}

		Predicate predicate = ((Join<?, ?>) path).in(in.toArray(new Expression[in.size()]));
		predicate.alias(Alias.in(alias, in.size()));
		return predicate;
	}

	private Predicate buildArrayPredicate(Expression<?> path, Class<?> type, String field, Object value, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		List<Predicate> predicates = stream(value)
			.map(item -> buildTypedPredicate(path, type, field, item, criteriaBuilder, parameterBuilder))
			.filter(Objects::nonNull)
			.collect(toList());

		if (predicates.isEmpty()) {
			throw new IllegalArgumentException(value.toString());
		}

		Predicate predicate = criteriaBuilder.or(predicates.toArray(PREDICATE_ARRAY));
		return predicate;
	}

	private Predicate buildEqualPredicate(Expression<?> path, Object value, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(path, parameterBuilder.create(value));
	}

	private Predicate buildLikePredicate(Expression<?> path, String value, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.function("str", String.class, path)), parameterBuilder.create(value));
	}

	/**
	 * You can override this method if you want more fine grained control over how enums values are parsed for predicates.
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param value Value to be parsed to enum.
	 * @return The parsed enum value.
	 * @throws IllegalArgumentException When value cannot be parsed as enum.
	 */
	protected Enum<?> parseEnum(Expression<Enum<?>> path, Object value) throws IllegalArgumentException {
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
	 * @throws IllegalArgumentException When value cannot be parsed as number.
	 */
	protected Number parseNumber(Expression<Number> path, Object value) throws NumberFormatException {
		if (value instanceof Number) {
			return (Number) value;
		}

		try {
			if (BigDecimal.class.isAssignableFrom(path.getJavaType())) {
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
		catch (NumberFormatException e) {
			throw new IllegalArgumentException(value.toString(), e);
		}
	}

	/**
	 * You can override this method if you want more fine grained control over how boolean values are parsed for predicates.
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param value Value to be parsed to boolean.
	 * @return The parsed boolean value.
	 * @throws IllegalArgumentException When value cannot be parsed as boolean.
	 */
	protected Boolean parseBoolean(Expression<Boolean> path, Object value) {
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
	 * <li>type = {@link Collection}
	 * <li>value = {@link Constraint}
	 * <li>value = {@link Iterable}
	 * <li>value = {@link Array}
	 * <li>type = {@link Enum}
	 * <li>type = {@link Number}
	 * <li>type = {@link Boolean}
	 * <li>type = {@link String}
	 * <li>value = {@link String}
	 * </ul>
	 * So if you want to support e.g. a {@link Map} value for a type not covered by one of above types, then you could consider
	 * overriding this method.
	 *
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param key Search key. Use this as key of <code>parameterValues</code>.
	 * @param value Search value. You can handle this here. Ultimately it must be put as value of <code>parameterValues</code>.
	 * @param criteriaBuilder So you can build a predicate with a {@link CriteriaBuilder#parameter(Class, String)}.
	 * @param parameterBuilder You must use this to obtain a {@link ParameterExpression} for a given value.
	 * @return The custom predicate.
	 * @throws UnsupportedOperationException When this is not overridden yet.
	 * @throws IllegalArgumentException When you cannot parse the value reasonably.
	 */
	protected Predicate buildUnsupportedPredicate(Expression<?> path, String key, Object value, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		throw new UnsupportedOperationException("Predicate for " + key + "=" + value + " is not supported."
			+ " Consider overriding buildUnsupportedPredicate() in your BaseEntityService subclass if you want to deal with it.");
	}


	// Helpers --------------------------------------------------------------------------------------------------------

	@FunctionalInterface
	private static interface PathResolver {
		Expression<?> get(String field);

		default String forElementCollection(String attribute) {
			return '@' + attribute;
		}

		default String inElementCollection(String attribute) {
			return '@' + attribute + '@';
		}
	}

	private static class RootPathResolver implements PathResolver {

		private Root<?> root;
		private Map<String, Path<?>> joins;
		private Map<String, Path<?>> paths;

		private RootPathResolver(Root<?> root) {
			this.root = root;
			this.joins = getJoins(root);
			this.paths = new HashMap<>();
		}

		@Override
		public Expression<?> get(String field) {
			if (field == null) {
				return root;
			}

			Path<?> path = paths.get(field);

			if (path != null) {
				return path;
			}

			path = root;
			String[] attributes = field.split("\\.");
			int depth = attributes.length;

			for (int i = 0; i < depth; i++) {
				String attribute = attributes[i];

				if (i + 1 < depth) {
					path = joins.get(attribute);
				}
				else if (!attribute.startsWith("@")) {
					path = path.get(attribute);
				}
				else if (!attribute.endsWith("@")) {
					path = joins.get(attribute.substring(1));
				}
				else {
					path = ((From<?, ?>) path).join(attribute.substring(1, attribute.length() - 1));
				}
			}

			paths.put(field, path);
			return path;
		}

		private static Map<String, Path<?>> getJoins(From<?, ?> from) {
			Map<String, Path<?>> joins = new HashMap<>(from.getJoins().stream().collect(toMap(join -> join.getAttribute().getName())));
			joins.putAll(from.getFetches().stream().filter(fetch -> fetch instanceof Path).collect(toMap(fetch -> fetch.getAttribute().getName(), fetch -> (Path<?>) fetch)));
			return joins;
		}
	}

	private static class Alias {

		private static final String WHERE = "where_";
		private static final String HAVING = "having_";
		private static final String IN = "_in";

		public static String create(Expression<?> expression, String field) {
			return (needsGroupBy(expression) ? HAVING : WHERE) + field.replace(".", "_");
		}

		public static String in(String alias, int count) {
			return alias + "_" + count + IN;
		}

		public static String having(Predicate predicate) {
			return HAVING + predicate.getAlias().substring(predicate.getAlias().indexOf("_") + 1);
		}

		public static boolean isWhere(Predicate predicate) {
			return predicate.getAlias().startsWith(WHERE);
		}

		public static boolean isIn(Predicate predicate) {
			return predicate.getAlias().endsWith(IN);
		}

		public static boolean isHaving(Predicate predicate) {
			return predicate.getAlias().startsWith(HAVING);
		}

		public static Entry<String, Integer> getFieldAndCount(Predicate predicate) {
			String alias = predicate.getAlias();
			String[] fieldAndCount = alias.substring(alias.indexOf('_') + 1, alias.lastIndexOf('_')).split("_");
			String field = fieldAndCount[0];
			int count = Integer.valueOf(fieldAndCount[1]);
			return new SimpleEntry<>(field, count);
		}

	}

	private static class UncheckedParameterBuilder implements ParameterBuilder {

		private String field;
		private CriteriaBuilder criteriaBuilder;
		private Map<String, Object> parameterValues;

		private UncheckedParameterBuilder(String field, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
			this.field = field.replace(".", "_") + "_";
			this.criteriaBuilder = criteriaBuilder;
			this.parameterValues = parameterValues;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> ParameterExpression<T> create(Object value) {
			String name = field + parameterValues.size();
			parameterValues.put(name, value);
			return (ParameterExpression<T>) criteriaBuilder.parameter(value.getClass(), name);
		}

	}

	private static boolean isElementCollection(Class<?> type) {
		return Collection.class.isAssignableFrom(type);
	}

	private static Predicate conjunctRestrictionsIfNecessary(CriteriaBuilder criteriaBuilder, Predicate nullable, Predicate nonnullable) {
		return nullable == null ? nonnullable : criteriaBuilder.and(nullable, nonnullable);
	}

	private static Predicate conjunctRestrictionsIfNecessary(CriteriaBuilder criteriaBuilder, Predicate nullable, List<Predicate> nonnullable) {
		return conjunctRestrictionsIfNecessary(criteriaBuilder, nullable, criteriaBuilder.and(nonnullable.toArray(PREDICATE_ARRAY)));
	}

	private static Predicate buildCountPredicateIfNecessary(Predicate inPredicate, CriteriaBuilder criteriaBuilder, AbstractQuery<?> query, PathResolver pathResolver) {
		Entry<String, Integer> fieldAndCount = Alias.getFieldAndCount(inPredicate);

		if (fieldAndCount.getValue() > 1) {
			Expression<?> join = pathResolver.get(pathResolver.inElementCollection(fieldAndCount.getKey()));
			Predicate countPredicate = criteriaBuilder.equal(criteriaBuilder.count(join), fieldAndCount.getValue());
			countPredicate.alias(Alias.having(inPredicate));
			groupByIfNecessary(query, pathResolver.get(pathResolver.forElementCollection(fieldAndCount.getKey())));
			return countPredicate;
		}

		return null;
	}

	private static boolean needsGroupBy(Expression<?> expression) {
		return !(expression instanceof Path);
	}

	private static void groupByIfNecessary(AbstractQuery<?> query, Expression<?> path) {
		if (!query.getGroupList().contains(path)) {
			List<Expression<?>> groupList = new ArrayList<>(query.getGroupList());
			groupList.add(path);
			query.groupBy(groupList);
		}
	}

	private static boolean hasRestrictions(AbstractQuery<?> query) {
		return query.getRestriction() != null || !query.getGroupList().isEmpty() || query.getGroupRestriction() != null;
	}

	private static boolean hasJoins(From<?, ?> from) {
		return !from.getJoins().isEmpty() || from.getFetches().stream().anyMatch(fetch -> fetch instanceof Path);
	}

	private static void copyRestrictions(AbstractQuery<?> source, AbstractQuery<?> target) {
		if (source.getRestriction() != null) {
			target.where(source.getRestriction());
		}

		target.groupBy(source.getGroupList());

		if (source.getGroupRestriction() != null) {
			target.having(source.getGroupRestriction());
		}
	}

	private static <T> T noop() {
		return null;
	}

}
