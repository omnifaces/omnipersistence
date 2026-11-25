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
import java.util.function.Function;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.omnifaces.persistence.query.Page;

/**
 * Creates <code>path = number</code>.
 * <p>
 * Supports parsing from string values. The target number type is determined by the entity field type and supports {@link BigDecimal}, {@link BigInteger},
 * {@link Double}, {@link Float}, {@link Long}, {@link Integer}, {@link Short} and {@link Byte}, as well as their primitives. Any other target number type is
 * parsed as {@link Long}. An integral target type is parsed by numeric value, so <code>1.0</code> parses as <code>1</code>, and a value with a fraction or
 * beyond its range cannot be parsed, so that it matches nothing in a {@link Page}.
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

    /** The maximum integer digits of a {@link BigInteger} search value, so that a short value such as <code>1E999999999</code> cannot exhaust the CPU. */
    private static final int MAX_BIG_INTEGER_DIGITS = 1000;

    /** The maximum length of the string form of a search value which is not a number, so that parsing a long value cannot exhaust the CPU. */
    private static final int MAX_SEARCH_VALUE_LENGTH = MAX_BIG_INTEGER_DIGITS + 100;

    private static final Function<String, Number> LONG_PARSER = value -> new BigDecimal(value).longValueExact();
    private static final Function<String, Number> INT_PARSER = value -> new BigDecimal(value).intValueExact();
    private static final Function<String, Number> SHORT_PARSER = value -> new BigDecimal(value).shortValueExact();
    private static final Function<String, Number> BYTE_PARSER = value -> new BigDecimal(value).byteValueExact();

    private static final Map<Class<?>, Function<String, Number>> PARSERS = Map.ofEntries(
        entry(BigDecimal.class, BigDecimal::new),
        entry(BigInteger.class, Numeric::parseBigInteger),
        entry(Double.class, Double::valueOf),
        entry(double.class, Double::valueOf),
        entry(Float.class, Float::valueOf),
        entry(float.class, Float::valueOf),
        entry(Integer.class, INT_PARSER),
        entry(int.class, INT_PARSER),
        entry(Short.class, SHORT_PARSER),
        entry(short.class, SHORT_PARSER),
        entry(Byte.class, BYTE_PARSER),
        entry(byte.class, BYTE_PARSER)
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
        return modelValue != null && compare(modelValue instanceof Number number ? number : toBigDecimal(modelValue), getValue()) == 0;
    }

    /**
     * Compares given numbers by their numeric value, regardless of their type and scale. A NaN or an infinity compares as double.
     */
    static int compare(Number left, Number right) {
        return isFinite(left) && isFinite(right) ? toBigDecimal(left).compareTo(toBigDecimal(right)) : Double.compare(left.doubleValue(), right.doubleValue());
    }

    private static boolean isFinite(Number number) {
        return !(number instanceof Double || number instanceof Float) || Double.isFinite(number.doubleValue());
    }

    private static BigDecimal toBigDecimal(Object value) {
        return (BigDecimal) parseNumber(value, BigDecimal.class);
    }

    private static Number parseNumber(Object searchValue, Class<?> targetType) {
        if (targetType.isInstance(searchValue)) {
            return (Number) searchValue;
        }

        var string = searchValue.toString();

        if (!(searchValue instanceof Number) && string.length() > MAX_SEARCH_VALUE_LENGTH) {
            throw new IllegalArgumentException(string.substring(0, MAX_SEARCH_VALUE_LENGTH) + "...");
        }

        try {
            return PARSERS.getOrDefault(targetType, LONG_PARSER).apply(string);
        }
        catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(string, e);
        }
    }

    private static BigInteger parseBigInteger(String value) {
        var number = new BigDecimal(value).stripTrailingZeros();

        if (number.scale() > 0) {
            throw new ArithmeticException("Rounding necessary");
        }

        if (number.precision() - number.scale() > MAX_BIG_INTEGER_DIGITS) {
            throw new ArithmeticException("Overflow");
        }

        return number.toBigIntegerExact();
    }

}
