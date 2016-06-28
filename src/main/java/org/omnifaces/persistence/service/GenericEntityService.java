package org.omnifaces.persistence.service;

import static java.util.Arrays.stream;
import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.emptyMap;
import static java.util.regex.Pattern.quote;
import static javax.persistence.criteria.JoinType.LEFT;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.Lang.isOneOf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Parameter;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.TypedValue;
import org.hibernate.hql.internal.ast.ASTQueryTranslatorFactory;
import org.hibernate.hql.spi.ParameterTranslations;
import org.hibernate.hql.spi.QueryTranslator;
import org.hibernate.hql.spi.QueryTranslatorFactory;
import org.hibernate.internal.AbstractQueryImpl;
import org.omnifaces.persistence.JPA;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.SortFilterPage;
import org.omnifaces.utils.collection.PartialResultList;

public class GenericEntityService {

	private final static Predicate[] PREDICATE_ARRAY = new Predicate[0];

	private EntityManager entityManager;
	private EntityManagerFactory entityManagerFactory;

	public static interface QueryBuilder<T> {
		Root<?> build(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Class<?> type);
	}

	public static void sort(CriteriaBuilder builder, CriteriaQuery<?> query, String sortOrder, Expression<?>... sortExpressions) {
		query.orderBy(stream(sortExpressions).map(e -> "ASCENDING".equals(sortOrder) ? builder.asc(e) : builder.desc(e)).collect(Collectors.toList()));
	}

	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}

	public BaseEntity<? extends Number> find(Class<BaseEntity<? extends Number>> type, Number id) {
		return entityManager.find(type, id);
	}

	public BaseEntity<? extends Number> findWithDepth(Class<BaseEntity<? extends Number>> type, Number id, String... fetchRelations) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<BaseEntity<? extends Number>> criteriaQuery = criteriaBuilder.createQuery(type);
		Root<BaseEntity<? extends Number>> root = criteriaQuery.from(type);

		for (String relation : fetchRelations) {
			FetchParent<BaseEntity<? extends Number>, BaseEntity<? extends Number>> fetch = root;
			for (String pathSegment : relation.split(quote("."))) {
				fetch = fetch.fetch(pathSegment, LEFT);
			}
		}

		criteriaQuery.where(criteriaBuilder.equal(root.get("id"), id));

		return JPA.getOptionalSingleResult(entityManager.createQuery(criteriaQuery));
	}

	public <T> PartialResultList<T> getAllPaged(Class<T> returnType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(returnType, queryBuilder, parameters, sortFilterPage, getCount, true, true);
	}

	public <T> PartialResultList<T> getAllPagedUncached(Class<T> returnType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(returnType, queryBuilder, parameters, sortFilterPage, getCount, true, false);
	}

	public <T extends BaseEntity<? extends Number>> PartialResultList<T> getAllPagedAndSorted(Class<T> type, SortFilterPage sortFilterPage) {
		return getAllPagedAndSorted(type, (builder, query, tp) -> query.from(tp), emptyMap(), sortFilterPage, true, false, true);
	}

	public <T> PartialResultList<T> getAllPagedAndSorted(Class<T> returnType, QueryBuilder<?> queryBuilder,	Map<String, Object> parameters,	SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(returnType, queryBuilder, parameters, sortFilterPage, getCount, false, true);
	}

	public <T> PartialResultList<T> getAllPagedAndSortedUncached(Class<T> returnType, QueryBuilder<?> queryBuilder,	Map<String, Object> parameters,	SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(returnType, queryBuilder, parameters, sortFilterPage, getCount, false, false);
	}

	public <T> Root<T> getRootQuery(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Class<T> type) {
		return criteriaQuery.from(type);
	}

	public QueryTranslator translateFromQuery(javax.persistence.Query query) {
		return translateFromHql(query.unwrap(Query.class).getQueryString());
	}

	public QueryTranslator translateFromHql(String hqlQueryText) {

		QueryTranslatorFactory translatorFactory = new ASTQueryTranslatorFactory();

		QueryTranslator translator = translatorFactory.createQueryTranslator(
			hqlQueryText, hqlQueryText,
			EMPTY_MAP,
			(SessionFactoryImplementor) entityManagerFactory.unwrap(SessionFactory.class),
			null
		);

		translator.compile(EMPTY_MAP, false);

		return translator;

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <T> PartialResultList<T> getAllPagedAndSorted(Class<T> returnType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount, boolean isSorted, boolean isCached) {

		// Create the two standard JPA objects used for criteria query construction
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(returnType);

		// Obtain main query from the passed-in builder
		Root<?> root = 	queryBuilder.build(criteriaBuilder, criteriaQuery, returnType);

		// Add sorting to query if necessary
		if (!isSorted && !isEmpty(sortFilterPage.getSortField())) {
			criteriaQuery.orderBy(
				"ASCENDING".equals(sortFilterPage.getSortOrder())?
					criteriaBuilder.asc(root.get(sortFilterPage.getSortField())) :
					criteriaBuilder.desc(root.get(sortFilterPage.getSortField()))
			);
		}

		List<Predicate> searchPredicates = new ArrayList<>();
		List<Predicate> exactPredicates = new ArrayList<>();

		// Add filtering to query
		sortFilterPage.getFilterValues().entrySet().forEach(
			e -> {
				String key = e.getKey() + "Search";
				String value = e.getValue().toString();
				Class<?> type = root.get(e.getKey()).getJavaType();

				if (type.isEnum()) {
					try {
						Enum enumValue = Enum.valueOf((Class<Enum>) type, value);
						exactPredicates.add(criteriaBuilder.equal(root.get(e.getKey()), criteriaBuilder.parameter(type, key)));
						parameters.put(key, enumValue);
					}
					catch (IllegalArgumentException ignore) {
						//
					}
				}
				else if (type.isAssignableFrom(Boolean.class)) {
					exactPredicates.add(criteriaBuilder.equal(root.get(e.getKey()), criteriaBuilder.parameter(type, key)));
					parameters.put(key, Boolean.valueOf(value));
				}
				else if (type.isAssignableFrom(Long.class)) {
					if (isOneOf(value, "true", "false")) {
						Path<Long> path = root.get(e.getKey());
						ParameterExpression<Long> parameter = criteriaBuilder.parameter(Long.class, key);
						exactPredicates.add("true".equals(value) ? criteriaBuilder.gt(path, parameter) : criteriaBuilder.le(path, parameter));
						parameters.put(key, 0L);
					}
				}
				else if (!sortFilterPage.getFilterableFields().contains(e.getKey())) {
					exactPredicates.add(criteriaBuilder.equal(root.get(e.getKey()), criteriaBuilder.parameter(type, key)));
					parameters.put(key, value);
				}

				if (!parameters.containsKey(key)) {
					searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.function("str", String.class, root.get(e.getKey()))), criteriaBuilder.parameter(String.class, key)));
					parameters.put(key, "%" + value.toLowerCase() + "%");
				}
			}
		);

		Predicate newRestrictions = null;

		if (!searchPredicates.isEmpty()) {
			newRestrictions = sortFilterPage.isFilterWithAND() ?
				criteriaBuilder.and(searchPredicates.toArray(PREDICATE_ARRAY)) :
				criteriaBuilder.or(searchPredicates.toArray(PREDICATE_ARRAY));
		}

		if (!exactPredicates.isEmpty()) {
			Predicate exactRestrictions = criteriaBuilder.and(exactPredicates.toArray(PREDICATE_ARRAY));
			newRestrictions = newRestrictions != null ? criteriaBuilder.and(newRestrictions, exactRestrictions) : exactRestrictions;
		}

		if (newRestrictions != null) {
			Predicate originalRestrictions = criteriaQuery.getRestriction();

			if (originalRestrictions != null) {
				criteriaQuery.where(criteriaBuilder.and(originalRestrictions, newRestrictions));
			} else {
				criteriaQuery.where(newRestrictions);
			}
		}

		// Create the "actual" JPA query from the above constructed criteria query
		// and add paging
		TypedQuery<T> typedQuery = entityManager
			.createQuery(criteriaQuery)
			.setFirstResult(sortFilterPage.getOffset())
			.setMaxResults(sortFilterPage.getLimit())
			.setHint("org.hibernate.cacheable", isCached ? "true" : "false")
			// TODO: Not just a global region but per query
			.setHint("org.hibernate.cacheRegion", "genericEntityServiceRegion");

		// Set parameters on the query. This includes both the provided parameters and the
		// ones for filtering that we generated here.
		parameters.entrySet().forEach(
			e -> typedQuery.setParameter(e.getKey(), e.getValue())
		);

		// Execute query
		List<T> entities = typedQuery.getResultList();

		// Troublesome Hibernate specific code to get total number of the results for the above query
		// without the paging
		Long count = -1l;
		if (getCount) {

			// Reset order by since count queries do not need sorting, it causes high memory consumption
			// or even temporary file generation in the database if the result set is rather large.
			javax.persistence.Query countQuery = entityManager.createQuery(criteriaQuery.orderBy());

			for (Map.Entry<String, Object> parameterEntry : parameters.entrySet()) {
				countQuery.setParameter(parameterEntry.getKey(), parameterEntry.getValue());
			}

			QueryTranslator translator = translateFromQuery(countQuery);
			javax.persistence.Query nativeQuery = entityManager.createNativeQuery(
				"select count(*) from (" +
					translator.getSQLString() +
				") x"
			); // not cacheable:  https://hibernate.atlassian.net/browse/HHH-9111 http://stackoverflow.com/q/25789176

			ParameterTranslations parameterTranslations = translator.getParameterTranslations();

			Query query = typedQuery.unwrap(Query.class);
			Map<String,TypedValue> namedParams = null;
			SessionImplementor session = null;
			try {

				// Yes, the following code is dreadful...

				Method method = AbstractQueryImpl.class.getDeclaredMethod("getNamedParams");
				method.setAccessible(true);
				Object map = method.invoke(query);
				namedParams = (Map<String, TypedValue>) map;


				method = AbstractQueryImpl.class.getDeclaredMethod("getSession");
				method.setAccessible(true);
				Object sessionObject = method.invoke(query);
				session = (SessionImplementor) sessionObject;
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
				e1.printStackTrace();
			}

			CapturingStatement capturingStatement = new CapturingStatement();
			for (Parameter<?> parameter : countQuery.getParameters()) {
				for (int position : parameterTranslations.getNamedParameterSqlLocations(parameter.getName())) {

					TypedValue typedValue = namedParams.get(parameter.getName());

					try {
						// Convert the parameter value
						typedValue.getType().nullSafeSet(capturingStatement, typedValue.getValue(), position + 1, session);
					} catch (HibernateException | SQLException e1) {
						e1.printStackTrace();
					}


					nativeQuery.setParameter(position + 1, capturingStatement.getObject());
				}
			}

			count = ((Number) nativeQuery.getSingleResult()).longValue();
		}

		return new PartialResultList<>(entities, sortFilterPage.getOffset(), count.intValue());
	}

}