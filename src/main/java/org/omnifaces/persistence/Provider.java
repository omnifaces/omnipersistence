package org.omnifaces.persistence;

import static javax.persistence.metamodel.Attribute.PersistentAttributeType.ELEMENT_COLLECTION;
import static org.omnifaces.utils.Collections.unmodifiableSet;
import static org.omnifaces.utils.reflect.Reflections.findClass;
import static org.omnifaces.utils.reflect.Reflections.findMethod;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import javax.persistence.ElementCollection;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.criteria.Expression;
import javax.persistence.metamodel.Attribute;

import org.omnifaces.persistence.service.BaseEntityService;

/**
 * Enumeration of all supported JPA providers.
 */
public enum Provider {

	HIBERNATE {

		@Override
		public String getDialectName(EntityManagerFactory entityManagerFactory) {
			return invokeMethod(invokeMethod(entityManagerFactory, "getSessionFactory"), "getDialect").getClass().getSimpleName();
		}

		@Override
		public boolean isAggregation(Expression<?> expression) {
			return HIBERNATE_BASIC_FUNCTION_EXPRESSION.get().isInstance(expression) && (boolean) invokeMethod(expression, "isAggregation");
		}

		@Override
		public boolean isProxy(Object object) {
			return HIBERNATE_PROXY_CLASS.get().isInstance(object);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> E dereferenceProxy(E entity) {
			return isProxy(entity) ? (E) invokeMethod(invokeMethod(entity, "getHibernateLazyInitializer"), "getImplementation") : entity;
		}
	},

	ECLIPSELINK {

		@Override
		public String getDialectName(EntityManagerFactory entityManagerFactory) {
			return invokeMethod(invokeMethod(entityManagerFactory, "getDatabaseSession"), "getDatasourcePlatform").getClass().getSimpleName();
		}

		@Override
		public boolean isAggregation(Expression<?> expression) {
			return ECLIPSELINK_FUNCTION_EXPRESSION_IMPL.get().isInstance(expression) && AGGREGATE_FUNCTIONS.contains(invokeMethod(expression, "getOperation"));
		}
	},

	OPENJPA {

		@Override
		public String getDialectName(EntityManagerFactory entityManagerFactory) {
			Optional<Method> getDelegate = findMethod(entityManagerFactory, "getDelegate");
			Object openjpaEntityManagerFactory = getDelegate.isPresent() ? invokeMethod(entityManagerFactory, getDelegate.get()) : entityManagerFactory;
			return invokeMethod(invokeMethod(openjpaEntityManagerFactory, "getConfiguration"), "getDBDictionaryInstance").getClass().getSimpleName();
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
	},

	UNKNOWN;

	private static final Optional<Class<Object>> HIBERNATE_PROXY_CLASS = findClass("org.hibernate.proxy.HibernateProxy");
	private static final Optional<Class<Object>> HIBERNATE_BASIC_FUNCTION_EXPRESSION = findClass("org.hibernate.jpa.criteria.expression.function.BasicFunctionExpression");
	private static final Optional<Class<Object>> ECLIPSELINK_FUNCTION_EXPRESSION_IMPL = findClass("org.eclipse.persistence.internal.jpa.querydef.FunctionExpressionImpl");
	private static final Set<String> AGGREGATE_FUNCTIONS = unmodifiableSet("MIN", "MAX", "SUM", "AVG", "COUNT");

	public static Provider of(EntityManager entityManager) {
		String packageName = entityManager.getDelegate().getClass().getPackage().getName();

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
		return BaseEntityService.getCurrentInstance().getProvider() == provider;
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

	public boolean isProxy(Object entity) {
		throw new UnsupportedOperationException(String.valueOf(entity));
	}

	public <E> E dereferenceProxy(E entity) {
		throw new UnsupportedOperationException(String.valueOf(entity));
	}

}
