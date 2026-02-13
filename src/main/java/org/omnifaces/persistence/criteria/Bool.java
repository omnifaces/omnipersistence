/*
 * Copyright 2021 OmniFaces
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
 *
 * @author Bauke Scholtz
 */
public final class Bool extends Criteria<Boolean> {

    private Bool(Boolean value) {
        super(value);
    }

    public static Bool value(Boolean value) {
        return new Bool(value);
    }

    public static Bool parse(Object searchValue) {
        return new Bool(isTruthy(searchValue));
    }

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

    public static Boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        else if (value instanceof Number) {
            return ((Number) value).intValue() > 0;
        }
        else {
            String valueAsString = value.toString();

            try {
                return new BigDecimal(valueAsString).intValue() > 0;
            }
            catch (NumberFormatException ignore) {
                return Boolean.parseBoolean(valueAsString);
            }
        }
    }

}
