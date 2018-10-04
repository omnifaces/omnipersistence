/*
 * Copyright 2018 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.service;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.reverse;
import static java.util.Collections.unmodifiableSet;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.IntStream.range;
import static javax.persistence.CacheRetrieveMode.BYPASS;
import static javax.persistence.metamodel.PluralAttribute.CollectionType.MAP;
import static org.omnifaces.persistence.Database.POSTGRESQL;
import static org.omnifaces.persistence.JPA.QUERY_HINT_CACHE_RETRIEVE_MODE;
import static org.omnifaces.persistence.JPA.QUERY_HINT_CACHE_STORE_MODE;
import static org.omnifaces.persistence.JPA.QUERY_HINT_LOAD_GRAPH;
import static org.omnifaces.persistence.JPA.countForeignKeyReferences;
import static org.omnifaces.persistence.JPA.getOptionalFirstResult;
import static org.omnifaces.persistence.JPA.getValidationMode;
import static org.omnifaces.persistence.Provider.ECLIPSELINK;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.Provider.OPENJPA;
import static org.omnifaces.persistence.Provider.QUERY_HINT_ECLIPSELINK_MAINTAIN_CACHE;
import static org.omnifaces.persistence.Provider.QUERY_HINT_ECLIPSELINK_REFRESH;
import static org.omnifaces.persistence.Provider.QUERY_HINT_HIBERNATE_CACHEABLE;
import static org.omnifaces.persistence.model.EnumMappingTable.MappingType.NO_ACTION;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.Lang.capitalize;
import static org.omnifaces.utils.Lang.coalesce;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.reflect.Reflections.getActualTypeArguments;
import static org.omnifaces.utils.reflect.Reflections.invokeGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.invokeSetter;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedEnumFields;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedFields;
import static org.omnifaces.utils.reflect.Reflections.map;
import static org.omnifaces.utils.stream.Streams.stream;

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.enterprise.inject.spi.CDI;
import javax.naming.InitialContext;
import javax.persistence.CacheRetrieveMode;
import javax.persistence.CacheStoreMode;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.NonUniqueResultException;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.ValidationMode;
import javax.persistence.criteria.AbstractQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.PluralAttribute.CollectionType;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

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
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.EnumMapping;
import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.model.NonDeletable;
import org.omnifaces.persistence.model.SoftDeletable;
import org.omnifaces.persistence.model.TimestampedBaseEntity;
import org.omnifaces.persistence.model.TimestampedEntity;
import org.omnifaces.persistence.model.VersionedBaseEntity;
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
 * <li>{@link TimestampedBaseEntity}
 * <li>{@link VersionedBaseEntity}
 * <li>{@link GeneratedIdEntity}
 * <li>{@link TimestampedEntity}
 * <li>{@link VersionedEntity}
 * </ul>
 *
 * <h3>Logging</h3>
 * <p>
 * {@link BaseEntityService} uses JULI {@link Logger} for logging.
 * <ul>
 * <li>{@link Level#FINER} will log the {@link #getPage(Page, boolean)} arguments, the set parameter values and the full query result.
 * <li>{@link Level#FINE} will log computed type mapping (the actual values of <code>I</code> and <code>E</code> type paramters), and
 * whether the ID is generated, and whether the entity is {@link SoftDeletable}, and whether any {@link EnumMapping} is modified, and
 * any discovered {@link ElementCollection}, {@link ManyToOne}, {@link OneToOne} and {@link OneToMany} mappings of the entity. This is
 * internally used in order to be able to build proper queries to perform a search inside those fields.
 * <li>{@link Level#INFO} will log both successful and unsuccessful attempts to modify enum representations basing on {@link EnumMapping}.
 * <li>{@link Level#WARNING} will log unparseable or illegal criteria values. The {@link BaseEntityService} will skip them and continue.
 * <li>{@link Level#SEVERE} will log constraint violations wrapped in any {@link ConstraintViolationException} during
 * {@link #persist(BaseEntity)} and {@link #update(BaseEntity)}. Due to technical limitations, it will during <code>update()</code> only
 * happen when <code>javax.persistence.validation.mode</code> property in <code>persistence.xml</code> is explicitly set to
 * <code>CALLBACK</code> (and thus not to its default of <code>AUTO</code>).
 * </ul>
 *
 * @param <I> The generic ID type.
 * @param <E> The generic base entity type.
 * @see BaseEntity
 * @see Page
 * @see Criteria
 * @see EnumMappingTableService
 */
public abstract class BaseEntityService<I extends Comparable<I> & Serializable, E extends BaseEntity<I>> {

	private static final Logger logger = Logger.getLogger(BaseEntityService.class.getName());

	private static final String LOG_FINER_GET_PAGE = "Get page: %s, count=%s, cacheable=%s, resultType=%s";
	private static final String LOG_FINER_SET_PARAMETER_VALUES = "Set parameter values: %s";
	private static final String LOG_FINER_QUERY_RESULT = "Query result: %s, estimatedTotalNumberOfResults=%s";
	private static final String LOG_FINE_COMPUTED_TYPE_MAPPING = "Computed type mapping for %s: <%s, %s>";
	private static final String LOG_FINE_COMPUTED_GENERATED_ID_MAPPING = "Computed generated ID mapping for %s: %s";
	private static final String LOG_FINE_COMPUTED_SOFT_DELETE_MAPPING = "Computed soft delete mapping for %s: %s";
	private static final String LOG_FINE_COMPUTED_ELEMENTCOLLECTION_MAPPING = "Computed @ElementCollection mapping for %s: %s";
	private static final String LOG_FINE_COMPUTED_MANY_OR_ONE_TO_ONE_MAPPING = "Computed @ManyToOne/@OneToOne mapping for %s: %s";
	private static final String LOG_FINE_COMPUTED_ONE_TO_MANY_MAPPING = "Computed @OneToMany mapping for %s: %s";
	private static final String LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING = "Enum mapping for enum %s: was %smodified";
	private static final String LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING_TABLE = "Enum mapping table for enum %s: was %smodified";
	private static final String LOG_WARNING_ILLEGAL_CRITERIA_VALUE = "Cannot parse predicate for %s(%s) = %s(%s), skipping!";
	private static final String LOG_SEVERE_CONSTRAINT_VIOLATION = "javax.validation.ConstraintViolation: @%s %s#%s %s on %s";

	private static final String ERROR_ILLEGAL_MAPPING =
		"You must return a getter-path mapping from MappedQueryBuilder";
	private static final String ERROR_UNSUPPORTED_CRITERIA =
		"Predicate for %s(%s) = %s(%s) is not supported. Consider wrapping in a Criteria instance or creating a custom one if you want to deal with it.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_ECLIPSELINK =
		"Sorry, EclipseLink does not support sorting a @OneToMany or @ElementCollection relationship. Consider using a DTO or a DB view instead.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_OPENJPA =
		"Sorry, OpenJPA does not support sorting a @OneToMany or @ElementCollection relationship. Consider using a DTO or a DB view instead.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_ECLIPSELINK =
		"Sorry, EclipseLink does not support searching in a @OneToMany relationship. Consider using a DTO or a DB view instead.";
	private static final String ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_OPENJPA =
		"Sorry, OpenJPA does not support searching in a @OneToMany relationship. Consider using a DTO or a DB view instead.";

	@SuppressWarnings("rawtypes")
	private static final Map<Class<? extends BaseEntityService>, Entry<Class<?>, Class<?>>> TYPE_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends BaseEntity<?>>, Boolean> GENERATED_ID_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends BaseEntity<?>>, SoftDeleteData> SOFT_DELETE_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends BaseEntity<?>>, Set<String>> ELEMENT_COLLECTION_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends BaseEntity<?>>, Set<String>> MANY_OR_ONE_TO_ONE_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends BaseEntity<?>>, Set<String>> ONE_TO_MANY_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends BaseEntity<?>>, Boolean> CHECKED_ENUM_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends Enum<?>>, Boolean> MODIFIED_ENUM_MAPPINGS = new ConcurrentHashMap<>();
	private static final Map<Class<? extends Enum<?>>, Boolean> MODIFIED_ENUM_TABLE_MAPPINGS = new ConcurrentHashMap<>();

	private final Class<I> identifierType;
	private final Class<E> entityType;
	private final boolean generatedId;
	private final SoftDeleteData softDeleteData;

	private Provider provider;
	private Database database;
	private Set<String> elementCollections;
	private Set<String> manyOrOneToOnes;
	private java.util.function.Predicate<String> oneToManys;
	private Validator validator;

	@PersistenceContext
	private EntityManager entityManager;


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
		generatedId = GENERATED_ID_MAPPINGS.computeIfAbsent(entityType, BaseEntityService::computeGeneratedIdMapping);
		softDeleteData = SOFT_DELETE_MAPPINGS.computeIfAbsent(entityType, BaseEntityService::computeSoftDeleteMapping);
		CHECKED_ENUM_MAPPINGS.computeIfAbsent(entityType, BaseEntityService::computeModifiedEnumMapping);
	}

	/**
	 * The postconstruct initializes the properties dependent on entity manager.
	 */
	@PostConstruct
	private void initWithEntityManager() {
		provider = Provider.of(getEntityManager());
		database = Database.of(getEntityManager());
		elementCollections = ELEMENT_COLLECTION_MAPPINGS.computeIfAbsent(entityType, this::computeElementCollectionMapping);
		manyOrOneToOnes = MANY_OR_ONE_TO_ONE_MAPPINGS.computeIfAbsent(entityType, this::computeManyOrOneToOneMapping);
		oneToManys = field -> ONE_TO_MANY_MAPPINGS.computeIfAbsent(entityType, this::computeOneToManyMapping).stream().anyMatch(oneToMany -> field.startsWith(oneToMany + '.'));

		if (getValidationMode(getEntityManager()) == ValidationMode.CALLBACK) {
			validator = CDI.current().select(Validator.class).get();
		}
	}

	@SuppressWarnings("rawtypes")
	private static Entry<Class<?>, Class<?>> computeTypeMapping(Class<? extends BaseEntityService> subclass) {
		List<Class<?>> actualTypeArguments = getActualTypeArguments(subclass, BaseEntityService.class);
		Class<?> identifierType = actualTypeArguments.get(0);
		Class<?> entityType = actualTypeArguments.get(1);
		logger.log(FINE, () -> format(LOG_FINE_COMPUTED_TYPE_MAPPING, subclass, identifierType, entityType));
		return new SimpleEntry<>(identifierType, entityType);
	}

	private static boolean computeGeneratedIdMapping(Class<?> entityType) {
		boolean generatedId = GeneratedIdEntity.class.isAssignableFrom(entityType) || !listAnnotatedFields(entityType, Id.class, GeneratedValue.class).isEmpty();
		logger.log(FINE, () -> format(LOG_FINE_COMPUTED_GENERATED_ID_MAPPING, entityType, generatedId));
		return generatedId;
	}

	private static SoftDeleteData computeSoftDeleteMapping(Class<?> entityType) {
		SoftDeleteData softDeleteData = new SoftDeleteData(entityType);
		logger.log(FINE, () -> format(LOG_FINE_COMPUTED_SOFT_DELETE_MAPPING, entityType, softDeleteData));
		return softDeleteData;
	}

	private static boolean computeModifiedEnumMapping(Class<?> entityType) {
		List<Class<? extends Enum<?>>> enumsToUpdate = listAnnotatedEnumFields(entityType, javax.persistence.Enumerated.class).stream()
			.filter(enumeratedType -> enumeratedType.isAnnotationPresent(EnumMapping.class) && !MODIFIED_ENUM_MAPPINGS.containsKey(enumeratedType))
			.peek(enumeratedType -> {
				boolean modified = EnumMappingTableService.modifyEnumMapping(enumeratedType);
				logger.log(INFO, () -> format(LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING, enumeratedType, modified ? "" : "not "));
				MODIFIED_ENUM_MAPPINGS.put(enumeratedType, modified);
			})
			.filter(enumeratedType -> !MODIFIED_ENUM_TABLE_MAPPINGS.containsKey(enumeratedType) && enumeratedType.getAnnotation(EnumMapping.class).enumMappingTable().mappingType() != NO_ACTION)
			.collect(toList());

		if (!enumsToUpdate.isEmpty()) {
			EnumMappingTableService emts = CDI.current().select(EnumMappingTableService.class).get();
			emts.computeModifiedEnumMappingTable(enumsToUpdate).forEach((enumeratedType, modified) -> {
				logger.log(INFO, () -> format(LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING_TABLE, enumeratedType, modified ? "" : "not "));
				MODIFIED_ENUM_TABLE_MAPPINGS.put(enumeratedType, modified);
			});
		}

		return true;
	}

	private Set<String> computeElementCollectionMapping(Class<? extends BaseEntity<?>> entityType) {
		Set<String> elementCollectionMapping = computeEntityMapping(entityType, "", new HashSet<>(), provider::isElementCollection);
		logger.log(FINE, () -> format(LOG_FINE_COMPUTED_ELEMENTCOLLECTION_MAPPING, entityType, elementCollectionMapping));
		return elementCollectionMapping;
	}

	private Set<String> computeManyOrOneToOneMapping(Class<? extends BaseEntity<?>> entityType) {
		Set<String> manyOrOneToOneMapping = computeEntityMapping(entityType, "", new HashSet<>(), provider::isManyOrOneToOne);
		logger.log(FINE, () -> format(LOG_FINE_COMPUTED_MANY_OR_ONE_TO_ONE_MAPPING, entityType, manyOrOneToOneMapping));
		return manyOrOneToOneMapping;
	}

	private Set<String> computeOneToManyMapping(Class<? extends BaseEntity<?>> entityType) {
		Set<String> oneToManyMapping = computeEntityMapping(entityType, "", new HashSet<>(), provider::isOneToMany);
		logger.log(FINE, () -> format(LOG_FINE_COMPUTED_ONE_TO_MANY_MAPPING, entityType, oneToManyMapping));
		return oneToManyMapping;
	}

	private Set<String> computeEntityMapping(Class<?> type, String basePath, Set<Class<?>> nestedTypes, java.util.function.Predicate<Attribute<?, ?>> attributePredicate) {
		Set<String> entityMapping = new HashSet<>(2);
		EntityType<?> entity = getEntityManager().getMetamodel().entity(type);

		for (Attribute<?, ?> attribute : entity.getAttributes()) {
			if (attributePredicate.test(attribute)) {
				entityMapping.add(basePath + attribute.getName());
			}

			if (attribute instanceof Bindable) {
				Class<?> nestedType = ((Bindable<?>) attribute).getBindableJavaType();

				if (BaseEntity.class.isAssignableFrom(nestedType) && nestedType != entityType && nestedTypes.add(nestedType)) {
					entityMapping.addAll(computeEntityMapping(nestedType, basePath + attribute.getName() + '.', nestedTypes, attributePredicate));
				}
			}
		}

		return unmodifiableSet(entityMapping);
	}


	// Getters --------------------------------------------------------------------------------------------------------

	/**
	 * Returns the JPA provider being used. This is immutable (you can't override the method to change the internally used value).
	 * @return The JPA provider being used.
	 */
	public Provider getProvider() {
		return provider;
	}

	/**
	 * Returns the SQL database being used. This is immutable (you can't override the method to change the internally used value).
	 * @return The SQL database being used.
	 */
	public Database getDatabase() {
		return database;
	}

	/**
	 * Returns the actual type of the generic ID type <code>I</code>. This is immutable (you can't override the method to change the internally used value).
	 * @return The actual type of the generic ID type <code>I</code>.
	 */
	protected Class<I> getIdentifierType() {
		return identifierType;
	}

	/**
	 * Returns the actual type of the generic base entity type <code>E</code>. This is immutable (you can't override the method to change the internally used value).
	 * @return The actual type of the generic base entity type <code>E</code>.
	 */
	protected Class<E> getEntityType() {
		return entityType;
	}

	/**
	 * Returns whether the ID is generated. This is immutable (you can't override the method to change the internally used value).
	 * @return Whether the ID is generated.
	 */
	protected boolean isGeneratedId() {
		return generatedId;
	}

	/**
	 * Returns the entity manager being used. When you have only one persistence unit, then you don't need to override
	 * this. When you have multiple persistence units, then you need to extend the {@link BaseEntityService} like below
	 * wherein you supply the persistence unit specific entity manager and then let all your service classes extend
	 * from it instead.
	 * <pre>
	 * public abstract class YourBaseEntityService&lt;E extends BaseEntity&lt;Long&gt;&gt; extends BaseEntityService&lt;Long, E&gt; {
	 *
	 *     &#64;PersistenceContext(unitName = "yourPersistenceUnitName")
	 *     private EntityManager entityManager;
	 *
	 *     &#64;Override
	 *     public EntityManager getEntityManager() {
	 *         return entityManager;
	 *     }
	 *
	 * }
	 * </pre>
	 *
	 * @return The entity manager being used.
	 */
	protected EntityManager getEntityManager() {
		return entityManager;
	}

	/**
	 * Returns the metamodel of current base entity.
	 * @return The metamodel of current base entity.
	 */
	protected EntityType<E> getMetamodel() {
		return getEntityManager().getMetamodel().entity(entityType);
	}

	/**
	 * Returns the metamodel of given base entity.
	 * @param <I> The generic ID type of the given base entity.
	 * @param <E> The generic base entity type of the given base entity.
	 * @param entity Base entity to obtain metamodel for.
	 * @return The metamodel of given base entity.
	 */
	@SuppressWarnings({ "unchecked", "hiding" })
	public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> EntityType<E> getMetamodel(E entity) {
		return getEntityManager().getMetamodel().entity((Class<E>) entity.getClass());
	}


	// Preparing actions ----------------------------------------------------------------------------------------------

	/**
	 * Create an instance of {@link TypedQuery} for executing a Java Persistence Query Language statement identified
	 * by the given name, usually to perform a SELECT e.
	 * @param name The name of the Java Persistence Query Language statement defined in metadata, which can be either
	 * a {@link NamedQuery} or a <code>&lt;persistence-unit&gt;&lt;mapping-file&gt;</code>.
	 * @return An instance of {@link TypedQuery} for executing a Java Persistence Query Language statement identified
	 * by the given name, usually to perform a SELECT e.
	 */
	protected TypedQuery<E> createNamedTypedQuery(String name) {
		return getEntityManager().createNamedQuery(name, entityType);
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
		return getEntityManager().createNamedQuery(name);
	}

	/**
	 * Create an instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement,
	 * usually to perform a SELECT e.
	 * @param jpql The Java Persistence Query Language statement.
	 * @return An instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement,
	 * usually to perform a SELECT.
	 */
	protected TypedQuery<E> createTypedQuery(String jpql) {
		return getEntityManager().createQuery(jpql, entityType);
	}

	/**
	 * Create an instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement which
	 * returns a <code>Long</code>, usually a SELECT e.id or SELECT COUNT(e).
	 * @param jpql The Java Persistence Query Language statement.
	 * @return An instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement which
	 * returns a <code>Long</code>, usually a SELECT e.id or SELECT COUNT(e).
	 */
	protected TypedQuery<Long> createLongQuery(String jpql) {
		return getEntityManager().createQuery(jpql, Long.class);
	}

	/**
	 * Create an instance of {@link Query} for executing the given Java Persistence Query Language statement,
	 * usually to perform an INSERT, UPDATE or DELETE.
	 * @param jpql The Java Persistence Query Language statement.
	 * @return An instance of {@link Query} for executing the given Java Persistence Query Language statement,
	 * usually to perform an INSERT, UPDATE or DELETE.
	 */
	protected Query createQuery(String jpql) {
		return getEntityManager().createQuery(jpql);
	}


	// Select actions -------------------------------------------------------------------------------------------------

	/**
	 * Functional interface to fine-grain a JPA criteria query for any of
	 * {@link #list(CriteriaQueryBuilder, Consumer)} or {@link #find(CriteriaQueryBuilder, Consumer)} methods.
	 * <p>
	 * You do not need this interface directly. Just supply a lambda. Below is an usage example:
	 * <pre>
	 * &#64;Stateless
	 * public class YourEntityService extends BaseEntityService&lt;YourEntity&gt; {
	 *
	 *     public List&lt;YourEntity&gt; getFooByType(Type type) {
	 *         return list((criteriaBuilder, query, root) -&gt; {
	 *             query.where(criteriaBuilder.equal(root.get("type"), type));
	 *         }, noop());
	 *     }
	 *
	 * }
	 * </pre>
	 * @param <E> The generic base entity type.
	 */
	@FunctionalInterface
	protected interface CriteriaQueryBuilder<E> {
		void build(CriteriaBuilder criteriaBuilder, CriteriaQuery<E> query, Root<E> root);
	}

	/**
	 * Find entity by the given query and positional parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; foo = find("SELECT f FROM Foo f WHERE f.bar = ?1 AND f.baz = ?2", bar, baz);
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters The positional query parameters, if any.
	 * @return Found entity matching the given query and positional parameters, if any.
	 * @throws NonUniqueResultException When more than one entity is found matching the given query and positional parameters.
	 */
	protected Optional<E> find(String jpql, Object... parameters) {
		return getOptionalSingleResult(list(jpql, parameters));
	}

	/**
	 * Find entity by the given query and mapped parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; foo = find("SELECT f FROM Foo f WHERE f.bar = :bar AND f.baz = :baz", params -&gt; {
	 *     params.put("bar", bar);
	 *     params.put("baz", baz);
	 * });
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters To put the mapped query parameters in.
	 * @return Found entity matching the given query and mapped parameters, if any.
	 * @throws NonUniqueResultException When more than one entity is found matching the given query and mapped parameters.
	 */
	protected Optional<E> find(String jpql, Consumer<Map<String, Object>> parameters) {
		return getOptionalSingleResult(list(jpql, parameters));
	}

	/**
	 * Find entity by {@link CriteriaQueryBuilder} and mapped parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; foo = find(
	 * 		(criteriaBuilder, query, root) -&gt; {
	 * 			query.where(criteriaBuilder.equal(root.get("type"), criteriaBuilder.parameter(Type.class, "foo"));
	 * 		},
	 * 		params -&gt; {
	 *     		params.put("foo", Type.FOO);
	 * 		}
	 * );
	 * </pre>
	 * @param queryBuilder This creates the JPA criteria query.
	 * @param parameters To put the mapped query parameters in.
	 * @return Found entity matching {@link CriteriaQueryBuilder} and mapped parameters, if any.
	 * @throws NonUniqueResultException When more than one entity is found matching given query and mapped parameters.
	 */
	protected Optional<E> find(CriteriaQueryBuilder<E> queryBuilder, Consumer<Map<String, Object>> parameters) {
		return getOptionalSingleResult(list(queryBuilder, parameters));
	}

	private Optional<E> getOptionalSingleResult(List<E> results) {
		if (results.isEmpty()) {
			return Optional.empty();
		}
		else if (results.size() == 1) {
			return Optional.of(results.get(0));
		}
		else {
			throw new NonUniqueResultException();
		}
	}

	/**
	 * Find first entity by the given query and positional parameters, if any.
	 * The difference with {@link #find(String, Object...)} is that it doesn't throw {@link NonUniqueResultException} when there are multiple matches.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; foo = findFirst("SELECT f FROM Foo f WHERE f.bar = ?1 AND f.baz = ?2", bar, baz);
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters The positional query parameters, if any.
	 * @return Found entity matching the given query and positional parameters, if any.
	 */
	protected Optional<E> findFirst(String jpql, Object... parameters) {
		return getOptionalFirstResult(createQuery(jpql, parameters));
	}

	/**
	 * Find first entity by the given query and mapped parameters, if any.
	 * The difference with {@link #find(String, Consumer)} is that it doesn't throw {@link NonUniqueResultException} when there are multiple matches.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; foo = findFirst("SELECT f FROM Foo f WHERE f.bar = :bar AND f.baz = :baz", params -&gt; {
	 *     params.put("bar", bar);
	 *     params.put("baz", baz);
	 * });
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters To put the mapped query parameters in.
	 * @return Found entity matching the given query and mapped parameters, if any.
	 */
	protected Optional<E> findFirst(String jpql, Consumer<Map<String, Object>> parameters) {
		return getOptionalFirstResult(createQuery(jpql, parameters));
	}

	/**
	 * Find first entity by {@link CriteriaQueryBuilder} and mapped parameters, if any.
	 * The difference with {@link #find(CriteriaQueryBuilder, Consumer)} is that it doesn't throw {@link NonUniqueResultException} when there are multiple matches.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; foo = findFirst(
	 * 		(criteriaBuilder, query, root) -&gt; {
	 * 			query.where(criteriaBuilder.equal(root.get("type"), criteriaBuilder.parameter(Type.class, "foo"));
	 * 		},
	 * 		params -&gt; {
	 *     		params.put("foo", Type.FOO);
	 * 		}
	 * );
	 * </pre>
	 * @param queryBuilder This creates the JPA criteria query.
	 * @param parameters To put the mapped query parameters in.
	 * @return Found entity matching {@link CriteriaQueryBuilder} and mapped parameters, if any.
	 */
	protected Optional<E> findFirst(CriteriaQueryBuilder<E> queryBuilder, Consumer<Map<String, Object>> parameters) {
		return getOptionalFirstResult(createQuery(queryBuilder, parameters));
	}

	/**
	 * Find entity by the given ID. This does not include soft deleted one.
	 * @param id Entity ID to find entity for.
	 * @return Found entity, if any.
	 */
	public Optional<E> findById(I id) {
		return Optional.ofNullable(getById(id, false));
	}

	/**
	 * Find entity by the given ID and set whether it may return a soft deleted one.
	 * @param id Entity ID to find entity for.
	 * @param includeSoftDeleted Whether to include soft deleted ones in the search.
	 * @return Found entity, if any.
	 */
	protected Optional<E> findById(I id, boolean includeSoftDeleted) {
		return Optional.ofNullable(getById(id, includeSoftDeleted));
	}

	/**
	 * Find soft deleted entity by the given ID.
	 * @param id Entity ID to find soft deleted entity for.
	 * @return Found soft deleted entity, if any.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 */
	public Optional<E> findSoftDeletedById(I id) {
		return Optional.ofNullable(getSoftDeletedById(id));
	}

	/**
	 * Get entity by the given ID. This does not include soft deleted one.
	 * @param id Entity ID to get entity by.
	 * @return Found entity, or <code>null</code> if there is none.
	 */
	public E getById(I id) {
		return getById(id, false);
	}

	/**
	 * Get entity by the given ID and set whether it may return a soft deleted one.
	 * @param id Entity ID to get entity by.
	 * @param includeSoftDeleted Whether to include soft deleted ones in the search.
	 * @return Found entity, or <code>null</code> if there is none.
	 */
	protected E getById(I id, boolean includeSoftDeleted) {
		E entity = getEntityManager().find(entityType, id);

		if (entity != null && !includeSoftDeleted && softDeleteData.isSoftDeleted(entity)) {
			return null;
		}

		return entity;
	}

	/**
	 * Get entity by the given ID and entity graph name.
	 * @param id Entity ID to get entity by.
	 * @param entityGraphName Entity graph name.
	 * @return Found entity, or <code>null</code> if there is none.
	 */
	public E getByIdWithLoadGraph(I id, String entityGraphName) {
		EntityGraph<?> entityGraph = entityManager.getEntityGraph(entityGraphName);

		Map<String, Object> properties = new HashMap<>();
		properties.put(QUERY_HINT_LOAD_GRAPH, entityGraph);
		properties.put(QUERY_HINT_CACHE_RETRIEVE_MODE, BYPASS);

		return getEntityManager().find(entityType, id, properties);
	}

	/**
	 * Get soft deleted entity by the given ID.
	 * @param id Entity ID to get soft deleted entity by.
	 * @return Found soft deleted entity, or <code>null</code> if there is none.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 */
	public E getSoftDeletedById(I id) {
		softDeleteData.checkSoftDeletable();
		E entity = getEntityManager().find(entityType, id);

		if (entity != null && !softDeleteData.isSoftDeleted(entity)) {
			return null;
		}

		return entity;
	}

	/**
	 * Get entities by the given IDs. The default ordering is by ID, descending. This does not include soft deleted ones.
	 * @param ids Entity IDs to get entities by.
	 * @return Found entities, or an empty set if there is none.
	 */
	public List<E> getByIds(Iterable<I> ids) {
		return getByIds(ids, false);
	}

	/**
	 * Get entities by the given IDs and set whether it may include soft deleted ones. The default ordering is by ID, descending.
	 * @param ids Entity IDs to get entities by.
	 * @param includeSoftDeleted Whether to include soft deleted ones in the search.
	 * @return Found entities, optionally including soft deleted ones, or an empty set if there is none.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 */
	protected List<E> getByIds(Iterable<I> ids, boolean includeSoftDeleted) {
		if (!ids.iterator().hasNext()) {
			return emptyList();
		}

		String whereClause = softDeleteData.getWhereClause(includeSoftDeleted);
		return list("SELECT e FROM " + entityType.getSimpleName() + " e"
			+ whereClause + (whereClause.isEmpty() ? " WHERE" : " AND") + " e.id IN (?1)"
			+ " ORDER BY e.id DESC", ids);
	}

	/**
	 * Check whether given entity exists.
	 * This method supports proxied entities.
	 * @param entity Entity to check.
	 * @return Whether entity with given entity exists.
	 */
	protected boolean exists(E entity) {
		I id = provider.getIdentifier(entity);
		return id != null && getEntityManager()
			.createQuery("SELECT COUNT(e) FROM " + entityType.getSimpleName() + " e WHERE e.id = :id", Long.class)
			.setParameter("id", id)
			.getSingleResult().intValue() > 0;
	}

	/**
	 * List all entities. The default ordering is by ID, descending. This does not include soft deleted entities.
	 * @return List of all entities.
	 */
	public List<E> list() {
		return list(false);
	}

	/**
	 * List all entities and set whether it may include soft deleted ones. The default ordering is by ID, descending.
	 * @param includeSoftDeleted Whether to include soft deleted ones in the search.
	 * @return List of all entities, optionally including soft deleted ones.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 */
	protected List<E> list(boolean includeSoftDeleted) {
		return list("SELECT e FROM " + entityType.getSimpleName() + " e"
			+ softDeleteData.getWhereClause(includeSoftDeleted)
			+ " ORDER BY e.id DESC");
	}

	/**
	 * List all entities that have been soft deleted. The default ordering is by ID, descending.
	 * @return List of all soft deleted entities.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 */
	public List<E> listSoftDeleted() {
		softDeleteData.checkSoftDeletable();
		return list("SELECT e FROM " + entityType.getSimpleName() + " e"
			+ softDeleteData.getWhereClause(true)
			+ " ORDER BY e.id DESC");
	}

	/**
	 * List entities matching the given query and positional parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * List&lt;Foo&gt; foos = list("SELECT f FROM Foo f WHERE f.bar = ?1 AND f.baz = ?2", bar, baz);
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters The positional query parameters, if any.
	 * @return List of entities matching the given query and positional parameters, if any.
	 */
	protected List<E> list(String jpql, Object... parameters) {
		return createQuery(jpql, parameters).getResultList();
	}

	/**
	 * List entities matching the given query and mapped parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * List&lt;Foo&gt; foos = list("SELECT f FROM Foo f WHERE f.bar = :bar AND f.baz = :baz", params -&gt; {
	 *     params.put("bar", bar);
	 *     params.put("baz", baz);
	 * });
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters To put the mapped query parameters in.
	 * @return List of entities matching the given query and mapped parameters, if any.
	 */
	protected List<E> list(String jpql, Consumer<Map<String, Object>> parameters) {
		return createQuery(jpql, parameters).getResultList();
	}

	/**
	 * List entities matching the {@link CriteriaQueryBuilder} and mapped parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * List&lt;Foo&gt; foo = list(
	 * 		(criteriaBuilder, query, root) -&gt; {
	 * 			query.where(criteriaBuilder.equal(root.get("type"), criteriaBuilder.parameter(Type.class, "foo"));
	 * 		},
	 * 		params -&gt; {
	 *     		params.put("foo", Type.FOO);
	 * 		}
	 * );
	 * </pre>
	 * @param queryBuilder This creates the JPA criteria query.
	 * @param parameters To put the mapped query parameters in.
	 * @return List of entities matching the {@link CriteriaQueryBuilder} and mapped parameters, if any.
	 */
	protected List<E> list(CriteriaQueryBuilder<E> queryBuilder, Consumer<Map<String, Object>> parameters) {
		return createQuery(queryBuilder, parameters).getResultList();
	}

	private TypedQuery<E> createQuery(String jpql, Object... parameters) {
		TypedQuery<E> query = getEntityManager().createQuery(jpql, entityType);
		setPositionalParameters(query, parameters);
		return query;
	}

	private TypedQuery<E> createQuery(String jpql, Consumer<Map<String, Object>> parameters) {
		TypedQuery<E> query = getEntityManager().createQuery(jpql, entityType);
		setSuppliedParameters(query, parameters);
		return query;
	}

	private TypedQuery<E> createQuery(CriteriaQueryBuilder<E> queryBuilder, Consumer<Map<String, Object>> parameters) {
		CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
		CriteriaQuery<E> criteriaQuery = criteriaBuilder.createQuery(entityType);
		Root<E> root = buildRoot(criteriaQuery);

		queryBuilder.build(criteriaBuilder, criteriaQuery, root);

		TypedQuery<E> query = getEntityManager().createQuery(criteriaQuery);

		if (root instanceof EclipseLinkRoot) {
			((EclipseLinkRoot<E>) root).runPostponedFetches(query);
		}

		setSuppliedParameters(query, parameters);
		return query;
	}

	/**
	 * @deprecated Renamed to {@link #list()} for consistency.
	 * @see #list()
	 */
	@Deprecated
	public List<E> getAll() {
		return list();
	}

	/**
	 * @deprecated Renamed to {@link #list(boolean)} for consistency.
	 * @see #list(boolean)
	 */
	@Deprecated
	protected List<E> getAll(boolean includeSoftDeleted) {
		return list(includeSoftDeleted);
	}

	/**
	 * @deprecated Renamed to {@link #listSoftDeleted()} for consistency.
	 * @see #listSoftDeleted()
	 */
	@Deprecated
	public List<E> getAllSoftDeleted() {
		return listSoftDeleted();
	}


	// Insert actions -------------------------------------------------------------------------------------------------

	/**
	 * Persist given entity. Any bean validation constraint violation will be logged separately.
	 * @param entity Entity to persist.
	 * @return Entity ID.
	 * @throws IllegalEntityStateException When entity is already persisted or its ID is not generated.
	 */
	public I persist(E entity) {
		return persist(entity, true);
	}

	private I persist(E entity, boolean checkExists) {
		if (entity.getId() != null) {
			if (generatedId || (checkExists && exists(entity))) {
				throw new IllegalEntityStateException(entity, "Entity is already persisted. Use update() instead.");
			}
		}
		else if (!generatedId) {
			throw new IllegalEntityStateException(entity, "Entity has no generated ID. You need to manually set it.");
		}

		try {
			getEntityManager().persist(entity);

		}
		catch (ConstraintViolationException e) {
			logConstraintViolations(e.getConstraintViolations());
			throw e;
		}

		// Entity is not guaranteed to have been given an Id before either the
		// TX commits or flush is called.
		getEntityManager().flush();

		return entity.getId();
	}

	/**
	 * Persist given entity via {@link #persist(BaseEntity)} and immediately perform a flush so that all changes in
	 * managed entities so far in the current transaction are persisted. This is particularly useful when you intend
	 * to process the given entity further in an asynchronous service method.
	 * @param entity Entity to persist.
	 * @return Entity ID.
	 * @throws IllegalEntityStateException When entity is already persisted or its ID is not generated.
	 */
	protected I persistAndFlush(E entity) {
		I id = persist(entity);
		getEntityManager().flush();
		return id;
	}


	// Update actions -------------------------------------------------------------------------------------------------

	/**
	 * Update given entity. If <code>javax.persistence.validation.mode</code> property in <code>persistence.xml</code> is explicitly set
	 * to <code>CALLBACK</code> (and thus not to its default of <code>AUTO</code>), then any bean validation constraint violation will be
	 * logged separately. Due to technical limitations, this effectively means that bean validation is invoked twice. First in this method
	 * in order to be able to obtain the constraint violations and then once more while JTA is committing the transaction, but is executed
	 * beyond the scope of this method.
	 * @param entity Entity to update.
	 * @return Updated entity.
	 * @throws IllegalEntityStateException When entity is not persisted or its ID is not generated.
	 */
	public E update(E entity) {
		return update(entity, true);
	}

	private E update(E entity, boolean checkExists) {
		if (entity.getId() == null) {
			if (generatedId) {
				throw new IllegalEntityStateException(entity, "Entity is not persisted. Use persist() instead.");
			}
			else {
				throw new IllegalEntityStateException(entity, "Entity has no generated ID. You need to manually set it.");
			}
		}

		if (checkExists && !exists(entity)) {
			throw new IllegalEntityStateException(entity, "Entity is not persisted. Use persist() instead.");
		}

		if (validator != null) {
			// EntityManager#merge() doesn't directly throw ConstraintViolationException without performing flush, so we can't put it in a
			// try-catch, and we can't even use an @Interceptor as it happens in JTA side not in EJB side. Hence, we're manually performing
			// bean validation here so that we can capture them.
			logConstraintViolations(validator.validate(entity));
		}

		return getEntityManager().merge(entity);
	}

	/**
	 * Update given entity via {@link #update(BaseEntity)} and immediately perform a flush so that all changes in
	 * managed entities so far in the current transaction are persisted. This is particularly useful when you intend
	 * to process the given entity further in an asynchronous service method.
	 * @param entity Entity to update.
	 * @return Updated entity.
	 * @throws IllegalEntityStateException When entity is not persisted or its ID is not generated.
	 */
	protected E updateAndFlush(E entity) {
		E updatedEntity = update(entity);
		getEntityManager().flush();
		return updatedEntity;
	}

	private void logConstraintViolations(Set<? extends ConstraintViolation<?>> constraintViolations) {
		constraintViolations.forEach(violation -> {
			String constraintName = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
			String beanName = violation.getRootBeanClass().getSimpleName();
			String propertyName = violation.getPropertyPath().toString();
			String violationMessage = violation.getMessage();
			Object beanInstance = violation.getRootBean();
			logger.severe(format(LOG_SEVERE_CONSTRAINT_VIOLATION, constraintName, beanName, propertyName, violationMessage, beanInstance));
		});
	}

	/**
	 * Update given entities.
	 * @param entities Entities to update.
	 * @return Updated entities.
	 * @throws IllegalEntityStateException When at least one entity has no ID.
	 */
	public List<E> update(Iterable<E> entities) {
		return stream(entities).map(this::update).collect(toList());
	}

	/**
	 * Update or delete all entities matching the given query and positional parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * int affectedRows = update("UPDATE Foo f SET f.bar = ?1 WHERE f.baz = ?2", bar, baz);
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters The positional query parameters, if any.
	 * @return The number of entities updated or deleted.
	 * @see Query#executeUpdate()
	 */
	protected int update(String jpql, Object... parameters) {
		return createQuery(jpql, parameters).executeUpdate();
	}

	/**
	 * Update or delete all entities matching the given query and mapped parameters, if any.
	 * <p>
	 * Usage example:
	 * <pre>
	 * int affectedRows = update("UPDATE Foo f SET f.bar = :bar WHERE f.baz = :baz", params -&gt; {
	 *     params.put("bar", bar);
	 *     params.put("baz", baz);
	 * });
	 * </pre>
	 * @param jpql The Java Persistence Query Language statement.
	 * @param parameters To put the mapped query parameters in, if any.
	 * @return The number of entities updated or deleted.
	 * @see Query#executeUpdate()
	 */
	protected int update(String jpql, Consumer<Map<String, Object>> parameters) {
		return createQuery(jpql, parameters).executeUpdate();
	}

	/**
	 * Save given entity. This will automatically determine based on the presence of generated entity ID,
	 * or existence of an entity in the data store whether to {@link #persist(BaseEntity)} or to {@link #update(BaseEntity)}.
	 * @param entity Entity to save.
	 * @return Saved entity.
	 */
	public E save(E entity) {
		if ((generatedId && entity.getId() == null) || (!generatedId && !exists(entity))) {
			persist(entity, false);
			return entity;
		}
		else {
			E update = update(entity, false);
			return update;
		}
	}

	/**
	 * Save given entity via {@link #save(BaseEntity)} and immediately perform a flush so that all changes in
	 * managed entities so far in the current transaction are persisted. This is particularly useful when you intend
	 * to process the given entity further in an asynchronous service method.
	 * @param entity Entity to save.
	 * @return Saved entity.
	 */
	protected E saveAndFlush(E entity) {
		E savedEntity = save(entity);
		getEntityManager().flush();
		return savedEntity;
	}


	// Delete actions -------------------------------------------------------------------------------------------------

	/**
	 * Delete given entity.
	 * @param entity Entity to delete.
	 * @throws NonDeletableEntityException When entity has {@link NonDeletable} annotation set.
	 * @throws IllegalEntityStateException When entity has no ID.
	 * @throws EntityNotFoundException When entity has in meanwhile been deleted.
	 */
	public void delete(E entity) {
		if (entity.getClass().isAnnotationPresent(NonDeletable.class)) {
			throw new NonDeletableEntityException(entity);
		}

		getEntityManager().remove(manage(entity));
		entity.setId(null);
	}

	/**
	 * Soft delete given entity.
	 * @param entity Entity to soft delete.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 * @throws IllegalEntityStateException When entity has no ID.
	 * @throws EntityNotFoundException When entity has in meanwhile been hard deleted.
	 */
	public void softDelete(E entity) {
		softDeleteData.checkSoftDeletable();
		softDeleteData.setSoftDeleted(manage(entity), true);
	}

	/**
	 * Soft undelete given entity.
	 * @param entity Entity to soft undelete.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 * @throws IllegalEntityStateException When entity has no ID.
	 * @throws EntityNotFoundException When entity has in meanwhile been hard deleted.
	 */
	public void softUndelete(E entity) {
		softDeleteData.checkSoftDeletable();
		softDeleteData.setSoftDeleted(manage(entity), false);
	}

	/**
	 * Delete given entities.
	 * @param entities Entities to delete.
	 * @throws NonDeletableEntityException When at least one entity has {@link NonDeletable} annotation set.
	 * @throws IllegalEntityStateException When at least one entity has no ID.
	 * @throws EntityNotFoundException When at least one entity has in meanwhile been deleted.
	 */
	public void delete(Iterable<E> entities) {
		entities.forEach(this::delete);
	}

	/**
	 * Soft delete given entities.
	 * @param entities Entities to soft delete.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 * @throws IllegalEntityStateException When at least one entity has no ID.
	 * @throws EntityNotFoundException When at least one entity has in meanwhile been hard deleted.
	 */
	public void softDelete(Iterable<E> entities) {
		entities.forEach(this::softDelete);
	}

	/**
	 * Soft undelete given entities.
	 * @param entities Entities to soft undelete.
	 * @throws NonSoftDeletableEntityException When entity doesn't have {@link SoftDeletable} annotation set on any of its fields.
	 * @throws IllegalEntityStateException When at least one entity has no ID.
	 * @throws EntityNotFoundException When at least one entity has in meanwhile been hard deleted.
	 */
	public void softUndelete(Iterable<E> entities) {
		entities.forEach(this::softUndelete);
	}


	// Manage actions -------------------------------------------------------------------------------------------------

	/**
	 * Make given entity managed. NOTE: This will discard any unmanaged changes in the given entity!
	 * This is particularly useful in case you intend to make sure that you have the most recent version at hands.
	 * This method supports proxied entities.
	 * @param entity Entity to manage.
	 * @return The managed entity.
	 * @throws NullPointerException When given entity is <code>null</code>.
	 * @throws IllegalEntityStateException When entity has no ID.
	 * @throws EntityNotFoundException When entity has in meanwhile been deleted.
	 */
	protected E manage(E entity) {
		if (provider.getIdentifier(entity) == null) {
			throw new IllegalEntityStateException(entity, "Entity has no ID.");
		}

		E managed = manageIfNecessary(entity);

		if (managed == null) {
			throw new EntityNotFoundException("Entity has in meanwhile been deleted.");
		}

		return managed;
	}

	/**
	 * Make any given entity managed if necessary. NOTE: This will discard any unmanaged changes in the given entity!
	 * This is particularly useful in case you intend to make sure that you have the most recent version at hands.
	 * This method supports <code>null</code> entities as well as proxied entities.
	 * @param <E> The generic entity type.
	 * @param entity Entity to manage, may be <code>null</code>.
	 * @return The managed entity, or <code>null</code> when <code>null</code> was supplied.
	 * It leniently returns the very same argument if the entity has been deleted in the meanwhile.
	 * @throws IllegalArgumentException When the given entity is actually not an instance of {@link BaseEntity}.
	 */
	@SuppressWarnings({ "hiding", "unchecked" })
	protected <E> E manageIfNecessary(E entity) {
		if (entity == null) {
			return null;
		}

		if (!(entity instanceof BaseEntity)) {
			throw new IllegalArgumentException();
		}

		BaseEntity<I> baseEntity = (BaseEntity<I>) entity;
		I id = provider.getIdentifier(baseEntity);

		if (id == null || (entity.getClass().getAnnotation(Entity.class) != null && getEntityManager().contains(entity))) {
			return entity;
		}

		return coalesce((E) getEntityManager().find(provider.getEntityType(baseEntity), id), entity);
	}

	/**
	 * Reset given entity. This will discard any changes in given entity. The given entity must be unmanaged/detached.
	 * The actual intent of this method is to have the opportunity to completely reset the state of a given entity
	 * which might have been edited in the client, without changing the reference. This is generally useful when the
	 * entity is in turn held in some collection and you'd rather not manually remove and reinsert it in the collection.
	 * This method supports proxied entities.
	 * @param entity Entity to reset.
	 * @throws IllegalEntityStateException When entity is already managed, or has no ID.
	 * @throws EntityNotFoundException When entity has in meanwhile been deleted.
	 */
	public void reset(E entity) {
		if (!provider.isProxy(entity) && getEntityManager().contains(entity)) {
			throw new IllegalEntityStateException(entity, "Only unmanaged entities can be resetted.");
		}

		E managed = manage(entity);
		getMetamodel(entity).getAttributes().forEach(attribute -> map(attribute.getJavaMember(), managed, entity));
		// Note: EntityManager#refresh() is insuitable as it requires a managed entity and thus merge() could unintentionally persist changes before resetting.
	}


	// Count actions --------------------------------------------------------------------------------------------------

	/**
	 * Returns count of all foreign key references to given entity.
	 * This is particularly useful in case you intend to check if the given entity is still referenced elsewhere in database.
	 * @param entity Entity to count all foreign key references for.
	 * @return Count of all foreign key references to given entity.
	 */
	protected long countForeignKeyReferencesTo(E entity) {
		return countForeignKeyReferences(getEntityManager(), entityType, identifierType, manage(entity).getId());
	}


	// Lazy fetching actions ------------------------------------------------------------------------------------------

	/**
	 * Fetch lazy collections of given entity on given getters. If no getters are supplied, then it will fetch every
	 * single {@link PluralAttribute} not of type {@link CollectionType#MAP}.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Foo fooWithBarsAndBazs = fetchLazyCollections(getById(fooId), Foo::getBars, Foo::getBazs);
	 * </pre>
	 * @param entity Entity instance to fetch lazy collections on.
	 * @param getters Getters of those lazy collections.
	 * @return The same entity, useful if you want to continue using it immediately.
	 */
	@SuppressWarnings("unchecked") // Unfortunately, @SafeVarargs cannot be used as it requires a final method.
	protected E fetchLazyCollections(E entity, Function<E, Collection<?>>... getters) {
		return fetchPluralAttributes(entity, type -> type != MAP, getters);
	}

	/**
	 * Fetch lazy collections of given optional entity on given getters. If no getters are supplied, then it will fetch
	 * every single {@link PluralAttribute} not of type {@link CollectionType#MAP}.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; fooWithBarsAndBazs = fetchLazyCollections(findById(fooId), Foo::getBars, Foo::getBazs);
	 * </pre>
	 * @param entity Optional entity instance to fetch lazy collections on.
	 * @param getters Getters of those lazy collections.
	 * @return The same optional entity, useful if you want to continue using it immediately.
	 */
	@SuppressWarnings("unchecked") // Unfortunately, @SafeVarargs cannot be used as it requires a final method.
	protected Optional<E> fetchLazyCollections(Optional<E> entity, Function<E, Collection<?>>... getters) {
		return ofNullable(entity.isPresent() ? fetchLazyCollections(entity.get(), getters) : null);
	}

	/**
	 * Fetch lazy maps of given entity on given getters. If no getters are supplied, then it will fetch every single
	 * {@link PluralAttribute} of type {@link CollectionType#MAP}.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Foo fooWithBarsAndBazs = fetchLazyCollections(getById(fooId), Foo::getBars, Foo::getBazs);
	 * </pre>
	 * @param entity Entity instance to fetch lazy maps on.
	 * @param getters Getters of those lazy collections.
	 * @return The same entity, useful if you want to continue using it immediately.
	 */
	@SuppressWarnings("unchecked") // Unfortunately, @SafeVarargs cannot be used as it requires a final method.
	protected E fetchLazyMaps(E entity, Function<E, Map<?, ?>>... getters) {
		return fetchPluralAttributes(entity, type -> type == MAP, getters);
	}

	/**
	 * Fetch lazy maps of given optional entity on given getters. If no getters are supplied, then it will fetch every
	 * single {@link PluralAttribute} of type {@link CollectionType#MAP}.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * <p>
	 * Usage example:
	 * <pre>
	 * Optional&lt;Foo&gt; fooWithBarsAndBazs = fetchLazyCollections(findById(fooId), Foo::getBars, Foo::getBazs);
	 * </pre>
	 * @param entity Optional entity instance to fetch lazy maps on.
	 * @param getters Getters of those lazy collections.
	 * @return The same optional entity, useful if you want to continue using it immediately.
	 */
	@SuppressWarnings("unchecked") // Unfortunately, @SafeVarargs cannot be used as it requires a final method.
	protected Optional<E> fetchLazyMaps(Optional<E> entity, Function<E, Map<?, ?>>... getters) {
		return ofNullable(entity.isPresent() ? fetchLazyMaps(entity.get(), getters) : null);
	}

	/**
	 * Fetch all lazy blobs of given entity.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Entity instance to fetch all blobs on.
	 * @return The same entity, useful if you want to continue using it immediately.
	 */
	protected E fetchLazyBlobs(E entity) {
		return fetchSingularAttributes(entity, type -> type == byte[].class);
	}

	/**
	 * Fetch all lazy blobs of given optional entity.
	 * Note that the implementation does for simplicitly not check if those are actually lazy or eager.
	 * @param entity Optional entity instance to fetch all blobs on.
	 * @return The same optional entity, useful if you want to continue using it immediately.
	 */
	protected Optional<E> fetchLazyBlobs(Optional<E> entity) {
		return ofNullable(entity.isPresent() ? fetchLazyBlobs(entity.get()) : null);
	}

	@SuppressWarnings("unchecked") // Unfortunately, @SafeVarargs cannot be used as it requires a final method.
	private E fetchPluralAttributes(E entity, java.util.function.Predicate<CollectionType> ofType, Function<E, ?>... getters) {
		if (isEmpty(getters)) {
			for (PluralAttribute<?, ?, ?> a : getMetamodel().getPluralAttributes()) {
				if (ofType.test(a.getCollectionType())) {
					ofNullable(invokeGetter(entity, a.getName())).ifPresent(c -> invokeMethod(c, "size"));
				}
			}
		}
		else {
			stream(getters).forEach(getter -> ofNullable(getter.apply(entity)).ifPresent(c -> invokeMethod(c, "size")));
		}

		return entity;
	}

	private E fetchSingularAttributes(E entity, java.util.function.Predicate<Class<?>> ofType) {
		E managed = getById(entity.getId());

		for (Attribute<?, ?> a : getMetamodel().getSingularAttributes()) {
			if (ofType.test(a.getJavaType())) {
				String name = capitalize(a.getName());
				invokeSetter(entity, name, invokeGetter(managed, name));
			}
		}

		return entity;
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
	 *             query.where(criteriaBuilder.equal(root.get("type"), Type.FOO));
	 *         });
	 *     }
	 *
	 * }
	 * </pre>
	 * @param <E> The generic base entity type.
	 */
	@FunctionalInterface
	protected interface QueryBuilder<E> {
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
	protected interface MappedQueryBuilder<T> {
		LinkedHashMap<Getter<T>, Expression<?>> build(CriteriaBuilder criteriaBuilder, AbstractQuery<T> query, Root<? super T> root);
	}

	/**
	 * Here you can in your {@link BaseEntityService} subclass define the callback method which needs to be invoked before any of
	 * {@link #getPage(Page, boolean)} methods is called. For example, to set a vendor specific {@link EntityManager} hint.
	 * The default implementation returns a no-op callback.
	 * @return The callback method which is invoked before any of {@link #getPage(Page, boolean)} methods is called.
	 */
	protected Consumer<EntityManager> beforePage() {
		return entityManager -> noop();
	}

	/**
	 * Here you can in your {@link BaseEntityService} subclass define the callback method which needs to be invoked when any query involved in
	 * {@link #getPage(Page, boolean)} is about to be executed. For example, to set a vendor specific {@link Query} hint.
	 * The default implementation sets Hibernate, EclipseLink and JPA 2.0 cache-related hints. When <code>cacheable</code> argument is
	 * <code>true</code>, then it reads from cache where applicable, else it will read from DB and force a refresh of cache. Note that
	 * this is not supported by OpenJPA.
	 * @param <T> The generic type of the entity or a DTO subclass thereof.
	 * @param resultType The result type which can be the entity type itself or a DTO subclass thereof.
	 * @param cacheable Whether the results should be cacheable.
	 * @return The callback method which is invoked when any query involved in {@link #getPage(Page, boolean)} is about
	 * to be executed.
	 */
	protected <T extends E> Consumer<TypedQuery<?>> onPage(Class<T> resultType, boolean cacheable) {
		return typedQuery -> {
			if (provider == HIBERNATE) {
				typedQuery
					.setHint(QUERY_HINT_HIBERNATE_CACHEABLE, cacheable);
			}
			else if (provider == ECLIPSELINK) {
				typedQuery
					.setHint(QUERY_HINT_ECLIPSELINK_MAINTAIN_CACHE, cacheable)
					.setHint(QUERY_HINT_ECLIPSELINK_REFRESH, !cacheable);
			}

			if (provider != OPENJPA) {
				// OpenJPA doesn't support 2nd level cache.
				typedQuery
					.setHint(QUERY_HINT_CACHE_STORE_MODE, cacheable ? CacheStoreMode.USE : CacheStoreMode.REFRESH)
					.setHint(QUERY_HINT_CACHE_RETRIEVE_MODE, cacheable ? CacheRetrieveMode.USE : CacheRetrieveMode.BYPASS);
			}
		};
	}

	/**
	 * Here you can in your {@link BaseEntityService} subclass define the callback method which needs to be invoked after any of
	 * {@link #getPage(Page, boolean)} methods is called. For example, to remove a vendor specific {@link EntityManager} hint.
	 * The default implementation returns a no-op callback.
	 * @return The callback method which is invoked after any of {@link #getPage(Page, boolean)} methods is called.
	 */
	protected Consumer<EntityManager> afterPage() {
		return entityManager -> noop();
	}

	/**
	 * Returns a partial result list based on given {@link Page}. This will by default cache the results.
	 * <p>
	 * Usage examples:
	 * <pre>
	 * Page first10Records = Page.of(0, 10);
	 * PartialResultList&lt;Foo&gt; foos = getPage(first10Records, true);
	 * </pre>
	 * <pre>
	 * Map&lt;String, Object&gt; criteria = new HashMap&lt;&gt;();
	 * criteria.put("bar", bar); // Exact match.
	 * criteria.put("baz", IgnoreCase.value(baz)); // Case insensitive match.
	 * criteria.put("faz", Like.contains(faz)); // Case insensitive LIKE match.
	 * criteria.put("kaz", Order.greaterThan(kaz)); // Greater than match.
	 * Page first10RecordsMatchingCriteriaOrderedByBar = Page.with().allMatch(criteria).orderBy("bar", true).range(0, 10);
	 * PartialResultList&lt;Foo&gt; foos = getPage(first10RecordsMatchingCriteriaOrderedByBar, true);
	 * </pre>
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @return A partial result list based on given {@link Page}.
	 * @see Page
	 * @see Criteria
	 */
	public PartialResultList<E> getPage(Page page, boolean count) {
		// Implementation notice: we can't remove this getPage() method and rely on the other getPage() method with varargs below,
		// because the one with varargs is incompatible as method reference for getPage(Page, boolean) in some Java versions.
		// See https://github.com/omnifaces/omnipersistence/issues/11
		return getPage(page, count, true, entityType, (builder, query, root) -> noop());
	}

	/**
	 * Returns a partial result list based on given {@link Page} and fetch fields. This will by default cache the results.
	 * <p>
	 * Usage examples:
	 * <pre>
	 * Page first10Records = Page.of(0, 10);
	 * PartialResultList&lt;Foo&gt; foosWithBars = getPage(first10Records, true, "bar");
	 * </pre>
	 * <pre>
	 * Map&lt;String, Object&gt; criteria = new HashMap&lt;&gt;();
	 * criteria.put("bar", bar); // Exact match.
	 * criteria.put("baz", IgnoreCase.value(baz)); // Case insensitive match.
	 * criteria.put("faz", Like.contains(faz)); // Case insensitive LIKE match.
	 * criteria.put("kaz", Order.greaterThan(kaz)); // Greater than match.
	 * Page first10RecordsMatchingCriteriaOrderedByBar = Page.with().allMatch(criteria).orderBy("bar", true).range(0, 10);
	 * PartialResultList&lt;Foo&gt; foosWithBars = getPage(first10RecordsMatchingCriteriaOrderedByBar, true, "bar");
	 * </pre>
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param fetchFields Optionally, all (lazy loaded) fields to be explicitly fetched during the query. Each field
	 * can represent a JavaBean path, like as you would do in EL, such as <code>parent.child.subchild</code>.
	 * @return A partial result list based on given {@link Page}.
	 * @see Page
	 * @see Criteria
	 */
	protected PartialResultList<E> getPage(Page page, boolean count, String... fetchFields) {
		return getPage(page, count, true, fetchFields);
	}

	/**
	 * Returns a partial result list based on given {@link Page} with the option whether to cache the results or not.
	 * <p>
	 * Usage example: see {@link #getPage(Page, boolean)} and {@link #getPage(Page, boolean, String...)}.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param cacheable Whether the results should be cacheable.
	 * @param fetchFields Optionally, all (lazy loaded) fields to be explicitly fetched during the query. Each field
	 * can represent a JavaBean path, like as you would do in EL, such as <code>parent.child.subchild</code>.
	 * @return A partial result list based on given {@link Page}.
	 * @see Page
	 * @see Criteria
	 */
	protected PartialResultList<E> getPage(Page page, boolean count, boolean cacheable, String... fetchFields) {
		return getPage(page, count, cacheable, entityType, (builder, query, root) -> {
			for (String fetchField : fetchFields) {
				FetchParent<?, ?> fetchParent = root;

				for (String attribute : fetchField.split("\\.")) {
					fetchParent = fetchParent.fetch(attribute);
				}
			}

			return null;
		});
	}

	/**
	 * Returns a partial result list based on given {@link Page} and {@link QueryBuilder}. This will by default cache
	 * the results.
	 * <p>
	 * Usage example: see {@link QueryBuilder}.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param queryBuilder This allows fine-graining the JPA criteria query.
	 * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
	 * @see Page
	 * @see Criteria
	 */
	protected PartialResultList<E> getPage(Page page, boolean count, QueryBuilder<E> queryBuilder) {
		return getPage(page, count, true, queryBuilder);
	}

	/**
	 * Returns a partial result list based on given {@link Page}, entity type and {@link QueryBuilder} with the option
	 * whether to cache the results or not.
	 * <p>
	 * Usage example: see {@link QueryBuilder}.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param cacheable Whether the results should be cacheable.
	 * @param queryBuilder This allows fine-graining the JPA criteria query.
	 * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
	 * @see Page
	 * @see Criteria
	 */
	@SuppressWarnings("unchecked")
	protected PartialResultList<E> getPage(Page page, boolean count, boolean cacheable, QueryBuilder<E> queryBuilder) {
		return getPage(page, count, cacheable, entityType, (builder, query, root) -> {
			queryBuilder.build(builder, query, (Root<E>) root);
			return new LinkedHashMap<>(0);
		});
	}

	/**
	 * Returns a partial result list based on given {@link Page}, result type and {@link MappedQueryBuilder}. This will
	 * by default cache the results.
	 * <p>
	 * Usage example: see {@link MappedQueryBuilder}.
	 * @param <T> The generic type of the entity or a DTO subclass thereof.
	 * @param page The page to return a partial result list for.
	 * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results. This will be
	 * available by {@link PartialResultList#getEstimatedTotalNumberOfResults()}.
	 * @param resultType The result type which can be the entity type itself or a DTO subclass thereof.
	 * @param mappedQueryBuilder This allows fine-graining the JPA criteria query and must return a mapping of
	 * getters-paths.
	 * @return A partial result list based on given {@link Page} and {@link MappedQueryBuilder}.
	 * @throws IllegalArgumentException When the result type does not equal entity type and mapping is empty.
	 * @see Page
	 * @see Criteria
	 */
	protected <T extends E> PartialResultList<T> getPage(Page page, boolean count, Class<T> resultType, MappedQueryBuilder<T> mappedQueryBuilder) {
		return getPage(page, count, true, resultType, mappedQueryBuilder);
	}

	/**
	 * Returns a partial result list based on given {@link Page}, entity type and {@link QueryBuilder} with the option
	 * whether to cache the results or not.
	 * <p>
	 * Usage example: see {@link MappedQueryBuilder}.
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
	 * @see Page
	 * @see Criteria
	 */
	protected <T extends E> PartialResultList<T> getPage(Page page, boolean count, boolean cacheable, Class<T> resultType, MappedQueryBuilder<T> queryBuilder) {
		beforePage().accept(getEntityManager());

		try {
			logger.log(FINER, () -> format(LOG_FINER_GET_PAGE, page, count, cacheable, resultType));
			PageBuilder<T> pageBuilder = new PageBuilder<>(page, cacheable, resultType, queryBuilder);
			CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
			TypedQuery<T> entityQuery = buildEntityQuery(pageBuilder, criteriaBuilder);
			TypedQuery<Long> countQuery = count ? buildCountQuery(pageBuilder, criteriaBuilder) : null;
			return executeQuery(pageBuilder, entityQuery, countQuery);
		}
		finally {
			afterPage().accept(getEntityManager());
		}
	}


	// Query actions --------------------------------------------------------------------------------------------------

	private <T extends E> TypedQuery<T> buildEntityQuery(PageBuilder<T> pageBuilder, CriteriaBuilder criteriaBuilder) {
		CriteriaQuery<T> entityQuery = criteriaBuilder.createQuery(pageBuilder.getResultType());
		Root<E> entityQueryRoot = buildRoot(entityQuery);
		PathResolver pathResolver = buildSelection(pageBuilder, entityQuery, entityQueryRoot, criteriaBuilder);
		buildOrderBy(pageBuilder, entityQuery, criteriaBuilder, pathResolver);
		Map<String, Object> parameters = buildRestrictions(pageBuilder, entityQuery, criteriaBuilder, pathResolver);
		return buildTypedQuery(pageBuilder, entityQuery, entityQueryRoot, parameters);
	}

	private <T extends E> TypedQuery<Long> buildCountQuery(PageBuilder<T> pageBuilder, CriteriaBuilder criteriaBuilder) {
		CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
		Root<E> countQueryRoot = countQuery.from(entityType);
		countQuery.select(criteriaBuilder.count(countQueryRoot));
		Map<String, Object> parameters = pageBuilder.shouldBuildCountSubquery() ? buildCountSubquery(pageBuilder, countQuery, countQueryRoot, criteriaBuilder) : emptyMap();
		return buildTypedQuery(pageBuilder, countQuery, null, parameters);
	}

	private <T extends E> Map<String, Object> buildCountSubquery(PageBuilder<T> pageBuilder, CriteriaQuery<Long> countQuery, Root<E> countRoot, CriteriaBuilder criteriaBuilder) {
		Subquery<T> countSubquery = countQuery.subquery(pageBuilder.getResultType());
		Root<E> countSubqueryRoot = buildRoot(countSubquery);
		PathResolver subqueryPathResolver = buildSelection(pageBuilder, countSubquery, countSubqueryRoot, criteriaBuilder);
		Map<String, Object> parameters = buildRestrictions(pageBuilder, countSubquery, criteriaBuilder, subqueryPathResolver);

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

		return parameters;
	}

	private <T extends E, Q> TypedQuery<Q> buildTypedQuery(PageBuilder<T> pageBuilder, CriteriaQuery<Q> criteriaQuery, Root<E> root, Map<String, Object> parameters) {
		TypedQuery<Q> typedQuery = getEntityManager().createQuery(criteriaQuery);
		buildRange(pageBuilder, typedQuery, root);
		setMappedParameters(typedQuery, parameters);
		onPage(pageBuilder.getResultType(), pageBuilder.isCacheable()).accept(typedQuery);
		return typedQuery;
	}

	private <Q> void setPositionalParameters(TypedQuery<Q> typedQuery, Object[] positionalParameters) {
		logger.log(FINER, () -> format(LOG_FINER_SET_PARAMETER_VALUES, Arrays.toString(positionalParameters)));
		range(0, positionalParameters.length).forEach(i -> typedQuery.setParameter(i, positionalParameters[i]));
	}

	private <Q> void setMappedParameters(TypedQuery<Q> typedQuery, Map<String, Object> mappedParameters) {
		logger.log(FINER, () -> format(LOG_FINER_SET_PARAMETER_VALUES, mappedParameters));
		mappedParameters.entrySet().forEach(parameter -> typedQuery.setParameter(parameter.getKey(), parameter.getValue()));
	}

	private <Q> void setSuppliedParameters(TypedQuery<Q> typedQuery, Consumer<Map<String, Object>> suppliedParameters) {
		Map<String, Object> mappedParameters = new HashMap<>();
		suppliedParameters.accept(mappedParameters);
		setMappedParameters(typedQuery, mappedParameters);
	}

	private <T extends E> PartialResultList<T> executeQuery(PageBuilder<T> pageBuilder, TypedQuery<T> entityQuery, TypedQuery<Long> countQuery) {
		Page page = pageBuilder.getPage();
		List<T> entities = entityQuery.getResultList();

		if (pageBuilder.canBuildValueBasedPagingPredicate() && page.isReversed()) {
			reverse(entities);
		}

		int estimatedTotalNumberOfResults = (countQuery != null) ? countQuery.getSingleResult().intValue() : -1;
		logger.log(FINER, () -> format(LOG_FINER_QUERY_RESULT, entities, estimatedTotalNumberOfResults));
		return new PartialResultList<>(entities, page.getOffset(), estimatedTotalNumberOfResults);
	}


	// Selection actions ----------------------------------------------------------------------------------------------

	private <T extends E> Root<E> buildRoot(AbstractQuery<T> query) {
		Root<E> root = query.from(entityType);
		return (query instanceof Subquery) ? new SubqueryRoot<>(root) : (provider == ECLIPSELINK) ? new EclipseLinkRoot<>(root) : root;
	}

	private <T extends E> PathResolver buildSelection(PageBuilder<T> pageBuilder, AbstractQuery<T> query, Root<E> root, CriteriaBuilder criteriaBuilder) {
		Map<Getter<T>, Expression<?>> mapping = pageBuilder.getQueryBuilder().build(criteriaBuilder, query, root);

		if (query instanceof Subquery) {
			((Subquery<T>) query).select(root.get(ID));
		}

		if (!isEmpty(mapping)) { // mapping is not empty when getPage(..., MappedQueryBuilder) is used.
			Map<String, Expression<?>> paths = stream(mapping).collect(toMap(e -> e.getKey().getPropertyName(), e -> e.getValue(), (l, r) -> l, LinkedHashMap::new));

			if (query instanceof CriteriaQuery) {
				((CriteriaQuery<T>) query).multiselect(stream(paths).map(Alias::as).collect(toList()));
			}

			Set<String> aggregatedFields = paths.entrySet().stream().filter(e -> provider.isAggregation(e.getValue())).map(Entry::getKey).collect(toSet());

			if (!aggregatedFields.isEmpty()) {
				groupByIfNecessary(query, root);
			}

			boolean orderingContainsAggregatedFields = aggregatedFields.removeAll(pageBuilder.getPage().getOrdering().keySet());
			pageBuilder.shouldBuildCountSubquery(true); // Normally, building of count subquery is skipped for performance, but when there's a custom mapping, we cannot reliably determine if custom criteria is used, so count subquery building cannot be reliably skipped.
			pageBuilder.canBuildValueBasedPagingPredicate(provider != HIBERNATE || !orderingContainsAggregatedFields); // Value based paging cannot be used in Hibernate if ordering contains aggregated fields, because Hibernate may return a cartesian product and apply firstResult/maxResults in memory.
			return new MappedPathResolver(root, paths, ELEMENT_COLLECTION_MAPPINGS.get(entityType), MANY_OR_ONE_TO_ONE_MAPPINGS.get(entityType));
		}
		else if (pageBuilder.getResultType() == entityType) {
			pageBuilder.shouldBuildCountSubquery(mapping != null); // mapping is empty but not null when getPage(..., QueryBuilder) is used.
			pageBuilder.canBuildValueBasedPagingPredicate(mapping == null); // when mapping is not null, we cannot reliably determine if ordering contains aggregated fields, so value based paging cannot be reliably used.
			return new RootPathResolver(root, ELEMENT_COLLECTION_MAPPINGS.get(entityType), MANY_OR_ONE_TO_ONE_MAPPINGS.get(entityType));
		}
		else {
			throw new IllegalArgumentException(ERROR_ILLEGAL_MAPPING);
		}
	}

	private <T extends E> void buildRange(PageBuilder<T> pageBuilder, Query query, Root<E> root) {
		if (root == null) {
			return;
		}

		boolean hasJoins = hasJoins(root);
		Page page = pageBuilder.getPage();

		if ((hasJoins || page.getOffset() > 0) && !pageBuilder.canBuildValueBasedPagingPredicate()) {
			query.setFirstResult(page.getOffset());
		}

		if (hasJoins || page.getLimit() != MAX_VALUE) {
			query.setMaxResults(page.getLimit());
		}

		if (hasJoins && root instanceof EclipseLinkRoot) {
			((EclipseLinkRoot<E>) root).runPostponedFetches(query);
		}
	}


	// Sorting actions ------------------------------------------------------------------------------------------------

	private <T extends E> void buildOrderBy(PageBuilder<T> pageBuilder, CriteriaQuery<T> criteriaQuery, CriteriaBuilder criteriaBuilder, PathResolver pathResolver) {
		Page page = pageBuilder.getPage();
		Map<String, Boolean> ordering = page.getOrdering();

		if (ordering.isEmpty() || page.getLimit() - page.getOffset() == 1) {
			return;
		}

		boolean reversed = pageBuilder.canBuildValueBasedPagingPredicate() && page.isReversed();
		criteriaQuery.orderBy(stream(ordering).map(order -> buildOrder(order, criteriaBuilder, pathResolver, reversed)).collect(toList()));
	}

	private Order buildOrder(Entry<String, Boolean> order, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, boolean reversed) {
		String field = order.getKey();

		if (oneToManys.test(field) || elementCollections.contains(field)) {
			if (provider == ECLIPSELINK) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_ECLIPSELINK); // EclipseLink refuses to perform a JOIN when setFirstResult/setMaxResults is used.
			}
			else if (provider == OPENJPA) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_ORDERBY_OPENJPA); // OpenJPA adds for some reason a second JOIN on the join table referenced in ORDER BY column causing it to be not sorted on the intended join.
			}
		}

		Expression<?> path = pathResolver.get(field);
		return (order.getValue() ^ reversed) ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path);
	}


	// Searching actions -----------------------------------------------------------------------------------------------

	private <T extends E> Map<String, Object> buildRestrictions(PageBuilder<T> pageBuilder, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver) {
		Page page = pageBuilder.getPage();
		Map<String, Object> parameters = new HashMap<>(page.getRequiredCriteria().size() + page.getOptionalCriteria().size());
		List<Predicate> requiredPredicates = buildPredicates(page.getRequiredCriteria(), query, criteriaBuilder, pathResolver, parameters);
		List<Predicate> optionalPredicates = buildPredicates(page.getOptionalCriteria(), query, criteriaBuilder, pathResolver, parameters);
		Predicate restriction = null;

		if (!optionalPredicates.isEmpty()) {
			pageBuilder.shouldBuildCountSubquery(true);
			restriction = criteriaBuilder.or(toArray(optionalPredicates));
		}

		if (!requiredPredicates.isEmpty()) {
			pageBuilder.shouldBuildCountSubquery(true);
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

		if (!(query instanceof Subquery) && pageBuilder.canBuildValueBasedPagingPredicate()) {
			restriction = conjunctRestrictionsIfNecessary(criteriaBuilder, restriction, buildValueBasedPagingPredicate(page, criteriaBuilder, pathResolver, parameters));
		}

		if (restriction != null) {
			boolean distinct = !optionalPredicates.isEmpty() || hasFetches((From<?, ?>) pathResolver.get(null));
			query.distinct(distinct).where(conjunctRestrictionsIfNecessary(criteriaBuilder, query.getRestriction(), restriction));
		}

		return parameters;
	}

	@SuppressWarnings("unchecked")
	private <T extends E, V extends Comparable<V>> Predicate buildValueBasedPagingPredicate(Page page, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameters) {
		// Value based paging https://blog.novatec-gmbh.de/art-pagination-offset-vs-value-based-paging/ is on large offsets much faster than offset based paging.
		// (orderByField1 > ?1) OR (orderByField1 = ?1 AND orderByField2 > ?2) OR (orderByField1 = ?1 AND orderByField2 = ?2 AND orderByField3 > ?3) [...]

		List<Predicate> predicates = new ArrayList<>(page.getOrdering().size());
		Map<Expression<V>, ParameterExpression<V>> orderByFields = new HashMap<>();
		T last = (T) page.getLast();

		for (Entry<String, Boolean> order : page.getOrdering().entrySet()) {
			String field = order.getKey();
			V value = invokeGetter(last, field);
			Expression<V> path = (Expression<V>) pathResolver.get(field);
			ParameterExpression<V> parameter = new UncheckedParameterBuilder(field, criteriaBuilder, parameters).create(value);
			Predicate predicate = order.getValue() ^ page.isReversed() ? criteriaBuilder.greaterThan(path, parameter) : criteriaBuilder.lessThan(path, parameter);

			for (Entry<Expression<V>, ParameterExpression<V>> previousOrderByField : orderByFields.entrySet()) {
				predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(previousOrderByField.getKey(), previousOrderByField.getValue()));
			}

			orderByFields.put(path, parameter);
			predicates.add(predicate);
		}

		return criteriaBuilder.or(toArray(predicates));
	}

	private <T extends E> List<Predicate> buildPredicates(Map<String, Object> criteria, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameters) {
		return stream(criteria)
			.map(parameter -> buildPredicate(parameter, query, criteriaBuilder, pathResolver, parameters))
			.filter(Objects::nonNull)
			.collect(toList());
	}

	private <T extends E> Predicate buildPredicate(Entry<String, Object> parameter, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, Map<String, Object> parameters) {
		String field = parameter.getKey();
		Expression<?> path = pathResolver.get(elementCollections.contains(field) ? pathResolver.join(field) : field);
		Class<?> type = ID.equals(field) ? identifierType : path.getJavaType();
		return buildTypedPredicate(path, type, field,  parameter.getValue(), query, criteriaBuilder, pathResolver, new UncheckedParameterBuilder(field, criteriaBuilder, parameters));
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

		try {
			if (value == null || (value instanceof Criteria && ((Criteria<?>) value).getValue() == null)) {
				predicate = criteriaBuilder.isNull(path);
			}
			else if (value instanceof Criteria) {
				predicate = ((Criteria<?>) value).build(path, criteriaBuilder, parameterBuilder);
			}
			else if (elementCollections.contains(field)) {
				predicate = buildElementCollectionPredicate(alias, path, type, field, value, query, criteriaBuilder, pathResolver, parameterBuilder);
			}
			else if (value instanceof Iterable || value.getClass().isArray()) {
				predicate = buildArrayPredicate(path, type, field, value, query, criteriaBuilder, pathResolver, parameterBuilder);
			}
			else if (value instanceof BaseEntity) {
				predicate = criteriaBuilder.equal(path, parameterBuilder.create(value));
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
				throw new UnsupportedOperationException(format(ERROR_UNSUPPORTED_CRITERIA, field, type, value, value.getClass()));
			}
		}
		catch (IllegalArgumentException e) {
			logger.log(WARNING, e, () -> format(LOG_WARNING_ILLEGAL_CRITERIA_VALUE, field, type, criteria, criteria != null ? criteria.getClass() : null));
			return null;
		}

		if (negated) {
			predicate = criteriaBuilder.not(predicate);
		}

		alias.set(predicate);
		return predicate;
	}

	private <T extends E> Predicate buildElementCollectionPredicate(Alias alias, Expression<?> path, Class<?> type, String field, Object value, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, ParameterBuilder parameterBuilder) {
		if (provider == ECLIPSELINK || (provider == HIBERNATE && database == POSTGRESQL)) {
			// EclipseLink refuses to perform GROUP BY on IN clause on @ElementCollection, causing a cartesian product.
			// Hibernate + PostgreSQL bugs on IN clause on @ElementCollection as PostgreSQL strictly requires an additional GROUP BY, but Hibernate didn't set it.
			return buildArrayPredicate(path, type, field, value, query, criteriaBuilder, pathResolver, parameterBuilder);
		}
		else {
			// For other cases, a real IN predicate is more efficient than an array predicate, even though both approaches are supported.
			return buildInPredicate(alias, path, type, value, parameterBuilder);
		}
	}

	private Predicate buildInPredicate(Alias alias, Expression<?> path, Class<?> type, Object value, ParameterBuilder parameterBuilder) {
		List<Expression<?>> in = stream(value)
			.map(item -> createElementCollectionCriteria(type, item).getValue())
			.filter(Objects::nonNull)
			.map(parameterBuilder::create)
			.collect(toList());

		if (in.isEmpty()) {
			throw new IllegalArgumentException(value.toString());
		}

		alias.in(in.size());
		return path.in(in.toArray(new Expression[in.size()]));
	}

	private <T extends E> Predicate buildArrayPredicate(Expression<?> path, Class<?> type, String field, Object value, AbstractQuery<T> query, CriteriaBuilder criteriaBuilder, PathResolver pathResolver, ParameterBuilder parameterBuilder) {
		boolean oneToManyField = oneToManys.test(field);

		if (oneToManyField) {
			if (provider == ECLIPSELINK) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_ECLIPSELINK); // EclipseLink refuses to perform a JOIN when setFirstResult/setMaxResults is used.
			}
			else if (provider == OPENJPA) {
				throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ONETOMANY_CRITERIA_OPENJPA); // OpenJPA bugs on setting parameters in a nested subquery "java.lang.IllegalArgumentException: Parameter named X is not declared in query"
			}
		}

		boolean elementCollectionField = elementCollections.contains(field);
		Subquery<Long> subquery = null;
		Expression<?> fieldPath;

		if (oneToManyField || elementCollectionField) {
			// This subquery must simulate an IN clause on a field of a @OneToMany or @ElementCollection relationship.
			// Otherwise the main query will return ONLY the matching values while the natural expectation in UI is that they are just all returned.
			subquery = query.subquery(Long.class);
			Root<E> subqueryRoot = subquery.from(entityType);
			fieldPath = new RootPathResolver(subqueryRoot, elementCollections, manyOrOneToOnes).get(pathResolver.join(field));
			subquery.select(criteriaBuilder.countDistinct(fieldPath)).where(criteriaBuilder.equal(subqueryRoot.get(ID), pathResolver.get(ID)));
		}
		else {
			fieldPath = path;
		}

		List<Predicate> predicates = stream(value)
			.map(item -> elementCollectionField
					? createElementCollectionCriteria(type, item).build(fieldPath, criteriaBuilder, parameterBuilder)
					: buildTypedPredicate(fieldPath, type, field, item, query, criteriaBuilder, pathResolver, parameterBuilder))
			.filter(Objects::nonNull)
			.collect(toList());

		if (predicates.isEmpty()) {
			throw new IllegalArgumentException(value.toString());
		}

		Predicate predicate = criteriaBuilder.or(toArray(predicates));

		if (subquery != null) {
			// SELECT e FROM E e WHERE (SELECT COUNT(DISTINCT field) FROM T t WHERE [restrictions] AND t.id = e.id) = ACTUALCOUNT
			Long actualCount = (long) predicates.size();
			predicate = criteriaBuilder.equal(subquery.where(criteriaBuilder.and(predicate, subquery.getRestriction())), actualCount);
		}

		return predicate;
	}

	@SuppressWarnings("unchecked")
	private Criteria<?> createElementCollectionCriteria(Class<?> type, Object value) {
		return type.isEnum() ? Enumerated.parse(value, (Class<Enum<?>>) type) : IgnoreCase.value(value.toString());
	}


	// Helpers --------------------------------------------------------------------------------------------------------

	/**
	 * Returns the currently active {@link BaseEntityService} from the {@link SessionContext}.
	 * @return The currently active {@link BaseEntityService} from the {@link SessionContext}.
	 * @throws IllegalStateException if there is none, which can happen if this method is called outside EJB context,
	 * or when currently invoked EJB service is not an instance of {@link BaseEntityService}.
	 */
	@SuppressWarnings("unchecked")
	public static BaseEntityService<?, ?> getCurrentInstance() {
		try {
			SessionContext ejbContext = (SessionContext) new InitialContext().lookup("java:comp/EJBContext");
			return (BaseEntityService<?, ?>) ejbContext.getBusinessObject(ejbContext.getInvokedBusinessInterface());
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
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
			|| (from instanceof EclipseLinkRoot && ((EclipseLinkRoot<?>) from).hasPostponedFetches());
	}

	private static <T> T noop() {
		return null;
	}

}
