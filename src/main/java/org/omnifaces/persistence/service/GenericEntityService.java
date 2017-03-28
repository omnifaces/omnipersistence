package org.omnifaces.persistence.service;

import static java.util.Arrays.stream;
import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.singleton;
import static java.util.regex.Pattern.quote;
import static javax.persistence.criteria.JoinType.LEFT;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.Lang.isOneOf;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

	private Consumer<EntityManager> setupHandler;
	private Consumer<EntityManager> teardownHandler;

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

	public void setSetupHandler(Consumer<EntityManager> setupHandler) {
		this.setupHandler = setupHandler;
	}

	public void setTeardownHandler(Consumer<EntityManager> teardownHandler) {
		this.teardownHandler = teardownHandler;
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

	public <T> PartialResultList<T> getAllPagedAndSortedByType(Class<T> resultType, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType,

			(builder, query, tp) -> query.from(resultType),

			Collections.emptyMap(),

			sortFilterPage, getCount
		);
	}

	public <T> PartialResultList<T> getAllPaged(Class<T> resultType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType, queryBuilder, parameters, sortFilterPage, getCount, true, true);
	}

	public <T> PartialResultList<T> getAllPagedUncached(Class<T> resultType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType, queryBuilder, parameters, sortFilterPage, getCount, true, false);
	}

	public <T extends BaseEntity<? extends Number>> PartialResultList<T> getAllPagedAndSorted(Class<T> resultType, SortFilterPage sortFilterPage) {
		return getAllPagedAndSorted(resultType, (builder, query, type) -> query.from(type), new HashMap<>(), sortFilterPage, true, false, true);
	}

	public <T extends BaseEntity<? extends Number>> PartialResultList<T> getAllPagedAndSorted(Class<T> resultType, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType, (builder, query, type) -> query.from(type), new HashMap<>(), sortFilterPage, getCount, false, true);
	}

	public <T> PartialResultList<T> getAllPagedAndSorted(Class<T> resultType, QueryBuilder<?> queryBuilder, SortFilterPage sortFilterPage) {
		return getAllPagedAndSorted(resultType, queryBuilder, new HashMap<>(), sortFilterPage, true, false, true);
	}

	public <T> PartialResultList<T> getAllPagedAndSorted(Class<T> resultType, QueryBuilder<?> queryBuilder, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType, queryBuilder, new HashMap<>(), sortFilterPage, getCount, false, true);
	}

	public <T> PartialResultList<T> getAllPagedAndSorted(Class<T> resultType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType, queryBuilder, parameters, sortFilterPage, getCount, false, true);
	}

	public <T> PartialResultList<T> getAllPagedAndSortedUncached(Class<T> resultType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(resultType, queryBuilder, parameters, sortFilterPage, getCount, false, false);
	}

	public <T> Root<T> getRootQuery(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Class<T> entityType) {
		return criteriaQuery.from(entityType);
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
	private <T> PartialResultList<T> getAllPagedAndSorted(Class<T> resultType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount, boolean isSorted, boolean isCached) {

		if (setupHandler != null) {
			setupHandler.accept(entityManager);
		}

		try {
			// Create the two standard JPA objects used for criteria query construction
			CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
			CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(resultType);

			// Obtain main query from the passed-in builder
			Root<?> root = queryBuilder.build(criteriaBuilder, criteriaQuery, resultType);

			// Add sorting to query if necessary
			if (!isSorted && !isEmpty(sortFilterPage.getSortField())) {
				criteriaQuery.orderBy(
						"ASCENDING".equals(sortFilterPage.getSortOrder()) ?
						criteriaBuilder.asc(root.get(sortFilterPage.getSortField())) :
						criteriaBuilder.desc(root.get(sortFilterPage.getSortField()))
				);
			}

			Map<String, Object> searchParameters = new HashMap<>(parameters);
			List<Predicate> searchPredicates = new ArrayList<>();
			List<Predicate> exactPredicates = new ArrayList<>();

			// Add filtering to query
			sortFilterPage.getFilterValues().entrySet().forEach(
				e -> {
					String key = e.getKey();
					Object value = e.getValue();
					String searchKey = key + "Search";
					String searchValue = value.toString();
					Class<?> type;

					try {
						type = root.get(key).getJavaType();
					}
					catch (IllegalArgumentException ignore) {
						return; // Likely custom search key referring non-existent property.
					}

					if (type.isEnum()) {
						try {
							boolean negated = searchValue.startsWith("!");

							if (negated) {
								searchValue = searchValue.substring(1);
							}

							Enum enumValue = Enum.valueOf((Class<Enum>) type, searchValue.toUpperCase());
							searchParameters.put(searchKey, enumValue);

							if (negated) {
								exactPredicates.add(criteriaBuilder.notEqual(root.get(key), criteriaBuilder.parameter(type, searchKey)));
							} else {
								exactPredicates.add(criteriaBuilder.equal(root.get(key), criteriaBuilder.parameter(type, searchKey)));
							}
						}
						catch (IllegalArgumentException ignore) {
							return; // Likely custom search value referring non-existent enum value.
						}
					}
					else if (Boolean.class.isAssignableFrom(type)) {
						exactPredicates.add(criteriaBuilder.equal(root.get(key), criteriaBuilder.parameter(type, searchKey)));
						searchParameters.put(searchKey, Boolean.valueOf(searchValue));
					}
					else if (Long.class.isAssignableFrom(type)) {
						if (isOneOf(searchValue, "true", "false")) {
							// If value happens to represent a boolean, use true to match everything > 0 and false to match everything < 0 (i.e. exclude everything positive).
							Path<Long> path = root.get(key);
							ParameterExpression<Long> parameter = criteriaBuilder.parameter(Long.class, searchKey);
							exactPredicates.add("true".equals(searchValue) ? criteriaBuilder.gt(path, parameter) : criteriaBuilder.le(path, parameter));
							searchParameters.put(searchKey, 0L);
						}
						else if (key.matches("(id|.+Id$)") && sortFilterPage.getFilterValues().size() == 1) {
							// If key happens to represent an ID, and this is the only search field, then assume exact match instead of search match.
							exactPredicates.add(criteriaBuilder.equal(root.get(key), criteriaBuilder.parameter(type, searchKey)));
							searchParameters.put(searchKey, Long.valueOf(searchValue));
						}
						else if (value instanceof Long[]) {
							// LongRange
							exactPredicates.add(criteriaBuilder.between(root.get(key), criteriaBuilder.parameter(Long.class, "min_" + searchKey), criteriaBuilder.parameter(Long.class, "max_" + searchKey)));
							searchParameters.put("min_" + searchKey, ((Long[]) value)[0]);
							searchParameters.put("max_" + searchKey, ((Long[]) value)[1]);
							searchParameters.put(searchKey, null);
						}
						else {
							searchParameters.put(searchKey, null);
						}
					}
					else if (Collection.class.isAssignableFrom(type)) {
						if (value instanceof Object[]) {
							Object[] values = (Object[]) value;

							if (values.length > 0) {
								List<Expression> in = new ArrayList<>(values.length);

								for (Object item : values) {
									String name = searchKey + item;
									in.add(criteriaBuilder.parameter(value.getClass().getComponentType(), name));
									searchParameters.put(name, item);
								}

								exactPredicates.add(root.join(key).in(in.toArray(new Expression[in.size()])));
							}
						}

						searchParameters.put(searchKey, null);
					}
					else if (!sortFilterPage.getFilterableFields().contains(key)) {
						exactPredicates.add(criteriaBuilder.equal(root.get(key), criteriaBuilder.parameter(type, searchKey)));
						searchParameters.put(searchKey, searchValue);
					}

					if (!searchParameters.containsKey(searchKey)) {
						searchPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.function("str", String.class, root.get(key))), criteriaBuilder.parameter(String.class, searchKey)));
						searchParameters.put(searchKey, "%" + searchValue.toLowerCase() + "%");
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
				newRestrictions = newRestrictions != null ? criteriaBuilder.and(newRestrictions, exactRestrictions) :
				                  exactRestrictions;
			}

			if (newRestrictions != null) {
				Predicate originalRestrictions = criteriaQuery.getRestriction();

				if (originalRestrictions != null) {
					criteriaQuery.where(criteriaBuilder.and(originalRestrictions, newRestrictions));
				}
				else {
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
			searchParameters.values().removeAll(singleton(null));
			searchParameters.entrySet().forEach(
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

				for (Map.Entry<String, Object> parameterEntry : searchParameters.entrySet()) {
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
				Map<String, TypedValue> namedParams = null;
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
				}
				catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
					throw new IllegalStateException(e1);
				}

				CapturingStatement capturingStatement = new CapturingStatement();
				for (Parameter<?> parameter : countQuery.getParameters()) {
					for (int position : parameterTranslations.getNamedParameterSqlLocations(parameter.getName())) {

						TypedValue typedValue = namedParams.get(parameter.getName());

						try {
							// Convert the parameter value
							typedValue.getType().nullSafeSet(capturingStatement, typedValue.getValue(), position + 1, session);
						}
						catch (HibernateException | SQLException e1) {
							throw new IllegalArgumentException(e1);
						}

						nativeQuery.setParameter(position + 1, capturingStatement.getObject());
					}
				}

				count = ((Number) nativeQuery.getSingleResult()).longValue();
			}

			return new PartialResultList<>(entities, sortFilterPage.getOffset(), count.intValue());
		}
		finally {
			if (teardownHandler != null) {
				teardownHandler.accept(entityManager);
			}
		}
	}

}