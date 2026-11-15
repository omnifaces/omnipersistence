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
package org.omnifaces.persistence.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.JPA;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * Unit tests for {@link JPA}. {@link JPA#lower(CriteriaBuilder, Expression)} must apply the SQL <code>LOWER</code> function via
 * {@link CriteriaBuilder#function(String, Class, Expression...)}, because OpenJPA cannot render {@link CriteriaBuilder#lower(Expression)} on an element of an
 * <code>@ElementCollection</code> in a correlated subquery. {@link JPA#castAsString(CriteriaBuilder, Expression)}, {@link JPA#escapeLike(String)} and
 * {@link JPA#escapeLikePattern(String)} depend on the provider and database of the current {@link BaseEntityService}, so they must reject an invocation without
 * one.
 */
class JPATest {

    @Test
    void lowerAppliesSqlLowerFunction() {
        var invocation = new AtomicReference<Object[]>();
        var result = proxy(Expression.class);
        var criteriaBuilder = (CriteriaBuilder) Proxy.newProxyInstance(
            JPATest.class.getClassLoader(), new Class<?>[] { CriteriaBuilder.class }, (proxy, method, args) -> {
                if (!"function".equals(method.getName())) {
                    throw new UnsupportedOperationException(method.getName());
                }

                invocation.set(args);
                return result;
            }
        );
        Expression<String> expression = proxy(Expression.class);

        assertSame(result, JPA.lower(criteriaBuilder, expression));
        assertArrayEquals(new Object[] { "LOWER", String.class, new Expression<?>[] { expression } }, invocation.get());
    }

    @Test
    void castAsStringAndEscapeLikeAndEscapeLikePatternRequireCurrentBaseEntityService() {
        assertThrows(IllegalStateException.class, () -> JPA.castAsString(proxy(CriteriaBuilder.class), proxy(Expression.class)), "castAsString");
        assertThrows(IllegalStateException.class, () -> JPA.escapeLike("value"), "escapeLike");
        assertThrows(IllegalStateException.class, () -> JPA.escapeLikePattern("value"), "escapeLikePattern");
    }

    @Test
    void singleResultHelpersMapNoResultToNullAndRethrowNonUniqueResult() {
        assertEquals("a", JPA.getSingleResultOrNull(typedQuery(List.of("a"), null)));
        assertEquals("a", JPA.<String>getSingleResultOrNull(query(List.of("a"), null)));
        assertNull(JPA.getSingleResultOrNull(typedQuery(List.of(), null)));
        assertNull(JPA.getSingleResultOrNull(query(List.of(), null)));
        assertEquals(Optional.of("a"), JPA.findSingleResult(typedQuery(List.of("a"), null)));
        assertEquals(Optional.of("a"), JPA.findSingleResult(query(List.of("a"), null)));
        assertEquals(Optional.empty(), JPA.findSingleResult(typedQuery(List.of(), null)));
        assertEquals(Optional.empty(), JPA.findSingleResult(query(List.of(), null)));
        assertThrows(NonUniqueResultException.class, () -> JPA.getSingleResultOrNull(typedQuery(List.of("a", "b"), null)));
        assertThrows(NonUniqueResultException.class, () -> JPA.getSingleResultOrNull(query(List.of("a", "b"), null)));
    }

    @Test
    void firstResultHelpersLimitQueryToOneResult() {
        var typedMaxResults = new AtomicReference<Integer>();
        assertEquals("a", JPA.getFirstResultOrNull(typedQuery(List.of("a", "b"), typedMaxResults)));
        assertEquals(1, typedMaxResults.get(), "Max results of typed query");
        assertNull(JPA.getFirstResultOrNull(typedQuery(List.of(), null)));

        var maxResults = new AtomicReference<Integer>();
        assertEquals("a", JPA.<String>getFirstResultOrNull(query(List.of("a", "b"), maxResults)));
        assertEquals(1, maxResults.get(), "Max results of query");
        assertNull(JPA.getFirstResultOrNull(query(List.of(), null)));
    }

    @Test
    void getResultMapMapsResultListByKeyAndValue() {
        TypedQuery<String> query = typedQuery(List.of("a", "bb"), null);
        assertEquals(Map.of(1, "a", 2, "bb"), JPA.getResultMap(query, String::length));
        assertEquals(Map.of("a", 1, "bb", 2), JPA.getResultMap(query, Function.identity(), String::length));
    }

    @Test
    void getValidationModeParsesPropertyCaseInsensitivelyAndDefaultsToAuto() {
        assertEquals(ValidationMode.CALLBACK, JPA.getValidationMode(entityManager(Map.of(JPA.PROPERTY_VALIDATION_MODE, "callback"))));
        assertEquals(ValidationMode.AUTO, JPA.getValidationMode(entityManager(Map.of())));
    }

    @SuppressWarnings("unchecked")
    private static <T> TypedQuery<T> typedQuery(List<T> results, AtomicReference<Integer> maxResults) {
        return (TypedQuery<T>) proxy(TypedQuery.class, results, maxResults);
    }

    private static Query query(List<?> results, AtomicReference<Integer> maxResults) {
        return (Query) proxy(Query.class, results, maxResults);
    }

    /**
     * Returns a query of given type whose single result and result list are given results, limited by the max results it records in given reference, if any.
     */
    private static Object proxy(Class<?> type, List<?> results, AtomicReference<Integer> maxResults) {
        var limit = new AtomicReference<Integer>(results.size());
        return Proxy.newProxyInstance(JPATest.class.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getSingleResult" :
                    if (results.isEmpty()) {
                        throw new NoResultException();
                    }
                    else if (results.size() > 1) {
                        throw new NonUniqueResultException();
                    }

                    return results.get(0);
                case "getResultList" :
                    return results.subList(0, Math.min(limit.get(), results.size()));
                case "setMaxResults" :
                    limit.set((Integer) args[0]);

                    if (maxResults != null) {
                        maxResults.set((Integer) args[0]);
                    }

                    return proxy;
                default :
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static EntityManager entityManager(Map<String, Object> properties) {
        var entityManagerFactory = (EntityManagerFactory) Proxy.newProxyInstance(
            JPATest.class.getClassLoader(), new Class<?>[] { EntityManagerFactory.class }, (proxy, method, args) -> {
                if (!"getProperties".equals(method.getName())) {
                    throw new UnsupportedOperationException(method.getName());
                }

                return properties;
            }
        );
        return (EntityManager) Proxy.newProxyInstance(JPATest.class.getClassLoader(), new Class<?>[] { EntityManager.class }, (proxy, method, args) -> {
            if (!"getEntityManagerFactory".equals(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }

            return entityManagerFactory;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type) {
        return (T) Proxy.newProxyInstance(JPATest.class.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

}
