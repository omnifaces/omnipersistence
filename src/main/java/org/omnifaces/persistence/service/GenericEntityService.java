package org.omnifaces.persistence.service;

import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.emptyMap;
import static java.util.regex.Pattern.quote;
import static javax.persistence.criteria.JoinType.LEFT;
import static org.omnifaces.utils.Lang.isEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Parameter;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.ast.ASTQueryTranslatorFactory;
import org.hibernate.hql.spi.ParameterTranslations;
import org.hibernate.hql.spi.QueryTranslator;
import org.hibernate.hql.spi.QueryTranslatorFactory;
import org.omnifaces.persistence.JPA;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.SortFilterPage;
import org.omnifaces.utils.collection.PartialResultList;

public class GenericEntityService {

	private EntityManager entityManager;
	private EntityManagerFactory entityManagerFactory;

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

	public static interface QueryBuilder<T> {
		Root<?> build(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Class<?> type);
	}

	public <T extends BaseEntity<? extends Number>> PartialResultList<T> getAllPagedAndSorted(Class<T> type, SortFilterPage sortFilterPage) {

		return getAllPagedAndSorted(type,
			(builder, query, tp) -> query.from(tp),
			emptyMap(),
			sortFilterPage, true
		);

	}

	public <T> PartialResultList<T> getAllPaged(Class<T> returnType, QueryBuilder<?> queryBuilder, Map<String, Object> parameters, SortFilterPage sortFilterPage, boolean getCount) {
		return getAllPagedAndSorted(returnType, queryBuilder, parameters, sortFilterPage.sortField(null), getCount);
	}

	private final static Predicate[] PREDICATE_ARRAY = new Predicate[0];

	public <T> PartialResultList<T> getAllPagedAndSorted(Class<T> returnType, QueryBuilder<?> queryBuilder,	Map<String, Object> parameters,	SortFilterPage sortFilterPage, boolean getCount) {

		// Create the two standard JPA objects used for criteria query construction
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(returnType);

		// Obtain main query from the passed-in builder
		Root<?> root = 	queryBuilder.build(criteriaBuilder, criteriaQuery, returnType);

		// Add sorting to query
		if (!isEmpty(sortFilterPage.getSortField())) {
			criteriaQuery.orderBy(
				"ASCENDING".equals(sortFilterPage.getSortOrder())?
					criteriaBuilder.asc(root.get(sortFilterPage.getSortField())) :
					criteriaBuilder.desc(root.get(sortFilterPage.getSortField()))
			);
		}

		List<Predicate> predicates = new ArrayList<Predicate>();

		// Add filtering to query
		sortFilterPage.getFilters().entrySet().forEach(
			e -> {

				// Add filtering condition to query (addition to where clause)
				predicates.add(
					criteriaBuilder.like(root.get(e.getKey()), criteriaBuilder.parameter(String.class, e.getKey() + "Search")));

				// Add the value to filter on as a parameter
				parameters.put(e.getKey() + "Search", "%" + e.getValue() + "%");
			}
		);

		if (!predicates.isEmpty()) {
			Predicate originalRestrictions = criteriaQuery.getRestriction();

			Predicate searchRestrictions = sortFilterPage.getFilterOperator().equals("or") ?
				criteriaBuilder.or(predicates.toArray(PREDICATE_ARRAY)) :
				criteriaBuilder.and(predicates.toArray(PREDICATE_ARRAY));

			criteriaQuery.where(criteriaBuilder.and(originalRestrictions, searchRestrictions));
		}

		// Create the "actual" JPA query from the above constructed criteria query
		// and add paging
		TypedQuery<T> typedQuery = entityManager
			.createQuery(criteriaQuery)
			.setFirstResult(sortFilterPage.getOffset())
			.setMaxResults(sortFilterPage.getLimit())
			.setHint("org.hibernate.cacheable", "true")
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
			javax.persistence.Query countQuery = entityManager
					.createQuery(criteriaQuery);

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

			for (Parameter<?> parameter : countQuery.getParameters()) {
				for (int position : parameterTranslations.getNamedParameterSqlLocations(parameter.getName())) {
					// Can't use countQuery.getParameter value due to bug in Hibernate
					nativeQuery.setParameter(position + 1, parameters.get(parameter.getName()));
				}
			}

			count = ((Number) nativeQuery.getSingleResult()).longValue();
		}

		return new PartialResultList<T>(entities, sortFilterPage.getOffset(), count.intValue());
	}

	public <T> Root<T> getRootQuery(CriteriaBuilder criteriaBuilder, CriteriaQuery<?> criteriaQuery, Class<T> type) {
		return criteriaQuery.from(type);
	}

	public static void sort(CriteriaBuilder builder, CriteriaQuery<?> query, Expression<?> sortExpression, String sortOrder) {
		query.orderBy(
			"ASCENDING".equals(sortOrder)?
				builder.asc(sortExpression) :
				builder.desc(sortExpression)
		);
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


}