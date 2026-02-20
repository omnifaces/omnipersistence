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

import static org.omnifaces.utils.Lang.isOneOf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Creates <code>path = number</code>.
 * <p>
 * Supports parsing from string values. The target number type is determined by the entity field type
 * and supports {@link java.math.BigDecimal}, {@link java.math.BigInteger}, {@link Integer} and {@link Long}.
 * <p>
 * Usage examples:
 * <pre>
 * criteria.put("age", Numeric.value(42));             // age = 42
 * criteria.put("age", Numeric.parse("42", type));     // age = 42 (parsed from string)
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Criteria
 */
public final class Numeric extends Criteria<Number> {

    private Numeric(Number value) {
        super(value);
    }

    public static Numeric value(Number value) {
        return new Numeric(value);
    }

    public static Numeric parse(Object searchValue, Class<Number> targetType) {
        return new Numeric(parseNumber(searchValue, targetType));
    }

    public static boolean is(Class<?> type) {
        return isOneOf(type, byte.class, short.class, int.class, long.class, float.class, double.class) || Number.class.isAssignableFrom(type);
    }

    @Override
    public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        var targetType = path.getJavaType();
        var value = (targetType != null && Numeric.is(targetType)) ? parseNumber(getValue(), targetType) : getValue();
        return criteriaBuilder.equal(path, parameterBuilder.create(value));
    }

    @Override
    public boolean applies(Object modelValue) {
        return modelValue != null && Objects.equals(parseNumber(modelValue, getValue().getClass()), getValue());
    }

    private static Number parseNumber(Object searchValue, Class<?> targetType) throws NumberFormatException {
        try {
            if (targetType.isInstance(searchValue)) {
                return (Number) searchValue;
            }
            else if (BigDecimal.class.isAssignableFrom(targetType)) {
                return searchValue instanceof BigDecimal n ? n : new BigDecimal(searchValue.toString());
            }
            else if (BigInteger.class.isAssignableFrom(targetType)) {
                return searchValue instanceof BigInteger n ? n : new BigInteger(searchValue.toString());
            }
            else if (Integer.class.isAssignableFrom(targetType) || int.class == targetType) {
                return searchValue instanceof Integer n ? n : Integer.valueOf(searchValue.toString());
            }
            else {
                return searchValue instanceof Long n ? n : Long.valueOf(searchValue.toString());
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(searchValue.toString(), e);
        }
    }

}
