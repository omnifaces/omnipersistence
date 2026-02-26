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

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Creates <code>path IS (NOT) TRUE</code>.
 * <p>
 * Supports truthy value parsing: besides actual {@link Boolean} values, it also accepts numeric values (where &gt; 0
 * is considered truthy) and string values (parsed via {@link Boolean#parseBoolean(String)} or as number).
 * <p>
 * Usage examples:
 * <pre>
 * criteria.put("active", Bool.value(true));   // active IS TRUE
 * criteria.put("active", Bool.value(false));  // active IS NOT TRUE
 * criteria.put("active", Bool.parse("1"));    // active IS TRUE (truthy)
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Criteria
 */
public final class Bool extends Criteria<Boolean> {

    private Bool(Boolean value) {
        super(value);
    }

    /**
     * Returns a new boolean criteria for the given value.
     * @param value The boolean value.
     * @return A new boolean criteria.
     */
    public static Bool value(Boolean value) {
        return new Bool(value);
    }

    /**
     * Returns a new boolean criteria for the given search value, parsed as truthy.
     * @param searchValue The search value to parse.
     * @return A new boolean criteria.
     * @see #isTruthy(Object)
     */
    public static Bool parse(Object searchValue) {
        return new Bool(isTruthy(searchValue));
    }

    /**
     * Returns true if the given type is a boolean.
     * @param type The type to check.
     * @return True if the given type is a boolean.
     */
    public static boolean is(Class<?> type) {
        return type == boolean.class || Boolean.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        Predicate predicate = criteriaBuilder.isTrue((Expression<Boolean>) path);
        return getValue() ? predicate : criteriaBuilder.not(predicate);
    }

    @Override
    public boolean applies(Object modelValue) {
        return Objects.equals(isTruthy(modelValue), getValue());
    }

    /**
     * Returns whether the given value is considered "truthy".
     * <p>
     * A value is truthy if:
     * <ul>
     * <li>It is a {@link Boolean} and is {@code true}.</li>
     * <li>It is a {@link Number} and is greater than 0.</li>
     * <li>It is a {@link String} representing a number greater than 0.</li>
     * <li>It is a {@link String} that {@link Boolean#parseBoolean(String)} evaluates to {@code true}.</li>
     * </ul>
     * @param value The value to check.
     * @return True if the value is truthy, false otherwise.
     */
    public static Boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        else if (value instanceof Number) {
            return ((Number) value).doubleValue() > 0;
        }
        else {
            String valueAsString = value.toString();

            try {
                return new BigDecimal(valueAsString).doubleValue() > 0;
            }
            catch (NumberFormatException ignore) {
                return Boolean.parseBoolean(valueAsString);
            }
        }
    }

}
