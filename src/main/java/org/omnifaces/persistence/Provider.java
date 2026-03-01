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
package org.omnifaces.persistence;

import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ELEMENT_COLLECTION;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_MANY;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_ONE;
import static org.omnifaces.persistence.JPA.QUERY_HINT_CACHE_RETRIEVE_MODE;
import static org.omnifaces.persistence.JPA.QUERY_HINT_CACHE_STORE_MODE;
import static org.omnifaces.utils.Collections.unmodifiableSet;
import static org.omnifaces.utils.Lang.isOneOf;
import static org.omnifaces.utils.reflect.Reflections.findClass;
import static org.omnifaces.utils.reflect.Reflections.findMethod;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;

import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * Enumeration of all supported Jakarta Persistence providers. The provider is automatically detected from the {@link EntityManager}'s
 * delegate class.
 * <p>
 * Currently supported providers:
 * <ul>
 * <li>{@link #HIBERNATE}
 * <li>{@link #ECLIPSELINK}
 * <li>{@link #OPENJPA}
 * </ul>
 * <p>
 * Each provider has specific handling for aggregation detection, proxy resolution, dialect name resolution,
 * and relationship mapping quirks. The {@link BaseEntityService} uses this internally to generate correct queries
 * across different providers.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Database
 * @see BaseEntityService#getProvider()
 */
public enum Provider {

    /**
     * Hibernate provider.
     */
    HIBERNATE {

        @Override
        public String getDialectName(EntityManagerFactory entityManagerFactory) {
            var unwrappedEntityManagerFactory = unwrapEntityManagerFactoryIfNecessary(entityManagerFactory);
            return invokeMethod(invokeMethod(invokeMethod(unwrappedEntityManagerFactory, "getJdbcServices"), "getJdbcEnvironment"), "getDialect").getClass().getSimpleName();
        }

        @Override
        public boolean isAggregation(Expression<?> expression) {
            return HIBERNATE_AGGREGATE_FUNCTION.get().isInstance(expression);
        }

        @Override
        public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> boolean isProxy(E entity) {
            return HIBERNATE_PROXY.get().isInstance(entity);
        }

        @Override
        public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> boolean isProxyUninitialized(E entity) {
            return invokeOnProxy(entity, "isUninitialized", super::isProxyUninitialized);
        }

        @Override
        public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> E dereferenceProxy(E entity) {
            return invokeOnProxy(entity, "getImplementation", super::dereferenceProxy);
        }

        @Override
        public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> Class<E> getEntityType(E entity) {
            return invokeOnProxy(entity, "getPersistentClass", super::getEntityType);
        }

        @Override
        public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> I getIdentifier(E entity) {
            return invokeOnProxy(entity, "getIdentifier", super::getIdentifier);
        }

        @Override
        public void configureSecondLevelCache(Query query, boolean cacheable) {
            query.setHint(QUERY_HINT_HIBERNATE_CACHEABLE, cacheable);
            super.configureSecondLevelCache(query, cacheable);
        }

        @SuppressWarnings("unchecked")
        private <T, I extends Comparable<I> & Serializable, E extends BaseEntity<I>> T invokeOnProxy(E entity, String methodName, Function<E, T> fallback) {
            return isProxy(entity) ? (T) invokeMethod(invokeMethod(entity, "getHibernateLazyInitializer"), methodName) : fallback.apply(entity);
        }
    },

    /**
     * EclipseLink provider.
     */
    ECLIPSELINK {

        @Override
        public String getDialectName(EntityManagerFactory entityManagerFactory) {
            var unwrappedEntityManagerFactory = unwrapEntityManagerFactoryIfNecessary(entityManagerFactory);
            return invokeMethod(invokeMethod(unwrappedEntityManagerFactory, "getDatabaseSession"), "getDatasourcePlatform").getClass().getSimpleName();
        }

        @Override
        public boolean isAggregation(Expression<?> expression) {
            return ECLIPSELINK_FUNCTION_EXPRESSION_IMPL.get().isInstance(expression) && AGGREGATE_FUNCTIONS.contains(invokeMethod(expression, "getOperation"));
        }

        @Override
        public void configureSecondLevelCache(Query query, boolean cacheable) {
            query
                .setHint(QUERY_HINT_ECLIPSELINK_MAINTAIN_CACHE, cacheable)
                .setHint(QUERY_HINT_ECLIPSELINK_REFRESH, !cacheable);
            super.configureSecondLevelCache(query, cacheable);
        }
    },

    /**
     * OpenJPA provider.
     */
    OPENJPA {

        @Override
        public String getDialectName(EntityManagerFactory entityManagerFactory) {
            var unwrappedEntityManagerFactory = unwrapEntityManagerFactoryIfNecessary(entityManagerFactory);
            return invokeMethod(invokeMethod(unwrappedEntityManagerFactory, "getConfiguration"), "getDBDictionaryInstance").getClass().getSimpleName();
        }

        @Override
        public boolean isAggregation(Expression<?> expression) {
            // We could also invoke toValue() on it and then isAggregate(), but that requires ExpressionFactory and CriteriaQueryImpl arguments which are not trivial to get here.
            return AGGREGATE_FUNCTIONS.contains(expression.getClass().getSimpleName().toUpperCase());
        }

        @Override
        public boolean isElementCollection(Attribute<?, ?> attribute) {
            // For some reason OpenJPA returns PersistentAttributeType.ONE_TO_MANY on an @ElementCollection.
            return ((Field) attribute.getJavaMember()).getAnnotation(ElementCollection.class) != null;
        }

        @Override
        public boolean isOneToMany(Attribute<?, ?> attribute) {
            // For some reason OpenJPA returns PersistentAttributeType.ONE_TO_MANY on an @ElementCollection.
            return !isElementCollection(attribute) && super.isOneToMany(attribute);
        }

        @Override
        public void configureSecondLevelCache(Query query, boolean cacheable) {
            var openJpaQuery = OPENJPA_QUERY_IMPL.map(query::unwrap).orElse(query);
            invokeMethod(invokeMethod(openJpaQuery, "getFetchPlan"), "setQueryResultCacheEnabled", cacheable);
        }
    },

    /**
     * Provider is unknown.
     */
    UNKNOWN;

    /** Returns the Hibernate-specific "cacheable" query hint constant: {@value}. */
    public static final String QUERY_HINT_HIBERNATE_CACHEABLE = "org.hibernate.cacheable"; // true | false
    /** Returns the Hibernate-specific "cache region" query hint constant: {@value}. */
    public static final String QUERY_HINT_HIBERNATE_CACHE_REGION = "org.hibernate.cacheRegion"; // 2nd level cache region ID
    /** Returns the EclipseLink-specific "maintain cache" query hint constant: {@value}. */
    public static final String QUERY_HINT_ECLIPSELINK_MAINTAIN_CACHE = "eclipselink.maintain-cache"; // true | false
    /** Returns the EclipseLink-specific "refresh" query hint constant: {@value}. */
    public static final String QUERY_HINT_ECLIPSELINK_REFRESH = "eclipselink.refresh"; // true | false

    private static final Optional<Class<Object>> HIBERNATE_PROXY = findClass("org.hibernate.proxy.HibernateProxy");
    private static final Optional<Class<Object>> HIBERNATE_AGGREGATE_FUNCTION = findClass("org.hibernate.query.sqm.function.SelfRenderingSqmAggregateFunction");
    private static final Optional<Class<Object>> ECLIPSELINK_FUNCTION_EXPRESSION_IMPL = findClass("org.eclipse.persistence.internal.jpa.querydef.FunctionExpressionImpl");
    private static final Optional<Class<Object>> OPENJPA_QUERY_IMPL = findClass("org.apache.openjpa.persistence.OpenJPAQuery");
    private static final Set<String> AGGREGATE_FUNCTIONS = unmodifiableSet("MIN", "MAX", "SUM", "AVG", "COUNT");

    private static Object unwrapEntityManagerFactoryIfNecessary(EntityManagerFactory entityManagerFactory) {
        var packageName = entityManagerFactory.getClass().getPackage().getName();

        if (packageName.startsWith("org.apache.openejb.")) {
            var getDelegate = findMethod(entityManagerFactory, "getDelegate");
            return getDelegate.isPresent() ? invokeMethod(entityManagerFactory, getDelegate.get()) : entityManagerFactory;
        }

        return entityManagerFactory;
    }

    /**
     * Returns the {@link Provider} associated with the given entity manager.
     * @param entityManager The entity manager to detect the provider for.
     * @return The {@link Provider} associated with the given entity manager.
     */
    public static Provider of(EntityManager entityManager) {
        var packageName = entityManager.getDelegate().getClass().getPackage().getName();

        if (packageName.startsWith("org.hibernate.")) {
            return HIBERNATE;
        }
        else if (packageName.startsWith("org.eclipse.persistence.")) {
            return ECLIPSELINK;
        }
        else if (packageName.startsWith("org.apache.openjpa.")) {
            return OPENJPA;
        }
        else {
            return UNKNOWN;
        }
    }

    /**
     * Returns the dialect name of the given entity manager factory.
     * The default implementation throws {@link UnsupportedOperationException}.
     * @param entityManagerFactory The entity manager factory to get the dialect name for.
     * @return The dialect name of the given entity manager factory.
     */
    public String getDialectName(EntityManagerFactory entityManagerFactory) {
        throw new UnsupportedOperationException(String.valueOf(entityManagerFactory));
    }

    /**
     * Returns whether the given expression is an aggregation.
     * The default implementation throws {@link UnsupportedOperationException}.
     * @param expression The expression to check.
     * @return Whether the given expression is an aggregation.
     */
    public boolean isAggregation(Expression<?> expression) {
        throw new UnsupportedOperationException(String.valueOf(expression));
    }

    /**
     * Returns whether the given attribute is an element collection.
     * The default implementation returns {@code true} if {@link Attribute#getPersistentAttributeType()} equals {@link PersistentAttributeType#ELEMENT_COLLECTION}.
     * @param attribute The attribute to check.
     * @return Whether the given attribute is an element collection.
     */
    public boolean isElementCollection(Attribute<?, ?> attribute) {
        return attribute.getPersistentAttributeType() == ELEMENT_COLLECTION;
    }

    /**
     * Returns whether the given attribute is a one-to-many relationship.
     * The default implementation returns {@code true} if {@link Attribute#getPersistentAttributeType()} equals {@link PersistentAttributeType#ONE_TO_MANY}.
     * @param attribute The attribute to check.
     * @return Whether the given attribute is a one-to-many relationship.
     */
    public boolean isOneToMany(Attribute<?, ?> attribute) {
        return attribute.getPersistentAttributeType() == ONE_TO_MANY;
    }

    /**
     * Returns whether the given attribute is a many-to-one or one-to-one relationship.
     * The default implementation returns {@code true} if {@link Attribute#getPersistentAttributeType()} equals {@link PersistentAttributeType#MANY_TO_ONE} or {@link PersistentAttributeType#ONE_TO_ONE}.
     * @param attribute The attribute to check.
     * @return Whether the given attribute is a many-to-one or one-to-one relationship.
     */
    public boolean isManyOrOneToOne(Attribute<?, ?> attribute) {
        return isOneOf(attribute.getPersistentAttributeType(), MANY_TO_ONE, ONE_TO_ONE);
    }

    /**
     * Returns whether the given entity is a proxy.
     * The default implementation returns {@code false}.
     * @param <I> The generic ID type.
     * @param <E> The generic entity type.
     * @param entity The entity to check.
     * @return Whether the given entity is a proxy.
     */
    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> boolean isProxy(E entity) {
        return false;
    }

    /**
     * Returns whether the given entity is an uninitialized proxy.
     * The default implementation returns {@code false}.
     * @param <I> The generic ID type.
     * @param <E> The generic entity type.
     * @param entity The entity to check.
     * @return Whether the given entity is an uninitialized proxy.
     */
    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> boolean isProxyUninitialized(E entity) {
        return false;
    }

    /**
     * Returns the dereferenced entity of the given entity. If it is a proxy, then the actual implementation will be returned.
     * The default implementation directly returns the given entity.
     * @param <I> The generic ID type.
     * @param <E> The generic entity type.
     * @param entity The entity to dereference.
     * @return The dereferenced entity of the given entity.
     */
    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> E dereferenceProxy(E entity) {
        return entity;
    }

    /**
     * Returns the actual entity type of the given entity. If it is a proxy, then the type of the actual implementation will be returned.
     * The default implementation returns the first class in the hierarchy having the {@link Entity} annotation.
     * @param <I> The generic ID type.
     * @param <E> The generic entity type.
     * @param entity The entity to get the type for.
     * @return The actual entity type of the given entity.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> Class<E> getEntityType(E entity) {
        if (entity == null) {
            return null;
        }

        Class<? extends BaseEntity> entityType = entity.getClass();

        while (BaseEntity.class.isAssignableFrom(entityType) && entityType.getAnnotation(Entity.class) == null)
        {
            entityType = (Class<? extends BaseEntity>) entityType.getSuperclass();
        }

        return (Class<E>) entityType;
    }

    /**
     * Returns the identifier of the given entity. If it is a proxy, then the identifier will be extracted from the proxy.
     * The default implementation returns {@link BaseEntity#getId()}.
     * @param <I> The generic ID type.
     * @param <E> The generic entity type.
     * @param entity The entity to get the identifier for.
     * @return The identifier of the given entity.
     */
    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> I getIdentifier(E entity) {
        return entity == null ? null : entity.getId();
    }

    /**
     * Returns the table name of the given entity.
     * The default implementation returns the {@link Table} annotation of {@link #getEntityType(BaseEntity)} or else defaults to entity class' simple name in upper cased form.
     * @param <I> The generic ID type.
     * @param <E> The generic entity type.
     * @param entity The entity to get the table name for.
     * @return The table name of the given entity.
     */
    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> String getTableName(E entity) {
        if (entity == null) {
            return null;
        }

        Class<E> entityType = getEntityType(entity);
        var table = entityType.getAnnotation(Table.class);
        return table != null ? table.name() : entityType.getSimpleName().toUpperCase();
    }

    /**
     * Applies 2nd level cache-related hints to the given query. The default implementation sets the standard Jakarta
     * Persistence {@code jakarta.persistence.cache.storeMode} and {@code jakarta.persistence.cache.retrieveMode} hints.
     * When {@code cacheable} is {@code true}, results are read from and stored in the 2nd level cache; otherwise
     * results are read from the DB and the cache is force-refreshed.
     * @param query The query to apply 2nd level cache hints to.
     * @param cacheable Whether results should be read from and stored in the 2nd level cache.
     */
    public void configureSecondLevelCache(Query query, boolean cacheable) {
        query
            .setHint(QUERY_HINT_CACHE_STORE_MODE, cacheable ? CacheStoreMode.USE : CacheStoreMode.REFRESH)
            .setHint(QUERY_HINT_CACHE_RETRIEVE_MODE, cacheable ? CacheRetrieveMode.USE : CacheRetrieveMode.BYPASS);
    }

}
