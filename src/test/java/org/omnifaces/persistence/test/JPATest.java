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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type) {
        return (T) Proxy.newProxyInstance(JPATest.class.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

}
