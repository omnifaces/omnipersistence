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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.JPA;

/**
 * Unit tests for {@link JPA}. {@link JPA#lower(CriteriaBuilder, Expression)} must apply the SQL <code>LOWER</code> function via
 * {@link CriteriaBuilder#function(String, Class, Expression...)}, because OpenJPA cannot render {@link CriteriaBuilder#lower(Expression)} on an element of an
 * <code>@ElementCollection</code> in a correlated subquery.
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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<?> type) {
        return (T) Proxy.newProxyInstance(JPATest.class.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

}
