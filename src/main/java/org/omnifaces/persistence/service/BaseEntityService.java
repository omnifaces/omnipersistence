package org.omnifaces.persistence.service;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_MANY;
import static javax.persistence.metamodel.PluralAttribute.CollectionType.MAP;
import static org.omnifaces.persistence.Provider.ECLIPSELINK;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.Provider.OPENJPA;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.map;
import static org.omnifaces.utils.stream.Collectors.toMap;
import static org.omnifaces.utils.stream.Streams.stream;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
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
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.PluralAttribute.CollectionType;

import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.criteria.Bool;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Criteria.ParameterBuilder;
import org.omnifaces.persistence.criteria.Enumerated;
import org.omnifaces.persistence.criteria.IgnoreCase;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.criteria.Numeric;
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
 * @see BaseEntity
 * @see Page
 * @see Criteria
 */
public abstract class BaseEntityService<I extends Comparable<I> & Serializable, E extends BaseEntity<I>> {

	private static final Map<Class<?>, Entry<Class<?>, Class<?>>> TYPE_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Set<String>> ELEMENT_COLLECTION_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Set<String>> ONE_TO_MANY_COLLECTION_MAPPINGS = new ConcurrentHashMap<>();

	private static final String ERROR_ILLEGAL_MAPPING =
		"You must return a getter-path mapping from MappedQueryBuilder";
	private static final String ERROR_UNSUPPORTED_CRITERIA =
		"Predicate for %s(%s) = %s(%s) is not supported. Consider wrapping in a Criteria instance or creating a custom one if you want to deal with it.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_ECLIPSELINK =
		"Sorry, EclipseLink does not support sorting a @OneToMany or @ElementCollection relationship. Consider using a DTO instead.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_OPENJPA =
		"Sorry, OpenJPA does not support sorting a @OneToMany or @ElementCollection relationship. Consider using a DTO instead.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_ECLIPSELINK =
		"Sorry, EclipseLink does not support searching in a @OneToMany relationship. Consider using a DTO instead.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_OPENJPA =
		"Sorry, OpenJPA does not support searching in a @OneToMany relationship. Consider using a DTO instead.";

	private final Class<I> identifierType;
	private final Class<E> entityType;
	private Set<String> elementCollections;
	private java.util.function.Predicate<String> oneToManyCollections;

	@PersistenceContext
	private EntityManager entityManager;

	@Inject
	private Provider provider;

	@Inject
	private Database database;


	// Init -----------------------------------------------------------------------------------------------------------

	/**
	 * The constructor initializes the type mapping.
	 * The <code>I</code> and <code>E</code> will be resolved to a concrete <code>Class&lt;?&gt;</code>.
	 */
	@SuppressWarnings("unchecked")
	public BaseEntityService() {
		Entry<Class<?>, Class<?>> typeMapping = TYPE_MAPPINGS.computeIfAbsent(getClass(), BaseEntityService::computeTypeMapping);
		identifierType = (Class<I>) typeMapping.getKey();
		entityType = (Class<E>) typeMapping.getValue();
	}

	/**
	 * The postconstruct initializes the element and one-to-many collections.
	 */
	@PostConstruct
	private void initWithEntityManager() {
		elementCollections = ELEMENT_COLLECTION_MAPPINGS.computeIfAbsent(entityType, this::computeElementCollectionMapping);
		oneToManyCollections = field -> ONE_TO_MANY_COLLECTION_MAPPINGS.computeIfAbsent(entityType, this::computeOneToManyCollectionMapping)
			.stream().anyMatch(oneToManyCollection -> field.startsWith(oneToManyCollection + "."));
	}

	private static Entry<Class<?>, Class<?>> computeTypeMapping(Class<?> type) {
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

	private Set<String> computeElementCollectionMapping(Class<?> type) {
		return computeCollectionMapping(type, "", new HashSet<>(), provider::isElementCollection);
	}

	private Set<String> computeOneToManyCollectionMapping(Class<?> type) {
		return computeCollectionMapping(type, "", new HashSet<>(), a -> !provider.isElementCollection(a) && a.getPersistentAttributeType() == ONE_TO_MANY);
	}

	private Set<String> computeCollectionMapping(Class<?> type, String basePath, Set<Class<?>> nestedTypes, java.util.function.Predicate<Attribute<?, ?>> attributePredicate) {
		Set<String> collectionMapping = new HashSet<>(1);
		EntityType<?> entity = entityManager.getMetamodel().entity(type);

		for (Attribute<?, ?> attribute : entity.getAttributes()) {
			if (attributePredicate.test(attribute)) {
				collectionMapping.add(basePath + attribute.getName());
			}
			else if (attribute instanceof Bindable) {
				Class<?> nestedType = ((Bindable<?>) attribute).getBindableJavaType();

				if (BaseEntity.class.isAssignableFrom(nestedType) && nestedType != entityType && nestedTypes.add(nestedType)) {
					collectionMapping.addAll(computeCollectionMapping(nestedType, basePath + attribute.getName() + ".", nestedTypes, attributePredicate));
				}
			}
		}

		return unmodifiableSet(collectionMapping);
	}


	// Standard actions -----------------------------------------------------------------------------------------------

	/**
	 * Returns the JPA provider being used.
	 * @return The JPA provider being used.
	 */
	protected Provider getProvider() {
		return provider;
	}

	/**
	 * Returns the SQL database being used.
	 * @return The SQL database being used.
	 */
	protected Database getDatabase() {
		return database;
	}

	/**
	 * Create an instance of {@link TypedQuery} for executing a Java Persistence Query Language statement identified
	 * by the given name, usually to perform a SELECT.
	 * @param name The name of the Java Persistence Query Language statement defined in metadata, which can be either
	 * a {@link NamedQuery} or a <code>&lt;persistence-unit&gt;&lt;mapping-file&gt;</code>.
	 * @return An instance of {@link TypedQuery} for executing a Java Persistence Query Language statement identified
	 * by the given name, usually to perform a SELECT.
	 */
	protected TypedQuery<E> createNamedTypedQuery(String name) {
		return entityManager.createNamedQuery(name, entityType);
	}

	/**
	 * Create an instance of {@link Query} for executing a Java Persistence Query Language statement identified
	 * by the given name, usually to perform an INSERT, UPDATE or DELETE.
	 * @param name The name of the Java Persistence Query Language statement defined in metadata, which can be either
	 * a {@link NamedQuery} or a <code>&lt;persistence-unit&gt;&lt;mapping-file&gt;</code>.
	 * @return An instance of {@link Query} for executing a Java Persistence Query Language statement identified
	 * by the given name, usually to perform an INSERT, UPDATE or DELETE.
	 */
	protected Query createNamedQuery(String name) {
		return entityManager.createNamedQuery(name);
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
		return entityManager.createQuery("SELECT e FROM " + entityType.getSimpleName() + " e ORDER BY e.id DESC", entityType).getResultList();
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
			persist(entity);
			return entity;
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
		E managed = manage(entity);
		entityManager.getMetamodel().entity(managed.getClass()).getAttributes().forEach(a -> map(a.getJavaMember(), managed, entity)); // Note: EntityManager#refresh() is insuitable as it requires a managed entity.
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
	protected E manage(E entity) {
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
	@SuppressWarnings("unchecked")
	protected void fetchLazyCollections(E entity, Function<E, Collection<?>>... getters) {
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
	@SuppressWarnings("unchecked")
	protected void fetchLazyMaps(E entity, Function<E, Map<?, ?>>... getters) {
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
	protected void fetchLazyBlobs(E entity) {
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
			TypedQuery<T> entityQuery = buildEntityQuery(page, cacheable, resultType, criteriaBuilder, queryBuilder);
			TypedQuery<Long> countQuery = count ? buildCountQuery(page, cacheable, resultType, !entityQuery.getParameters().isEmpty(), criteriaBuilder, queryBuilder) : null;
			return executeQuery(page, entityQuery, countQuery);
		}
		finally {
			afterPage().accept(entityManager);
		}
	}


	// Query actions --------------------------------------------------------------------------------------------------

	private <T extends E> TypedQuery<T> buildEntityQuery(Page page, boolean cacheable, Class<T> resultType, CriteriaBuilder criteriaBuilder, MappedQueryBuilder<T> queryBuilder) {
		CriteriaQuery<T> entityQuery = criteriaBuilder.createQuery(resultType);
		Root<E> entityQueryRoot = buildRoot(entityQuery);
		PathResolver pathResolver = buildSelection(resultType, entityQuery, entityQueryRoot, criteriaBuilder, queryBuilder);
		buildOrderBy(page, entityQuery, criteriaBuilder, pathResolver);
		Map<String, Object> parameterValues = buildRestrictions(page, entityQuery, criteriaBuilder, pathResolver);
		return buildTypedQuery(page, cacheable, entityQuery, entityQueryRoot, parameterValues);
	}

	private <T extends E> TypedQuery<Long> buildCountQuery(Page page, boolean cacheable, Class<T> resultType, boolean buildCountSubquery, CriteriaBuilder criteriaBuilder, MappedQueryBuilder<T> queryBuilder) {
		CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
		Root<E> countQueryRoot = buildRoot(countQuery);
		countQuery.select(criteriaBuilder.count(countQueryRoot));
		Map<String, Object> parameterValues = buildCountSubquery ? buildCountSubquery(page, resultType, countQuery, countQueryRoot, criteriaBuilder, queryBuilder) : emptyMap();
		return buildTypedQuery(page, cacheable, countQuery, null, parameterValues);
	}

	private <T extends E> Map<String, Object> buildCountSubquery(Page page, Class<T> resultType, CriteriaQuery<Long> countQuery, Root<E> countRoot, CriteriaBuilder criteriaBuilder, MappedQueryBuilder<T> queryBuilder) {
		Subquery<T> countSubquery = countQuery.subquery(resultType);
		Root<E> countSubqueryRoot = buildRoot(countSubquery);
		PathResolver subqueryPathResolver = buildSelection(resultType, countSubquery, countSubqueryRoot, criteriaBuilder, queryBuilder);
		Map<String, Object> parameterValues = buildRestrictions(page, countSubquery, criteriaBuilder, subqueryPathResolver);

		if (provider == HIBERNATE) {
			// SELECT COUNT(e) FROM E e WHERE e IN (SELECT t FROM T t WHERE [restrictions])
			countQuery.where(criteriaBuilder.in(countRoot).value(countSubquery));
			// EclipseLink (tested 2.6.4) fails here with an incorrect selection in subquery: SQLException: Database "T1" not found; SQL statement: SELECT COUNT(t0.ID) FROM PERSON t0 WHERE t0.ID IN (SELECT DISTINCT t1.ID.t1.ID FROM PERSON t1 WHERE [...])
			// OpenJPA (tested 2.4.2) fails here as it doesn't interpret root as @Id: org.apache.openjpa.persistence.ArgumentException: Filter invalid. Cannot compare value of type optimusfaces.test.Person to value of type java.lang.Long.
		}
		else if (provider == OPENJPA) {
			// SELECT COUNT(e) FROM E e WHERE e.id IN (SELECT t.id FROM T t WHERE [restrictions])
			countQuery.where(criteriaBuilder.in(countRoot.get(ID)).value(countSubquery));
			// Hibernate (tested 5.0.10) fails here when DTO is used as it does not have a mapped ID.
			// EclipseLink (tested 2.6.4) fails here with an incorrect selection in subquery: SQLException: Database "T1" not found; SQL statement: SELECT COUNT(t0.ID) FROM PERSON t0 WHERE t0.ID IN (SELECT DISTINCT t1.ID.t1.ID FROM PERSON t1 WHERE [...])
		}
		else {
			// SELECT COUNT(e) FROM E e WHERE EXISTS (SELECT t.id FROM T t WHERE [restrictions] AND t.id = e.id)
			countQuery.where(criteriaBuilder.exists(countSubquery.where(conjunctRestrictionsIfNecessary(criteriaBuilder, countSubquery.getRestriction(), criteriaBuilder.equal(countSubqueryRoot.get(ID), countRoot.get(ID))))));
			// Hibernate (tested 5.0.10) and OpenJPA (tested 2.4.2) also support this but this is a tad less efficient than IN.
		}

		return parameterValues;
	}

	private <T> TypedQuery<T> buildTypedQuery(Page page, boolean cacheable, CriteriaQuery<T> criteriaQuery, Root<E> root, Map<String, Object> parameterValues) {
		TypedQuery<T> typedQuery = entityManager.createQuery(criteriaQuery);
		buildRange(page, typedQuery, root);
		parameterValues.entrySet().forEach(parameter -> typedQuery.setParameter(parameter.getKey(), parameter.getValue()));
		onPage(page, cacheable).accept(typedQuery);
		return typedQuery;
	}

	private <T extends E> PartialResultList<T> executeQuery(Page page, TypedQuery<T> entityQuery, TypedQuery<Long> countQuery) {
		List<T> entities = entityQuery.getResultList();
		int estimatedTotalNumberOfResults = (countQuery != null) ? countQuery.getSingleResult().intValue() : -1;
		return new PartialResultList<>(entities, page.getOffset(), estimatedTotalNumberOfResults);
	}


	// Selection actions ----------------------------------------------------------------------------------------------

	private Root<E> buildRoot(AbstractQuery<?> query) {
		Root<E> root = query.from(entityType);
		return (query instanceof Subquery) ? new SubqueryRoot<>(root) : (provider == ECLIPSELINK) ? new EclipseLinkRoot<>(root) : root;
	}

	private <T extends E> PathResolver buildSelection(Class<T> resultType, AbstractQuery<T> query, Root<E> root, CriteriaBuilder criteriaBuilder, MappedQueryBuilder<T> queryBuilder) {
		LinkedHashMap<Getter<T>, Expression<?>> mapping = queryBuilder.build(criteriaBuilder, query, root);

		if (query instanceof Subquery) {
			((Subquery<?>) query).select(root.get(ID));
		}

		if (!isEmpty(mapping)) {
			Map<String, Expression<?>> paths = stream(mapping).collect(toMap(e -> e.getKey().getPropertyName(), e -> e.getValue(), (l, r) -> l, LinkedHashMap::new));

			if (query instanceof CriteriaQuery) {
				((CriteriaQuery<?>) query).multiselect(stream(paths).map(Alias::as).collect(toList()));
			}

			if (paths.values().stream().anyMatch(provider::isAggregation)) {
				groupByIfNecessary(query, root);
			}

			return field -> (field == null) ? root : paths.get(field);
		}
		else if (resultType == entityType) {
			return new RootPathResolver(root, ELEMENT_COLLECTION_MAPPINGS.get(entityType));
		}
		else {
			throw new IllegalArgumentException(ERROR_ILLEGAL_MAPPING);
		}
	}

	private <T> void buildRange(Page page, TypedQuery<T> typedQuery, Root<E> root) {
		if (root == null) {
			return;
		}

		boolean hasJoins = hasJoins(root);

		if (hasJoins || page.getOffset() != 0) {
			typedQuery.setFirstResult(page.getOffset());
		}

		if (hasJoins || page.getLimit() != MAX_VALUE) {
			typedQuery.setMaxResults(page.getLimit());
		}

		if (hasJoins && root instanceof EclipseLinkRoot) {
			((EclipseLinkRoot<?>) root).getPostponedFetches().forEach(fetch -> {
				typedQuery.setHint("eclipselink.batch", "e." + fetch);
			});
		}
	}


	// Sorting actions ------------------------------------------------------------------------------------------------

	private <T> void buildOrderBy(Page page, CriteriaQuery<T> criteriaQuery, CriteriaBuilder criteriaBuilder, PathResolver pathResolver) {
		Map<String, Boolean> ordering = page.getOrdering();

		if (ordering.isEmpty() || page.getLimit() - page.getOffset() == 1) {
			return;
		}

		criteriaQuery.orderBy(stream(ordering).map(order -> buildOrder(order, criteriaBuilder, pathResolver)).collect(toList()));
	}

	private Order buildOrder(Entry<String, Boolean> order, CriteriaBuilder criteriaBuilder, PathResolver pathResolver) {
		String field = order.getKey();

		if (oneToManyCollections.test(field) || elementCollections.contains(field)) {
			if (provider == ECLIPSELINK) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_ECLIPSELINK); // EclipseLink refuses to perform a JOIN when setFirstResult/setMaxResults is used.
			}
			else if (provider == OPENJPA) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_OPENJPA); // OpenJPA adds for some reason a second JOIN on the join table referenced in ORDER BY column causing it to be not sorted on the intended join.
			}
		}

		Expression<?> path = pathResolver.get(field);
		return order.getValue() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path);
	}


	// Searching actions -----------------------------------------------------------------------------------------------

	private <T extends E> Map<String, Object> buildRestrictions(Page page, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver) {
		Map<String, Object> parameterValues = new HashMap<>(page.getRequiredCriteria().size() + page.getOptionalCriteria().size());
		List<Predicate> requiredPredicates = buildPredicates(page.getRequiredCriteria(), query, criteriaBuilder, pathResolver, parameterValues);
		List<Predicate> optionalPredicates = buildPredicates(page.getOptionalCriteria(), query, criteriaBuilder, pathResolver, parameterValues);
		Predicate restriction = null;

		if (!optionalPredicates.isEmpty()) {
			restriction = criteriaBuilder.or(toArray(optionalPredicates));
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
			query.distinct(hasFetches((From<?, ?>) pathResolver.get(null))).where(conjunctRestrictionsIfNecessary(criteriaBuilder, query.getRestriction(), restriction));
		}

		return parameterValues;
	}

	private <T extends E> List<Predicate> buildPredicates(Map<String, Object> criteria, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameterValues) {
		return stream(criteria)
			.map(parameter -> buildPredicate(parameter, query, criteriaBuilder, pathResolver, parameterValues))
			.filter(Objects::nonNull)
			.collect(toList());
	}

	private <T extends E> Predicate buildPredicate(Entry<String, Object> parameter, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameterValues) {
		String field = parameter.getKey();
		Expression<?> path;

		try {
			path = pathResolver.get(field);
		}
		catch (IllegalArgumentException ignore) {
			return null; // Likely custom search key referring non-existent property.
		}

		Object value = parameter.getValue();

		if (elementCollections.contains(field)) {
			path = pathResolver.get(pathResolver.join(field));
		}

		Class<?> type = ID.equals(field) ? identifierType : path.getJavaType();
		return buildTypedPredicate(path, type, field, value, query, criteriaBuilder, pathResolver, new UncheckedParameterBuilder(field, criteriaBuilder, parameterValues));
	}

	@SuppressWarnings("unchecked")
	private <T extends E> Predicate buildTypedPredicate(Expression<?> path, Class<?> type, String field, Object criteria, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, ParameterBuilder parameterBuilder) {
		Alias alias = Alias.create(provider, path, field);
		Object value = criteria;
		boolean negated = value instanceof Not;
		Predicate predicate;

		if (negated) {
			value = ((Not) value).getValue();
		}

		if (value instanceof Criteria && ((Criteria<?>) value).getValue() == null) {
			value = null;
		}

		try {
			if (value == null) {
				predicate = criteriaBuilder.isNull(path);
			}
			else if (elementCollections.contains(field)) {
				predicate = buildElementCollectionPredicate(alias, path, type, value, parameterBuilder);
			}
			else if (value instanceof Iterable || value.getClass().isArray()) {
				predicate = buildArrayPredicate(path, type, field, value, query, criteriaBuilder, pathResolver, parameterBuilder);
			}
			else if (value instanceof Criteria) {
				predicate = ((Criteria<?>) value).build(path, criteriaBuilder, parameterBuilder);
			}
			else if (type.isEnum()) {
				predicate = Enumerated.parse(value, (Class<Enum<?>>) type).build(path, criteriaBuilder, parameterBuilder);
			}
			else if (Number.class.isAssignableFrom(type)) {
				predicate = Numeric.parse(value, (Class<Number>) type).build(path, criteriaBuilder, parameterBuilder);
			}
			else if (Boolean.class.isAssignableFrom(type)) {
				predicate = Bool.parse(value).build(path, criteriaBuilder, parameterBuilder);
			}
			else if (String.class.isAssignableFrom(type) || value instanceof String) {
				predicate = IgnoreCase.value(value.toString()).build(path, criteriaBuilder, parameterBuilder);
			}
			else {
				throw new UnsupportedOperationException(String.format(ERROR_UNSUPPORTED_CRITERIA, field, type, value, value.getClass()));
			}
		}
		catch (IllegalArgumentException e) {
			return null; // Likely custom search value referring illegal value.
		}

		if (negated) {
			predicate = criteriaBuilder.not(predicate);
		}

		alias.set(predicate);
		return predicate;
	}

	private Predicate buildElementCollectionPredicate(Alias alias, Expression<?> path, Class<?> type, Object value, ParameterBuilder parameterBuilder) {
		List<Expression<?>> in = stream(value)
			.map(item -> parseElementCollectionValue(type, item))
			.filter(Objects::nonNull)
			.map(parameterBuilder::create)
			.collect(toList());

		if (in.isEmpty()) {
			throw new IllegalArgumentException(value.toString());
		}

		alias.in(in.size());
		return path.in(in.toArray(new Expression[in.size()]));
	}

	@SuppressWarnings("unchecked")
	private Object parseElementCollectionValue(Class<?> type, Object value) {
		Object searchValue = Criteria.unwrap(value); // TODO: Log warning here? @ElementCollection usually represent enums only anyway. There's no business interest of performing e.g. LIKE on them.

		if (type.isEnum()) {
			try {
				return Enumerated.parse(searchValue, (Class<Enum<?>>) type).getValue();
			}
			catch (IllegalArgumentException ignore) {
				return null; // Likely custom search value referring illegal value.
			}
		}

		return searchValue;
	}

	private <T extends E> Predicate buildArrayPredicate(Expression<?> path, Class<?> type, String field, Object value, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, ParameterBuilder parameterBuilder) {
		Subquery<Long> oneToManySubquery = null;
		Expression<?> fieldPath;

		if (oneToManyCollections.test(field)) {
			if (provider == ECLIPSELINK) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_ECLIPSELINK); // EclipseLink refuses to perform a JOIN when setFirstResult/setMaxResults is used.
			}
			else if (provider == OPENJPA) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_OPENJPA); // OpenJPA bugs on setting parameters in a nested subquery "java.lang.IllegalArgumentException: Parameter named X is not declared in query"
			}

			// This subquery must simulate an IN clause on a field of a @OneToMany relationship which behaves exactly the same as an IN clause on a @ElementCollection field.
			// Otherwise the query will return ONLY the matching values while the natural expectation in UI is that they are just all returned.
			oneToManySubquery = query.subquery(Long.class);
			Root<E> subqueryRoot = oneToManySubquery.from(entityType);
			fieldPath = new RootPathResolver(subqueryRoot, elementCollections).get(pathResolver.join(field));
			oneToManySubquery.select(criteriaBuilder.countDistinct(fieldPath)).where(criteriaBuilder.equal(subqueryRoot.get(ID), pathResolver.get(ID)));
		}
		else {
			fieldPath = path;
		}

		List<Predicate> predicates = stream(value)
			.map(item -> buildTypedPredicate(fieldPath, type, field, item, query, criteriaBuilder, pathResolver, parameterBuilder))
			.filter(Objects::nonNull)
			.collect(toList());

		if (predicates.isEmpty()) {
			throw new IllegalArgumentException(value.toString());
		}

		Predicate predicate = criteriaBuilder.or(toArray(predicates));

		if (oneToManySubquery != null) {
			// SELECT e FROM E e WHERE (SELECT COUNT(DISTINCT field) FROM T t WHERE [restrictions] AND t.id = e.id) = ACTUALCOUNT
			Long actualCount = (long) predicates.size();
			predicate = criteriaBuilder.equal(oneToManySubquery.where(criteriaBuilder.and(predicate, oneToManySubquery.getRestriction())), actualCount);
			Alias.create(provider, pathResolver.get(pathResolver.join(field)), field).set(predicate);
		}

		return predicate;
	}


	// Helpers --------------------------------------------------------------------------------------------------------

	@FunctionalInterface
	private static interface PathResolver {
		Expression<?> get(String field);

		default String join(String field) {
			return '@' + field;
		}
	}

	private static class RootPathResolver implements PathResolver {

		private Root<?> root;
		private Map<String, Path<?>> joins;
		private Map<String, Path<?>> paths;
		private Set<String> elementCollections;

		private RootPathResolver(Root<?> root, Set<String> elementCollections) {
			this.root = root;
			this.joins = getJoins(root);
			this.paths = new HashMap<>();
			this.elementCollections = elementCollections;
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
			boolean explicitJoin = field.charAt(0) == '@';
			String originalField = explicitJoin ? field.substring(1) : field;
			String[] attributes = originalField.split("\\.");
			int depth = attributes.length;

			for (int i = 0; i < depth; i++) {
				String attribute = attributes[i];

				if (i + 1 < depth || elementCollections.contains(originalField)) {
					path = explicitJoin ? ((From<?, ?>) path).join(attribute) : joins.get(attribute);
				}
				else {
					path = path.get(attribute);
				}
			}

			paths.put(field, path);
			return path;
		}

	}

	private static class Alias {

		private static final String AS = "as_";
		private static final String WHERE = "where_";
		private static final String HAVING = "having_";
		private static final String IN = "_in";

		private String value;

		private Alias(String alias) {
			this.value = alias;
		}

		public static Selection<?> as(Entry<String, Expression<?>> mappingEntry) {
			Selection<?> selection = mappingEntry.getValue();
			return selection.getAlias() != null ? selection : selection.alias(AS + mappingEntry.getKey().replace(".", "_"));
		}

		public static Alias create(Provider provider, Expression<?> expression, String field) {
			return new Alias((provider.isAggregation(expression) ? HAVING : WHERE) + field.replace(".", "$"));
		}

		public void in(int count) {
			value += "_" + count + IN;
		}

		public void set(Predicate predicate) {
			predicate.alias(value);
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

		public static Entry<String, Long> getFieldAndCount(Predicate inPredicate) {
			String alias = inPredicate.getAlias();
			String fieldAndCount = alias.substring(alias.indexOf('_') + 1, alias.lastIndexOf('_'));
			String field = fieldAndCount.substring(0, fieldAndCount.lastIndexOf('_')).replace('$', '.');
			long count = Long.valueOf(fieldAndCount.substring(field.length() + 1));
			return new SimpleEntry<>(field, count);
		}

		public static void setHaving(Predicate inPredicate, Predicate countPredicate) {
			countPredicate.alias(HAVING + inPredicate.getAlias().substring(inPredicate.getAlias().indexOf("_") + 1));
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

	private static Predicate[] toArray(List<Predicate> predicates) {
		return predicates.toArray(new Predicate[predicates.size()]);
	}

	private static Predicate conjunctRestrictionsIfNecessary(CriteriaBuilder criteriaBuilder, Predicate nullable, Predicate nonnullable) {
		return nullable == null ? nonnullable : criteriaBuilder.and(nullable, nonnullable);
	}

	private static Predicate conjunctRestrictionsIfNecessary(CriteriaBuilder criteriaBuilder, Predicate nullable, List<Predicate> nonnullable) {
		return conjunctRestrictionsIfNecessary(criteriaBuilder, nullable, criteriaBuilder.and(toArray(nonnullable)));
	}

	private static Predicate buildCountPredicateIfNecessary(Predicate inPredicate, CriteriaBuilder criteriaBuilder, AbstractQuery<?> query, PathResolver pathResolver) {
		Entry<String, Long> fieldAndCount = Alias.getFieldAndCount(inPredicate);

		if (fieldAndCount.getValue() > 1) {
			Expression<?> join = pathResolver.get(pathResolver.join(fieldAndCount.getKey()));
			Predicate countPredicate = criteriaBuilder.equal(criteriaBuilder.countDistinct(join), fieldAndCount.getValue());
			Alias.setHaving(inPredicate, countPredicate);
			groupByIfNecessary(query, pathResolver.get(fieldAndCount.getKey()));
			return countPredicate;
		}

		return null;
	}

	private static void groupByIfNecessary(AbstractQuery<?> query, Expression<?> path) {
		Expression<?> groupByPath = (path instanceof RootWrapper) ? ((RootWrapper<?>) path).getWrapped() : path;

		if (!query.getGroupList().contains(groupByPath)) {
			List<Expression<?>> groupList = new ArrayList<>(query.getGroupList());
			groupList.add(groupByPath);
			query.groupBy(groupList);
		}
	}

	private static boolean hasJoins(From<?, ?> from) {
		return !from.getJoins().isEmpty() || hasFetches(from);
	}

	private static boolean hasFetches(From<?, ?> from) {
		return from.getFetches().stream().anyMatch(fetch -> fetch instanceof Path)
			|| (from instanceof EclipseLinkRoot && !((EclipseLinkRoot<?>) from).getPostponedFetches().isEmpty());
	}

	private static Map<String, Path<?>> getJoins(From<?, ?> from) {
		Map<String, Path<?>> joins = new HashMap<>(from.getJoins().stream().collect(toMap(join -> join.getAttribute().getName())));
		joins.putAll(from.getFetches().stream().filter(fetch -> fetch instanceof Path).collect(toMap(fetch -> fetch.getAttribute().getName(), fetch -> (Path<?>) fetch)));

		if (from instanceof EclipseLinkRoot) {
			((EclipseLinkRoot<?>) from).getPostponedFetches().forEach(fetch -> joins.put(fetch, from.get(fetch)));
		}

		return joins;
	}

	private static <T> T noop() {
		return null;
	}

}
