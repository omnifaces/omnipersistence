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

import static java.util.Map.entry;
import static org.omnifaces.utils.Lang.isOneOf;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Creates <code>path = number</code>.
 * <p>
 * Supports parsing from string values. The target number type is determined by the entity field type and supports {@link BigDecimal}, {@link BigInteger},
 * {@link Double}, {@link Float}, {@link Long}, {@link Integer}, {@link Short} and {@link Byte}, as well as their primitives. Any other target number type is
 * parsed as {@link Long}.
 * <p>
 * Usage examples:
 *
 * <pre>
 * criteria.put("age", Numeric.value(42)); // age = 42
 * criteria.put("age", Numeric.parse("42", type)); // age = 42 (parsed from string)
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Criteria
 */
public final class Numeric extends Criteria<Number> {

    private static final long serialVersionUID = 1L;

    private static final Map<Class<?>, Function<String, Number>> PARSERS = Map.ofEntries(
        entry(BigDecimal.class, BigDecimal::new),
        entry(BigInteger.class, BigInteger::new),
        entry(Double.class, Double::valueOf),
        entry(double.class, Double::valueOf),
        entry(Float.class, Float::valueOf),
        entry(float.class, Float::valueOf),
        entry(Integer.class, Integer::valueOf),
        entry(int.class, Integer::valueOf),
        entry(Short.class, Short::valueOf),
        entry(short.class, Short::valueOf),
        entry(Byte.class, Byte::valueOf),
        entry(byte.class, Byte::valueOf)
    );

    private Numeric(Number value) {
        super(value);
    }

    /**
     * Returns a new numeric criteria for the given value.
     *
     * @param value The numeric value.
     * @return A new numeric criteria.
     */
    public static Numeric value(Number value) {
        return new Numeric(value);
    }

    /**
     * Returns a new numeric criteria for the given search value, parsed against the given target numeric type.
     *
     * @param searchValue The search value to parse (usually a String).
     * @param targetType The target numeric class (e.g. Long.class, BigDecimal.class).
     * @return A new numeric criteria.
     * @throws IllegalArgumentException If the search value cannot be parsed to the target type.
     */
    public static Numeric parse(Object searchValue, Class<Number> targetType) {
        return new Numeric(parseNumber(searchValue, targetType));
    }

    /**
     * Returns true if the given type is a numeric type.
     *
     * @param type The type to check.
     * @return True if the given type is a numeric type.
     */
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
    public boolean applies(@Nullable Object modelValue) {
        return modelValue != null && Objects.equals(parseNumber(modelValue, getValue().getClass()), getValue());
    }

    private static Number parseNumber(Object searchValue, Class<?> targetType) {
        if (targetType.isInstance(searchValue)) {
            return (Number) searchValue;
        }

        try {
            return PARSERS.getOrDefault(targetType, Long::valueOf).apply(searchValue.toString());
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(searchValue.toString(), e);
        }
    }

}
