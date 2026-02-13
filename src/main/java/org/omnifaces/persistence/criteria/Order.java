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
package org.omnifaces.persistence.criteria;

import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;

/**
 * Creates comparison predicates: <code>path &lt;</code>, <code>path &lt;=</code>, <code>path &gt;</code>,
 * or <code>path &gt;=</code> a given value.
 * <p>
 * Usage examples:
 * <pre>
 * criteria.put("age", Order.greaterThan(18));                         // age &gt; 18
 * criteria.put("age", Order.greaterThanOrEqualTo(18));                // age &gt;= 18
 * criteria.put("created", Order.lessThan(LocalDate.of(2025, 1, 1)));  // created &lt; 2025-01-01
 * criteria.put("price", Order.lessThanOrEqualTo(99.99));              // price &lt;= 99.99
 * </pre>
 *
 * @param <T> The generic comparable type.
 * @author Bauke Scholtz
 * @see Criteria
 * @see Between
 */
public final class Order<T extends Comparable<T>> extends Criteria<T> {

    private enum Type {
        LT,
        LTE,
        GT,
        GTE
    }

    private Type type;

    private Order(Type type, T value) {
        super(value);
        this.type = type;
    }

    public static <T extends Comparable<T>> Order<T> lessThan(T value) {
        return new Order<>(Type.LT, value);
    }

    public static <T extends Comparable<T>> Order<T> lessThanOrEqualTo(T value) {
        return new Order<>(Type.LTE, value);
    }

    public static <T extends Comparable<T>> Order<T> greaterThanOrEqualTo(T value) {
        return new Order<>(Type.GTE, value);
    }

    public static <T extends Comparable<T>> Order<T> greaterThan(T value) {
        return new Order<>(Type.GT, value);
    }

    public boolean lessThan() {
        return type == Type.LT;
    }

    public boolean lessThanOrEqualTo() {
        return type == Type.LTE;
    }

    public boolean greaterThanOrEqualTo() {
        return type == Type.GTE;
    }

    public boolean greaterThan() {
        return type == Type.GT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        Expression<T> typedPath = (Expression<T>) path;
        ParameterExpression<T> parameter = parameterBuilder.create(getValue());

        if (lessThan()) {
            return criteriaBuilder.lessThan(typedPath, parameter);
        }
        else if (lessThanOrEqualTo()) {
            return criteriaBuilder.lessThanOrEqualTo(typedPath, parameter);
        }
        else if (greaterThanOrEqualTo()) {
            return criteriaBuilder.greaterThanOrEqualTo(typedPath, parameter);
        }
        else {
            return criteriaBuilder.greaterThan(typedPath, parameter);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean applies(Object value) {
        if (!(value instanceof Comparable)) {
            return false;
        }

        T typedValue = (T) value;

        if (greaterThan()) {
            return typedValue.compareTo(getValue()) > 0;
        }
        else if (greaterThanOrEqualTo()) {
            return typedValue.compareTo(getValue()) >= 0;
        }
        else if (lessThan()) {
            return typedValue.compareTo(getValue()) < 0;
        }
        else {
            return typedValue.compareTo(getValue()) <= 0;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object) && Objects.equals(type, ((Order<?>) object).type);
    }

    @Override
    public String toString() {
        return (type == Type.GT ? ">" : type == Type.GTE ? ">=" : type==Type.LT ? "<" : "<=") + " " + getValue();
    }

}
