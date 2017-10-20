/*
 * Copyright 2015 OmniFaces.
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
package org.omnifaces.persistence;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.omnifaces.persistence.Database.POSTGRESQL;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.utils.stream.Collectors.toMap;
import static org.omnifaces.utils.stream.Streams.stream;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.Typed;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.CollectionAttribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;

/**
 * JPA utilities.
 */
@Typed
public final class JPA {

	// Public constants -------------------------------------------------------------------------------------------------------------------

	public static final String LOAD_GRAPH_HINT_KEY = "javax.persistence.loadgraph";
	public static final String FETCH_GRAPH_HINT_KEY = "javax.persistence.fetchgraph";
	public static final String CACHE_RETRIEVE_MODE_HINT_KEY = "javax.persistence.cache.retrieveMode";


	// Constructors -----------------------------------------------------------------------------------------------------------------------

	private JPA() {
		throw new AssertionError();
	}


	// Query utils ------------------------------------------------------------------------------------------------------------------------

	/**
	 * Returns single result of given typed query as {@link Optional}.
	 * @param <T> The generic result type.
	 * @param typedQuery The involved typed query.
	 * @return Single result of given typed query as {@link Optional}.
	 */
	public static <T> Optional<T> getOptionalSingleResult(TypedQuery<T> typedQuery) {
		return ofNullable(getSingleResultOrNull(typedQuery));
	}

	/**
	 * Returns single result of given query as {@link Optional}.
	 * @param <T> The expected result type.
	 * @param query The involved query.
	 * @param resultType The expected result type.
	 * @return Single result of given query as {@link Optional}.
	 * @throws ClassCastException When <code>T</code> is of wrong type.
	 */
	public static <T> Optional<T> getOptionalSingleResult(Query query, Class<T> resultType) {
		return ofNullable(getSingleResultOrNull(query, resultType));
	}

	/**
	 * Returns single result of given typed query, or <code>null</code> if there is none.
	 * @param <T> The generic result type.
	 * @param typedQuery The involved typed query.
	 * @return Single result of given typed query, or <code>null</code> if there is none.
	 */
	public static <T> T getSingleResultOrNull(TypedQuery<T> typedQuery) {
		return getSingleResultOrNull(typedQuery, null);
	}

	/**
	 * Returns single result of given query, or <code>null</code> if there is none.
	 * @param <T> The expected result type.
	 * @param query The involved query.
	 * @param resultType The expected result type.
	 * @return Single result of given query, or <code>null</code> if there is none.
	 * @throws ClassCastException When <code>T</code> is of wrong type.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getSingleResultOrNull(Query query, Class<T> resultType) {
		try {
			query.setMaxResults(1);
			Object singleResult = query.getSingleResult();
			return resultType == null ? (T) singleResult : resultType.cast(singleResult);
		}
		catch (NoResultException e) {
			return null;
		}
	}

	/**
	 * Returns the result list of given typed query as a map mapped by the given key mapper.
	 * @param <K> The generic map key type.
	 * @param <T> The generic result type, also map value type.
	 * @param typedQuery The involved typed query.
	 * @param keyMapper The key mapper.
	 * @return The result list of given typed query as a map mapped by the given key mapper.
	 */
	public static <K, T> Map<K, T> getResultMap(TypedQuery<T> typedQuery, Function<? super T, ? extends K> keyMapper) {
		return typedQuery.getResultList().stream().collect(toMap(keyMapper));
	}

	/**
	 * Returns the result list of given typed query as a map mapped by the given key and value mappers.
	 * @param <K> The generic map key type.
	 * @param <T> The generic result type.
	 * @param <V> The generic map value type.
	 * @param query The involved typed query.
	 * @param keyMapper The key mapper.
	 * @param valueMapper The value mapper.
	 * @return The result list of given typed query as a map mapped by the given key and value mappers.
	 */
	public static <K, T, V> Map<K, V> getResultMap(TypedQuery<T> query, Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
		return query.getResultList().stream().collect(Collectors.toMap(keyMapper, valueMapper));
	}


	// Entity utils -----------------------------------------------------------------------------------------------------------------------

	/**
	 * Returns count of all foreign key references to entity of given entity type with given ID of given identifier type.
	 * This is particularly useful in case you intend to check if the given entity is orphaned in that perspective.
	 * @param <T> The generic result type.
	 * @param <I> The generic identifier type.
	 * @param entityManager The involved entity manager.
	 * @param entityType Entity type.
	 * @param identifierType Identifier type.
	 * @param id Entity ID.
	 * @return Count of all foreign key references to entity of given entity type with given ID of given identifier type.
	 */
	public static <T, I> Long countForeignKeyReferences(EntityManager entityManager, Class<T> entityType, Class<I> identifierType, I id) {
		Metamodel metamodel = entityManager.getMetamodel();
		SingularAttribute<? super T, I> idAttribute = metamodel.entity(entityType).getId(identifierType);
		return metamodel.getEntities().stream()
			.flatMap(entity -> getAttributesOfType(entity, entityType))
			.distinct()
			.mapToLong(attribute -> countReferencesTo(entityManager, attribute, idAttribute, id))
			.sum();
	}

	private static <E, T> Stream<Attribute<?, ?>> getAttributesOfType(EntityType<E> entity, Class<T> entityType) {
		return entity.getAttributes().stream()
			.filter(attribute -> entityType.equals(getJavaType(attribute)))
			.map(attribute -> attribute);
	}

	private static <E> Class<?> getJavaType(Attribute<? super E, ?> attribute) {
		return (attribute instanceof PluralAttribute)
			? ((PluralAttribute<?, ?, ?>) attribute).getElementType().getJavaType()
			: attribute.getJavaType();
	}

	@SuppressWarnings("unchecked")
	private static <E, T, I> Long countReferencesTo(EntityManager entityManager, Attribute<E, ?> attribute, SingularAttribute<? super T, I> idAttribute, I id) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
		Root<E> root = query.from(attribute.getDeclaringType().getJavaType());
		Join<E, T> join;

		if (attribute instanceof SingularAttribute) {
			join = root.join((SingularAttribute<E, T>) attribute);
		}
		else if (attribute instanceof ListAttribute) {
			join = root.join((ListAttribute<E, T>) attribute);
		}
		else if (attribute instanceof SetAttribute) {
			join = root.join((SetAttribute<E, T>) attribute);
		}
		else if (attribute instanceof MapAttribute) {
			join = root.join((MapAttribute<E, ?, T>) attribute);
		}
		else if (attribute instanceof CollectionAttribute) {
			join = root.join((CollectionAttribute<E, T>) attribute);
		}
		else {
			return 0L; // Unknown attribute type, just return 0.
		}

		query.select(criteriaBuilder.count(root)).where(criteriaBuilder.equal(join.get(idAttribute), id));
		return entityManager.createQuery(query).getSingleResult();
	}


	// Criteria utils ---------------------------------------------------------------------------------------------------------------------

	/**
	 * Returns a SQL CONCAT(...) of given expressions or strings.
	 * @param builder The involved criteria builder.
	 * @param expressionsOrStrings Expressions or Strings.
	 * @return A SQL CONCAT(...) of given expressions or strings.
	 * @throws IllegalArgumentException When there are less than 2 expressions or strings. There's no point of concat then.
	 */
	@SuppressWarnings("unchecked")
	public static Expression<String> concat(CriteriaBuilder builder, Object... expressionsOrStrings) {
		if (expressionsOrStrings.length < 2) {
			throw new IllegalArgumentException("There must be at least 2 expressions or strings");
		}

		List<Expression<? extends Object>> expressions = stream(expressionsOrStrings).map(expressionOrString -> {
			if (expressionOrString instanceof Expression) {
				return castAsString(builder, (Expression<?>) expressionOrString);
			}
			else {
				return builder.literal(expressionOrString);
			}
		}).collect(toList());

		return builder.function("CONCAT", String.class, expressions.toArray(new Expression[expressions.size()]));
	}

	/**
	 * Returns a new expression wherein given expression is cast as String.
	 * This covers known problems with certain providers and/or databases.
	 * @param builder The involved criteria builder.
	 * @param expression Expression to be cast as String.
	 * @return A new expression wherein given expression is cast as String.
	 */
	@SuppressWarnings("unchecked")
	public static Expression<String> castAsString(CriteriaBuilder builder, Expression<?> expression) {
		boolean numeric = Number.class.isAssignableFrom(expression.getJavaType());

		if (!numeric || Provider.is(HIBERNATE)) { // EclipseLink and OpenJPA have a broken Path#as() implementation, need to delegate to DB specific function.
			return expression.as(String.class);
		}
		else if (Database.is(POSTGRESQL)) {
			return builder.function("TO_CHAR", String.class, expression, builder.literal("FM999999999999999999"));
		}
		else { // H2 and MySQL are more lenient in this, no function call necessary.
			return (Expression<String>) expression;
		}
	}

}
