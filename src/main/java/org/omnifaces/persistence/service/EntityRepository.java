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

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.EntityType;
import jakarta.transaction.Transactional;

import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService.CriteriaQueryBuilder;
import org.omnifaces.persistence.service.BaseEntityService.MappedQueryBuilder;
import org.omnifaces.persistence.service.BaseEntityService.QueryBuilder;
import org.omnifaces.utils.collection.PartialResultList;

/**
 * <p>
 * Entity repository. This offers the persistence actions of {@link BaseEntityService} for a given entity type without the need to extend from it. Inject it
 * with concrete type arguments in any CDI or EJB managed bean:
 *
 * <pre>
 *
 * &#64;Inject
 * private EntityRepository&lt;Long, Person&gt; personRepository;
 * </pre>
 * <p>
 * The injected instance uses the entity manager of the default persistence unit. For another persistence unit, construct it yourself with a supplier of the
 * desired entity manager, for example in your own CDI producer. The supplier must not capture any non-serializable state, such as the entity manager itself, so
 * let it look up the entity manager on each invocation instead:
 *
 * <pre>
 * new EntityRepository&lt;&gt;(() -&gt; CDI.current().select(YourEntityManagerHolder.class).get().getEntityManager(), Long.class, Person.class);
 * </pre>
 * <p>
 * Each method behaves exactly like the {@link BaseEntityService} method of the same signature. The entity manager supplier is only invoked once a persistence
 * action is invoked, and the instance is {@link Serializable}, so it can safely be held by a view or session scoped bean.
 * <p>
 * This repository does not manage transactions: any {@link Transactional} annotation of {@link BaseEntityService} does not apply here. Modifying actions such
 * as {@link #persist(Object)}, {@link #update(Object)} and {@link #delete(Object)} must therefore be invoked within a transaction of the calling bean, such as
 * a {@link Transactional} CDI bean or a stateless EJB.
 * <p>
 * This is a concrete class, not a Jakarta Data repository interface which the container implements.
 *
 * @author Bauke Scholtz
 * @param <I> The generic ID type.
 * @param <E> The generic entity type.
 * @see BaseEntityService
 * @since 1.3
 */
public final class EntityRepository<I extends Comparable<I> & Serializable, E> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The supplier of the entity manager, invoked on every action. */
    private final SerializableSupplier<EntityManager> entityManager;

    /** The actual type of the generic ID type. */
    private final Class<I> identifierType;

    /** The actual type of the generic entity type. */
    private final Class<E> entityType;

    private transient volatile BaseEntityService<I, E> service;

    /**
     * A {@link Supplier} which is also {@link Serializable}, so that it survives serialization of the {@link EntityRepository} holding it.
     *
     * @param <T> The generic type of the supplied result.
     * @since 1.3
     */
    @FunctionalInterface
    public interface SerializableSupplier<T> extends Supplier<T>, Serializable {
    }

    /**
     * Creates an entity repository for the given entity type.
     *
     * @param entityManager The supplier of the entity manager to use. It is invoked on every action, never during construction.
     * @param identifierType The actual type of the generic ID type <code>I</code>.
     * @param entityType The actual type of the generic entity type <code>E</code>.
     * @throws NullPointerException When any argument is <code>null</code>.
     * @since 1.3
     */
    public EntityRepository(SerializableSupplier<EntityManager> entityManager, Class<I> identifierType, Class<E> entityType) {
        this.entityManager = requireNonNull(entityManager, "entityManager");
        this.identifierType = requireNonNull(identifierType, "identifierType");
        this.entityType = requireNonNull(entityType, "entityType");
    }

    private BaseEntityService<I, E> service() {
        var current = service;

        if (current == null) {
            current = new EntityService<>(entityManager, identifierType, entityType);
            service = current;
        }

        return current;
    }

    /**
     * The wrapped {@link BaseEntityService}. Every action routes through it so that the behavior is identical to that of a {@link BaseEntityService} subclass,
     * including {@link BaseEntityService#getCurrentBaseEntityService()} wherever {@link BaseEntityService} itself sets it.
     */
    private static final class EntityService<I extends Comparable<I> & Serializable, E> extends BaseEntityService<I, E> {

        private final Supplier<EntityManager> entityManager;

        private EntityService(Supplier<EntityManager> entityManager, Class<I> identifierType, Class<E> entityType) {
            super(identifierType, entityType);
            this.entityManager = entityManager;
            initWithEntityManager();
        }

        @Override
        protected EntityManager getEntityManager() {
            return entityManager.get();
        }

    }

    // Getters --------------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#getProvider()}.
     *
     * @return The Jakarta Persistence provider being used.
     * @since 1.3
     */
    public Provider getProvider() {
        return service().getProvider();
    }

    /**
     * See {@link BaseEntityService#getDatabase()}.
     *
     * @return The SQL database being used.
     * @since 1.3
     */
    public Database getDatabase() {
        return service().getDatabase();
    }

    /**
     * Returns the actual type of the generic ID type <code>I</code>.
     *
     * @return The actual type of the generic ID type <code>I</code>.
     * @since 1.3
     */
    public Class<I> getIdentifierType() {
        return identifierType;
    }

    /**
     * Returns the actual type of the generic entity type <code>E</code>.
     *
     * @return The actual type of the generic entity type <code>E</code>.
     * @since 1.3
     */
    public Class<E> getEntityType() {
        return entityType;
    }

    /**
     * See {@link BaseEntityService#isGeneratedId()}.
     *
     * @return Whether the ID is generated.
     * @since 1.3
     */
    public boolean isGeneratedId() {
        return service().isGeneratedId();
    }

    /**
     * Returns the entity manager being used, as obtained from the supplier given in the constructor.
     *
     * @return The entity manager being used.
     * @since 1.3
     */
    public EntityManager getEntityManager() {
        return entityManager.get();
    }

    /**
     * See {@link BaseEntityService#getMetamodel()}.
     *
     * @return The metamodel of current entity.
     * @since 1.3
     */
    public EntityType<E> getMetamodel() {
        return service().getMetamodel();
    }

    /**
     * See {@link BaseEntityService#getMetamodel(Object)}.
     *
     * @param <T> The generic entity type of the given entity.
     * @param entity Entity to obtain metamodel for.
     * @return The metamodel of given entity.
     * @since 1.3
     */
    public <T> EntityType<T> getMetamodel(T entity) {
        return service().getMetamodel(entity);
    }

    // Preparing actions ----------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#createNamedTypedQuery(String)}.
     *
     * @param name The name of the Java Persistence Query Language statement defined in metadata.
     * @return An instance of {@link TypedQuery} for executing the named Java Persistence Query Language statement.
     * @since 1.3
     */
    public TypedQuery<E> createNamedTypedQuery(String name) {
        return service().createNamedTypedQuery(name);
    }

    /**
     * See {@link BaseEntityService#createNamedQuery(String)}.
     *
     * @param name The name of the Java Persistence Query Language statement defined in metadata.
     * @return An instance of {@link Query} for executing the named Java Persistence Query Language statement.
     * @since 1.3
     */
    public Query createNamedQuery(String name) {
        return service().createNamedQuery(name);
    }

    /**
     * See {@link BaseEntityService#createTypedQuery(String, Class)}.
     *
     * @param <T> The generic result type.
     * @param jpql The Java Persistence Query Language statement.
     * @param resultType The result type.
     * @return An instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement which returns the specified <code>T</code>.
     * @since 1.3
     */
    public <T> TypedQuery<T> createTypedQuery(String jpql, Class<T> resultType) {
        return service().createTypedQuery(jpql, resultType);
    }

    /**
     * See {@link BaseEntityService#createTypedQuery(String)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @return An instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement which returns a <code>E</code>.
     * @since 1.3
     */
    public TypedQuery<E> createTypedQuery(String jpql) {
        return service().createTypedQuery(jpql);
    }

    /**
     * See {@link BaseEntityService#createLongQuery(String)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @return An instance of {@link TypedQuery} for executing the given Java Persistence Query Language statement which returns a <code>Long</code>.
     * @since 1.3
     */
    public TypedQuery<Long> createLongQuery(String jpql) {
        return service().createLongQuery(jpql);
    }

    /**
     * See {@link BaseEntityService#createQuery(String)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @return An instance of {@link Query} for executing the given Java Persistence Query Language statement.
     * @since 1.3
     */
    public Query createQuery(String jpql) {
        return service().createQuery(jpql);
    }

    // Select actions -------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#find(String, Object...)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The positional query parameters, if any.
     * @return Found entity matching the given query and positional parameters, if any.
     * @since 1.3
     */
    public Optional<E> find(String jpql, Object... parameters) {
        return service().find(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#find(String, Map)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The mapped query parameters.
     * @return Found entity matching the given query and mapped parameters, if any.
     * @since 1.3
     */
    public Optional<E> find(String jpql, Map<String, Object> parameters) {
        return service().find(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#find(CriteriaQueryBuilder, Map)}.
     *
     * @param queryBuilder This creates the Jakarta Persistence criteria query.
     * @param parameters The mapped query parameters.
     * @return Found entity matching {@link CriteriaQueryBuilder} and mapped parameters, if any.
     * @since 1.3
     */
    public Optional<E> find(CriteriaQueryBuilder<E> queryBuilder, Map<String, Object> parameters) {
        return service().find(queryBuilder, parameters);
    }

    /**
     * See {@link BaseEntityService#findFirst(String, Object...)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The positional query parameters, if any.
     * @return Found entity matching the given query and positional parameters, if any.
     * @since 1.3
     */
    public Optional<E> findFirst(String jpql, Object... parameters) {
        return service().findFirst(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#findFirst(String, Map)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The mapped query parameters.
     * @return Found entity matching the given query and mapped parameters, if any.
     * @since 1.3
     */
    public Optional<E> findFirst(String jpql, Map<String, Object> parameters) {
        return service().findFirst(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#findFirst(CriteriaQueryBuilder, Map)}.
     *
     * @param queryBuilder This creates the Jakarta Persistence criteria query.
     * @param parameters The mapped query parameters.
     * @return Found entity matching {@link CriteriaQueryBuilder} and mapped parameters, if any.
     * @since 1.3
     */
    public Optional<E> findFirst(CriteriaQueryBuilder<E> queryBuilder, Map<String, Object> parameters) {
        return service().findFirst(queryBuilder, parameters);
    }

    /**
     * See {@link BaseEntityService#findById(Comparable)}.
     *
     * @param id Entity ID to find entity for.
     * @return Found entity, if any.
     * @since 1.3
     */
    public Optional<E> findById(I id) {
        return service().findById(id);
    }

    /**
     * See {@link BaseEntityService#findById(Comparable, boolean)}.
     *
     * @param id Entity ID to find entity for.
     * @param includeSoftDeleted Whether to include soft deleted ones in the search.
     * @return Found entity, if any.
     * @since 1.3
     */
    public Optional<E> findById(I id, boolean includeSoftDeleted) {
        return service().findById(id, includeSoftDeleted);
    }

    /**
     * See {@link BaseEntityService#findSoftDeletedById(Comparable)}.
     *
     * @param id Entity ID to find soft deleted entity for.
     * @return Found soft deleted entity, if any.
     * @since 1.3
     */
    public Optional<E> findSoftDeletedById(I id) {
        return service().findSoftDeletedById(id);
    }

    /**
     * See {@link BaseEntityService#getById(Comparable)}.
     *
     * @param id Entity ID to get entity by.
     * @return Found entity, or <code>null</code> if there is none.
     * @since 1.3
     */
    @Nullable
    public E getById(I id) {
        return service().getById(id);
    }

    /**
     * See {@link BaseEntityService#getById(Comparable, boolean)}.
     *
     * @param id Entity ID to get entity by.
     * @param includeSoftDeleted Whether to include soft deleted ones in the search.
     * @return Found entity, or <code>null</code> if there is none.
     * @since 1.3
     */
    @Nullable
    public E getById(I id, boolean includeSoftDeleted) {
        return service().getById(id, includeSoftDeleted);
    }

    /**
     * See {@link BaseEntityService#getByIdWithLoadGraph(Comparable, String)}.
     *
     * @param id Entity ID to get entity by.
     * @param entityGraphName Entity graph name.
     * @return Found entity, or <code>null</code> if there is none.
     * @since 1.3
     */
    @Nullable
    public E getByIdWithLoadGraph(I id, String entityGraphName) {
        return service().getByIdWithLoadGraph(id, entityGraphName);
    }

    /**
     * See {@link BaseEntityService#getSoftDeletedById(Comparable)}.
     *
     * @param id Entity ID to get soft deleted entity by.
     * @return Found soft deleted entity, or <code>null</code> if there is none.
     * @since 1.3
     */
    @Nullable
    public E getSoftDeletedById(I id) {
        return service().getSoftDeletedById(id);
    }

    /**
     * See {@link BaseEntityService#getByIds(Iterable)}.
     *
     * @param ids Entity IDs to get entities by.
     * @return Found entities, or an empty set if there is none.
     * @since 1.3
     */
    public List<E> getByIds(Iterable<I> ids) {
        return service().getByIds(ids);
    }

    /**
     * See {@link BaseEntityService#getByIds(Iterable, boolean)}.
     *
     * @param ids Entity IDs to get entities by.
     * @param includeSoftDeleted Whether to include soft deleted ones in the search.
     * @return Found entities, optionally including soft deleted ones, or an empty set if there is none.
     * @since 1.3
     */
    public List<E> getByIds(Iterable<I> ids, boolean includeSoftDeleted) {
        return service().getByIds(ids, includeSoftDeleted);
    }

    /**
     * See {@link BaseEntityService#exists(Object)}.
     *
     * @param entity Entity to check.
     * @return Whether entity with given entity exists.
     * @since 1.3
     */
    public boolean exists(E entity) {
        return service().exists(entity);
    }

    /**
     * See {@link BaseEntityService#list()}.
     *
     * @return List of all entities.
     * @since 1.3
     */
    public List<E> list() {
        return service().list();
    }

    /**
     * See {@link BaseEntityService#list(boolean)}.
     *
     * @param includeSoftDeleted Whether to include soft deleted ones in the search.
     * @return List of all entities, optionally including soft deleted ones.
     * @since 1.3
     */
    public List<E> list(boolean includeSoftDeleted) {
        return service().list(includeSoftDeleted);
    }

    /**
     * See {@link BaseEntityService#listSoftDeleted()}.
     *
     * @return List of all soft deleted entities.
     * @since 1.3
     */
    public List<E> listSoftDeleted() {
        return service().listSoftDeleted();
    }

    /**
     * See {@link BaseEntityService#list(String, Object...)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The positional query parameters, if any.
     * @return List of entities matching the given query and positional parameters, if any.
     * @since 1.3
     */
    public List<E> list(String jpql, Object... parameters) {
        return service().list(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#list(String, Map)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The mapped query parameters.
     * @return List of entities matching the given query and mapped parameters, if any.
     * @since 1.3
     */
    public List<E> list(String jpql, Map<String, Object> parameters) {
        return service().list(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#list(CriteriaQueryBuilder, Map)}.
     *
     * @param queryBuilder This creates the Jakarta Persistence criteria query.
     * @param parameters The mapped query parameters.
     * @return List of entities matching the {@link CriteriaQueryBuilder} and mapped parameters, if any.
     * @since 1.3
     */
    public List<E> list(CriteriaQueryBuilder<E> queryBuilder, Map<String, Object> parameters) {
        return service().list(queryBuilder, parameters);
    }

    // Insert actions -------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#persist(Object)}.
     *
     * @param entity Entity to persist.
     * @return Entity ID.
     * @since 1.3
     */
    public I persist(E entity) {
        return service().persist(entity);
    }

    // Update actions -------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#update(Object)}.
     *
     * @param entity Entity to update.
     * @return Updated entity.
     * @since 1.3
     */
    public E update(E entity) {
        return service().update(entity);
    }

    /**
     * See {@link BaseEntityService#updateAndFlush(Object)}.
     *
     * @param entity Entity to update.
     * @return Updated entity.
     * @since 1.3
     */
    public E updateAndFlush(E entity) {
        return service().updateAndFlush(entity);
    }

    /**
     * See {@link BaseEntityService#update(Iterable)}.
     *
     * @param entities Entities to update.
     * @return Updated entities.
     * @since 1.3
     */
    public List<E> update(Iterable<E> entities) {
        return service().update(entities);
    }

    /**
     * See {@link BaseEntityService#update(String, Object...)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The positional query parameters, if any.
     * @return The number of entities updated or deleted.
     * @since 1.3
     */
    public int update(String jpql, Object... parameters) {
        return service().update(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#update(String, Map)}.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The mapped query parameters.
     * @return The number of entities updated or deleted.
     * @since 1.3
     */
    public int update(String jpql, Map<String, Object> parameters) {
        return service().update(jpql, parameters);
    }

    /**
     * See {@link BaseEntityService#save(Object)}.
     *
     * @param entity Entity to save.
     * @return Saved entity.
     * @since 1.3
     */
    public E save(E entity) {
        return service().save(entity);
    }

    /**
     * See {@link BaseEntityService#saveAndFlush(Object)}.
     *
     * @param entity Entity to save.
     * @return Saved entity.
     * @since 1.3
     */
    public E saveAndFlush(E entity) {
        return service().saveAndFlush(entity);
    }

    // Delete actions -------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#delete(Object)}.
     *
     * @param entity Entity to delete.
     * @since 1.3
     */
    public void delete(E entity) {
        service().delete(entity);
    }

    /**
     * See {@link BaseEntityService#softDelete(Object)}.
     *
     * @param entity Entity to soft delete.
     * @since 1.3
     */
    public void softDelete(E entity) {
        service().softDelete(entity);
    }

    /**
     * See {@link BaseEntityService#softUndelete(Object)}.
     *
     * @param entity Entity to soft undelete.
     * @since 1.3
     */
    public void softUndelete(E entity) {
        service().softUndelete(entity);
    }

    /**
     * See {@link BaseEntityService#delete(Iterable)}.
     *
     * @param entities Entities to delete.
     * @since 1.3
     */
    public void delete(Iterable<E> entities) {
        service().delete(entities);
    }

    /**
     * See {@link BaseEntityService#softDelete(Iterable)}.
     *
     * @param entities Entities to soft delete.
     * @since 1.3
     */
    public void softDelete(Iterable<E> entities) {
        service().softDelete(entities);
    }

    /**
     * See {@link BaseEntityService#softUndelete(Iterable)}.
     *
     * @param entities Entities to soft undelete.
     * @since 1.3
     */
    public void softUndelete(Iterable<E> entities) {
        service().softUndelete(entities);
    }

    // Manage actions -------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#manage(Object)}.
     *
     * @param entity Entity to manage.
     * @return The managed entity.
     * @since 1.3
     */
    public E manage(E entity) {
        return service().manage(entity);
    }

    /**
     * See {@link BaseEntityService#manageIfNecessary(Object)}.
     *
     * @param <T> The generic entity type.
     * @param entity Entity to manage, may be <code>null</code>.
     * @return The managed entity, or <code>null</code> when <code>null</code> was supplied.
     * @since 1.3
     */
    @Nullable
    public <T> T manageIfNecessary(@Nullable T entity) {
        return service().manageIfNecessary(entity);
    }

    /**
     * See {@link BaseEntityService#reset(Object)}.
     *
     * @param entity Entity to reset.
     * @since 1.3
     */
    public void reset(E entity) {
        service().reset(entity);
    }

    // Count actions --------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#countForeignKeyReferencesTo(Object)}.
     *
     * @param entity Entity to count all foreign key references for.
     * @return Count of all foreign key references to given entity.
     * @since 1.3
     */
    public long countForeignKeyReferencesTo(E entity) {
        return service().countForeignKeyReferencesTo(entity);
    }

    // Lazy fetching actions ------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#fetchLazyCollections(Object, Function...)}.
     *
     * @param entity Entity instance to fetch lazy collections on.
     * @param getters Getters of those lazy collections.
     * @return The same entity, useful if you want to continue using it immediately.
     * @since 1.3
     */
    @SafeVarargs
    public final E fetchLazyCollections(E entity, Function<E, Collection<?>>... getters) {
        return service().fetchLazyCollections(entity, getters);
    }

    /**
     * See {@link BaseEntityService#fetchLazyCollections(Optional, Function...)}.
     *
     * @param entity Optional entity instance to fetch lazy collections on.
     * @param getters Getters of those lazy collections.
     * @return The same optional entity, useful if you want to continue using it immediately.
     * @since 1.3
     */
    @SafeVarargs
    public final Optional<E> fetchLazyCollections(Optional<E> entity, Function<E, Collection<?>>... getters) {
        return service().fetchLazyCollections(entity, getters);
    }

    /**
     * See {@link BaseEntityService#fetchLazyMaps(Object, Function...)}.
     *
     * @param entity Entity instance to fetch lazy maps on.
     * @param getters Getters of those lazy maps.
     * @return The same entity, useful if you want to continue using it immediately.
     * @since 1.3
     */
    @SafeVarargs
    public final E fetchLazyMaps(E entity, Function<E, Map<?, ?>>... getters) {
        return service().fetchLazyMaps(entity, getters);
    }

    /**
     * See {@link BaseEntityService#fetchLazyMaps(Optional, Function...)}.
     *
     * @param entity Optional entity instance to fetch lazy maps on.
     * @param getters Getters of those lazy maps.
     * @return The same optional entity, useful if you want to continue using it immediately.
     * @since 1.3
     */
    @SafeVarargs
    public final Optional<E> fetchLazyMaps(Optional<E> entity, Function<E, Map<?, ?>>... getters) {
        return service().fetchLazyMaps(entity, getters);
    }

    /**
     * See {@link BaseEntityService#fetchLazyBlobs(Object)}.
     *
     * @param entity Entity instance to fetch all blobs on.
     * @return The same entity, useful if you want to continue using it immediately.
     * @since 1.3
     */
    public E fetchLazyBlobs(E entity) {
        return service().fetchLazyBlobs(entity);
    }

    /**
     * See {@link BaseEntityService#fetchLazyBlobs(Optional)}.
     *
     * @param entity Optional entity instance to fetch all blobs on.
     * @return The same optional entity, useful if you want to continue using it immediately.
     * @since 1.3
     */
    public Optional<E> fetchLazyBlobs(Optional<E> entity) {
        return service().fetchLazyBlobs(entity);
    }

    // Paging actions -------------------------------------------------------------------------------------------------

    /**
     * See {@link BaseEntityService#getPage(Page, boolean)}.
     *
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @return A partial result list based on given {@link Page}.
     * @since 1.3
     */
    public PartialResultList<E> getPage(Page page, boolean count) {
        return service().getPage(page, count);
    }

    /**
     * See {@link BaseEntityService#getPage(Page, boolean, String...)}.
     *
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @param fetchFields Optionally, all (lazy loaded) fields to be explicitly fetched during the query.
     * @return A partial result list based on given {@link Page}.
     * @since 1.3
     */
    public PartialResultList<E> getPage(Page page, boolean count, String... fetchFields) {
        return service().getPage(page, count, fetchFields);
    }

    /**
     * See {@link BaseEntityService#getPage(Page, boolean, boolean, String...)}.
     *
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @param cacheable Whether the results should be cacheable.
     * @param fetchFields Optionally, all (lazy loaded) fields to be explicitly fetched during the query.
     * @return A partial result list based on given {@link Page}.
     * @since 1.3
     */
    public PartialResultList<E> getPage(Page page, boolean count, boolean cacheable, String... fetchFields) {
        return service().getPage(page, count, cacheable, fetchFields);
    }

    /**
     * See {@link BaseEntityService#getPage(Page, boolean, QueryBuilder)}.
     *
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @param queryBuilder This allows fine-graining the Jakarta Persistence criteria query.
     * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
     * @since 1.3
     */
    public PartialResultList<E> getPage(Page page, boolean count, QueryBuilder<E> queryBuilder) {
        return service().getPage(page, count, queryBuilder);
    }

    /**
     * See {@link BaseEntityService#getPage(Page, boolean, boolean, QueryBuilder)}.
     *
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @param cacheable Whether the results should be cacheable.
     * @param queryBuilder This allows fine-graining the Jakarta Persistence criteria query.
     * @return A partial result list based on given {@link Page} and {@link QueryBuilder}.
     * @since 1.3
     */
    public PartialResultList<E> getPage(Page page, boolean count, boolean cacheable, QueryBuilder<E> queryBuilder) {
        return service().getPage(page, count, cacheable, queryBuilder);
    }

    /**
     * See {@link BaseEntityService#getPage(Page, boolean, Class, MappedQueryBuilder)}.
     *
     * @param <T> The generic type of the entity or a DTO subclass thereof.
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @param resultType The result type which can be the entity type itself or a DTO subclass thereof.
     * @param mappedQueryBuilder This allows fine-graining the Jakarta Persistence criteria query and must return a mapping of getters-paths.
     * @return A partial result list based on given {@link Page} and {@link MappedQueryBuilder}.
     * @since 1.3
     */
    public <T extends E> PartialResultList<T> getPage(Page page, boolean count, Class<T> resultType, MappedQueryBuilder<T> mappedQueryBuilder) {
        return service().getPage(page, count, resultType, mappedQueryBuilder);
    }

    /**
     * See {@link BaseEntityService#getPage(Page, boolean, boolean, Class, MappedQueryBuilder)}.
     *
     * @param <T> The generic type of the entity or a DTO subclass thereof.
     * @param page The page to return a partial result list for.
     * @param count Whether to run the <code>COUNT(id)</code> query to estimate total number of results.
     * @param cacheable Whether the results should be cacheable.
     * @param resultType The result type which can be the entity type itself or a DTO subclass thereof.
     * @param queryBuilder This allows fine-graining the Jakarta Persistence criteria query and must return a mapping of getters-paths when result type does not
     * equal entity type.
     * @return A partial result list based on given {@link Page} and {@link MappedQueryBuilder}.
     * @since 1.3
     */
    public <T extends E> PartialResultList<T> getPage(Page page, boolean count, boolean cacheable, Class<T> resultType, MappedQueryBuilder<T> queryBuilder) {
        return service().getPage(page, count, cacheable, resultType, queryBuilder);
    }

}
