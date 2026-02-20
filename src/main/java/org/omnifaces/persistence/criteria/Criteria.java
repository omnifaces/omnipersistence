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

import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * This is used by {@link Page} and {@link BaseEntityService#getPage(Page, boolean)}. It defines a set of criteria
 * which could be supplied as value of "required criteria" and "optional criteria" maps.
 * <p>
 * There are so far the following criteria:
 * <ul>
 * <li>{@link Not} - to negate any criteria value
 * <li>{@link Like} - to search a string value (case insensitive; supports starts with, ends with, and contains)
 * <li>{@link Order} - to perform "less than" or "greater than" searches
 * <li>{@link Between} - to perform "between" searches on any {@link Comparable}
 * <li>{@link Enumerated} - to parse value as enum (case insensitive name matching)
 * <li>{@link Numeric} - to parse value as number
 * <li>{@link Bool} - to parse value as boolean (supports truthy values like "1", "true", etc.)
 * <li>{@link IgnoreCase} - to perform case insensitive exact match
 * </ul>
 * <p>
 * Usage examples:
 * <pre>
 * Map&lt;String, Object&gt; criteria = new HashMap&lt;&gt;();
 * criteria.put("name", Like.contains("john"));          // LIKE '%john%'
 * criteria.put("email", IgnoreCase.value("FOO@BAR"));   // LOWER(email) = LOWER('FOO@BAR')
 * criteria.put("age", Order.greaterThan(18));            // age &gt; 18
 * criteria.put("status", Not.value("INACTIVE"));        // status &lt;&gt; 'INACTIVE'
 * criteria.put("created", Between.range(start, end));   // created BETWEEN start AND end
 * criteria.put("role", Enumerated.value(Role.ADMIN));   // role = 'ADMIN'
 * criteria.put("active", Bool.value(true));              // active IS TRUE
 * criteria.put("score", Numeric.value(42));              // score = 42
 * </pre>
 * <p>
 * You can create your own ones if you want to have more fine grained control over how criteria values are parsed and
 * turned into a predicate. Simply extend this class, implement {@link #build(Expression, CriteriaBuilder, ParameterBuilder)},
 * and optionally override {@link #applies(Object)}.
 * <p>
 * An elaborate use case can be found in <a href="https://github.com/omnifaces/optimusfaces">OptimusFaces</a> project.
 *
 * @param <T> The generic criteria value type.
 * @author Bauke Scholtz
 * @since 1.0
 * @see Page
 * @see BaseEntityService
 */
public abstract class Criteria<T> {

    private T value;

    /**
     * Create criteria based on given value.
     * @param value The criteria value.
     * @throws IllegalArgumentException When given criteria value cannot be reasonably parsed.
     */
    protected Criteria(T value) {
        this(value, false);
    }

    Criteria(T value, boolean nestable) {
        if (value instanceof Criteria && (!nestable || value.getClass() == getClass())) {
            throw new IllegalArgumentException("You cannot nest " + value + " in " + this);
        }

        if (!nestable && value == null) {
            throw new NullPointerException("value");
        }

        this.value = value;
    }

    /**
     * Returns a predicate for the criteria value. Below is an example implementation:
     * <pre>
     * return criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
     * </pre>
     * @param path Entity property path. You can use this to inspect the target entity property.
     * @param criteriaBuilder So you can build a predicate with a {@link ParameterExpression}.
     * @param parameterBuilder You must use this to create a {@link ParameterExpression} for the criteria value.
     * @return A predicate for the criteria value.
     */
    public abstract Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder);

    /**
     * Returns whether this criteria value would apply to the given model value. This must basically represent the "plain Java"
     * equivalent of the SQL behavior as achieved by {@link #build(Expression, CriteriaBuilder, ParameterBuilder)}.
     * @param modelValue The model value to test this criteria on.
     * @return Whether this criteria value would apply to the given model value.
     * @throws IllegalArgumentException When given model value cannot be reasonably parsed.
     * @throws UnsupportedOperationException When this method is not implemented yet.
     */
    public boolean applies(Object modelValue) {
        throw new UnsupportedOperationException("This method is not implemented yet.");
    }

    /**
     * Returns the criteria value.
     * @return The criteria value.
     */
    public T getValue() {
        return value;
    }

    /**
     * Unwraps the criteria value from given object which could possibly represent a {@link Criteria}.
     * @param possibleCriteria Any object which could possibly represent a {@link Criteria}.
     * @return The unwrapped criteria value when given object actually represents a {@link Criteria}, else the original value unmodified.
     */
    public static Object unwrap(Object possibleCriteria) {
        Object value = possibleCriteria;

        while (value instanceof Criteria) {
            value = ((Criteria<?>) value).getValue();
        }

        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), value);
    }

    @Override
    public boolean equals(Object object) {
        return getClass().isInstance(object) && (object == this || (Objects.equals(value, ((Criteria<?>) object).value)));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName().toUpperCase() + "(" + getValue() + ")";
    }

    /**
     * Factory for creating {@link ParameterExpression} instances in {@link Criteria#build(Expression, CriteriaBuilder, ParameterBuilder)}.
     * The implementation is responsible for generating unique parameter names and tracking parameter values.
     */
    @FunctionalInterface
    public interface ParameterBuilder {

        /**
         * Creates a new {@link ParameterExpression} for the given value.
         * @param <T> The generic parameter type.
         * @param value The parameter value.
         * @return A new parameter expression.
         */
        <T> ParameterExpression<T> create(Object value);
    }

}
