/*
 * Copyright 2021 OmniFaces
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
import static org.omnifaces.persistence.JPA.getCurrentBaseEntityService;
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
import java.util.stream.Stream;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.metamodel.Attribute;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * Enumeration of all supported JPA providers.
 */
public enum Provider {

    HIBERNATE {

        @Override
        public String getDialectName(EntityManagerFactory entityManagerFactory) {
            var unwrappedEntityManagerFactory = unwrapEntityManagerFactoryIfNecessary(entityManagerFactory);

            if (HIBERNATE_SESSION_FACTORY.get().isInstance(unwrappedEntityManagerFactory)) {
                // 5.2+ has merged hibernate-entitymanager into hibernate-core, and made EntityManagerFactory impl an instance of SessionFactory, and removed getDialect() shortcut method.
                return invokeMethod(invokeMethod(invokeMethod(unwrappedEntityManagerFactory, "getJdbcServices"), "getJdbcEnvironment"), "getDialect").getClass().getSimpleName();
            }
            else {
                return invokeMethod(invokeMethod(unwrappedEntityManagerFactory, "getSessionFactory"), "getDialect").getClass().getSimpleName();
            }
        }

        @Override
        public boolean isAggregation(Expression<?> expression) {
            if (HIBERNATE_6_0_0_AGGREGATE_FUNCTION.isPresent()) {
                return HIBERNATE_6_0_0_AGGREGATE_FUNCTION.get().isInstance(expression);
            }
            else {
                return HIBERNATE_BASIC_FUNCTION_EXPRESSION.get().isInstance(expression) && (boolean) invokeMethod(expression, "isAggregation")
                    || HIBERNATE_COMPARISON_PREDICATE.get().isInstance(expression) && (isAggregation(invokeMethod(expression, "getLeftHandOperand")) || isAggregation(invokeMethod(expression, "getRightHandOperand")));
            }
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

        @SuppressWarnings("unchecked")
        private <T, I extends Comparable<I> & Serializable, E extends BaseEntity<I>> T invokeOnProxy(E entity, String methodName, Function<E, T> fallback) {
            return isProxy(entity) ? (T) invokeMethod(invokeMethod(entity, "getHibernateLazyInitializer"), methodName) : fallback.apply(entity);
        }
    },

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
    },

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
    },

    UNKNOWN;

    public static final String QUERY_HINT_HIBERNATE_CACHEABLE = "org.hibernate.cacheable"; // true | false
    public static final String QUERY_HINT_HIBERNATE_CACHE_REGION = "org.hibernate.cacheRegion"; // 2nd level cache region ID
    public static final String QUERY_HINT_ECLIPSELINK_MAINTAIN_CACHE = "eclipselink.maintain-cache"; // true | false
    public static final String QUERY_HINT_ECLIPSELINK_REFRESH = "eclipselink.refresh"; // true | false

    private static final Optional<Class<Object>> HIBERNATE_PROXY = findClass("org.hibernate.proxy.HibernateProxy");
    private static final Optional<Class<Object>> HIBERNATE_SESSION_FACTORY = findClass("org.hibernate.SessionFactory");
    private static final Optional<Class<Object>> HIBERNATE_3_5_0_BASIC_FUNCTION_EXPRESSION = findClass("org.hibernate.ejb.criteria.expression.function.BasicFunctionExpression");
    private static final Optional<Class<Object>> HIBERNATE_4_3_0_BASIC_FUNCTION_EXPRESSION = findClass("org.hibernate.jpa.criteria.expression.function.BasicFunctionExpression");
    private static final Optional<Class<Object>> HIBERNATE_5_2_0_BASIC_FUNCTION_EXPRESSION = findClass("org.hibernate.query.criteria.internal.expression.function.BasicFunctionExpression");
    private static final Optional<Class<Object>> HIBERNATE_BASIC_FUNCTION_EXPRESSION = Stream.of(HIBERNATE_5_2_0_BASIC_FUNCTION_EXPRESSION, HIBERNATE_4_3_0_BASIC_FUNCTION_EXPRESSION, HIBERNATE_3_5_0_BASIC_FUNCTION_EXPRESSION).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    private static final Optional<Class<Object>> HIBERNATE_6_0_0_AGGREGATE_FUNCTION = findClass("org.hibernate.query.sqm.function.SelfRenderingSqmAggregateFunction");
    private static final Optional<Class<Object>> HIBERNATE_3_5_0_COMPARISON_PREDICATE = findClass("org.hibernate.ejb.criteria.predicate.ComparisonPredicate");
    private static final Optional<Class<Object>> HIBERNATE_4_3_0_COMPARISON_PREDICATE = findClass("org.hibernate.jpa.criteria.predicate.ComparisonPredicate");
    private static final Optional<Class<Object>> HIBERNATE_5_2_0_COMPARISON_PREDICATE = findClass("org.hibernate.query.criteria.internal.predicate.ComparisonPredicate");
    private static final Optional<Class<Object>> HIBERNATE_COMPARISON_PREDICATE = Stream.of(HIBERNATE_5_2_0_COMPARISON_PREDICATE, HIBERNATE_4_3_0_COMPARISON_PREDICATE, HIBERNATE_3_5_0_COMPARISON_PREDICATE).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    private static final Optional<Class<Object>> ECLIPSELINK_FUNCTION_EXPRESSION_IMPL = findClass("org.eclipse.persistence.internal.jpa.querydef.FunctionExpressionImpl");
    private static final Set<String> AGGREGATE_FUNCTIONS = unmodifiableSet("MIN", "MAX", "SUM", "AVG", "COUNT");

    private static Object unwrapEntityManagerFactoryIfNecessary(EntityManagerFactory entityManagerFactory) {
        var packageName = entityManagerFactory.getClass().getPackage().getName();

        if (packageName.startsWith("org.apache.openejb.")) {
            var getDelegate = findMethod(entityManagerFactory, "getDelegate");
            return getDelegate.isPresent() ? invokeMethod(entityManagerFactory, getDelegate.get()) : entityManagerFactory;
        }

        return entityManagerFactory;
    }

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

    public static boolean is(Provider provider) {
        return getCurrentBaseEntityService().getProvider() == provider;
    }

    public String getDialectName(EntityManagerFactory entityManagerFactory) {
        throw new UnsupportedOperationException(String.valueOf(entityManagerFactory));
    }

    public boolean isAggregation(Expression<?> expression) {
        throw new UnsupportedOperationException(String.valueOf(expression));
    }

    public boolean isElementCollection(Attribute<?, ?> attribute) {
        return attribute.getPersistentAttributeType() == ELEMENT_COLLECTION;
    }

    public boolean isOneToMany(Attribute<?, ?> attribute) {
        return attribute.getPersistentAttributeType() == ONE_TO_MANY;
    }

    public boolean isManyOrOneToOne(Attribute<?, ?> attribute) {
        return isOneOf(attribute.getPersistentAttributeType(), MANY_TO_ONE, ONE_TO_ONE);
    }

    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> boolean isProxy(E entity) {
        return false;
    }

    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> boolean isProxyUninitialized(E entity) {
        return false;
    }

    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> E dereferenceProxy(E entity) {
        return entity;
    }

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

    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> I getIdentifier(E entity) {
        return entity == null ? null : entity.getId();
    }

    public <I extends Comparable<I> & Serializable, E extends BaseEntity<I>> String getTableName(E entity) {
        if (entity == null) {
            return null;
        }

        Class<E> entityType = getEntityType(entity);
        var table = entityType.getAnnotation(Table.class);
        return table != null ? table.name() : entityType.getSimpleName().toUpperCase();
    }

}
