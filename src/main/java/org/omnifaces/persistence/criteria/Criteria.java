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

import static java.text.Normalizer.Form.NFC;
import static java.text.Normalizer.Form.NFD;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toSet;
import static org.omnifaces.persistence.JPA.isEnumeratedByName;
import static org.omnifaces.persistence.JPA.matchNothing;
import static org.omnifaces.utils.stream.Streams.stream;

import java.io.Serializable;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.omnifaces.persistence.query.Page;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * This is used by {@link Page} and {@link BaseEntityService#getPage(Page, boolean)}. It defines a set of criteria which could be supplied as value of "required
 * criteria" and "optional criteria" maps.
 * <p>
 * There are so far the following criteria:
 * <ul>
 * <li>{@link Not} - to negate any criteria value other than {@link Not}
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
 *
 * <pre>
 * Map&lt;String, Object&gt; criteria = new HashMap&lt;&gt;();
 * criteria.put("name", Like.contains("john")); // LIKE '%john%'
 * criteria.put("email", IgnoreCase.value("FOO@BAR")); // LOWER(email) = LOWER('FOO@BAR')
 * criteria.put("age", Order.greaterThan(18)); // age &gt; 18
 * criteria.put("status", Not.value("INACTIVE")); // status &lt;&gt; 'INACTIVE' OR status IS NULL
 * criteria.put("created", Between.range(start, end)); // created BETWEEN start AND end
 * criteria.put("role", Enumerated.value(Role.ADMIN)); // role = 'ADMIN'
 * criteria.put("active", Bool.value(true)); // active = TRUE
 * criteria.put("score", Numeric.value(42)); // score = 42
 * criteria.put("deleted", null); // deleted IS NULL
 * criteria.put("email", Not.value(null)); // email IS NOT NULL
 * </pre>
 * <p>
 * You can create your own ones if you want to have more fine grained control over how criteria values are parsed and turned into a predicate. Simply extend
 * this class, implement {@link #build(Expression, CriteriaBuilder, ParameterBuilder)}, and optionally override {@link #applies(Object)}.
 * <p>
 * A criteria value may by default not be <code>null</code>; the constructor then throws {@link NullPointerException}. The exceptions are {@link Not}, as
 * <code>Not.value(null)</code> matches <code>path IS NOT NULL</code>, and an {@link Enumerated} parsed from a criteria which matches no constant. To match
 * <code>path IS NULL</code>, put a plain <code>null</code> value in the criteria map.
 * <p>
 * An elaborate use case can be found in <a href="https://github.com/omnifaces/optimusfaces">OptimusFaces</a> project.
 *
 * @param <T> The generic criteria value type.
 * @author Bauke Scholtz
 * @since 1.0
 * @see Page
 * @see BaseEntityService
 */
public abstract class Criteria<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String ERROR_INCOMPARABLE = "Model value '%s' of type %s cannot be compared with criteria value '%s' of type %s.";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "HH:mm:ss";
    private static final DateTimeFormatter LOCAL_TIME = ofPattern(TIME_PATTERN);
    private static final DateTimeFormatter LOCAL_DATE_TIME = ofPattern(DATE_PATTERN + " " + TIME_PATTERN);
    private static final DateTimeFormatter OFFSET_TIME = withOffset(LOCAL_TIME);
    private static final DateTimeFormatter OFFSET_DATE_TIME = withOffset(LOCAL_DATE_TIME);
    private static final Pattern ACCENTS = Pattern.compile("(?:[\\p{InCombiningDiacriticalMarks}&&[^\\u0306]]|(?<![\\u0418\\u0438])\\u0306)+");

    /** The criteria value. */
    private final T value;

    private transient String foldedValue;

    /**
     * Create criteria based on given value.
     *
     * @param value The criteria value, may not be <code>null</code>.
     * @throws NullPointerException When given criteria value is <code>null</code>.
     * @throws IllegalArgumentException When given criteria value cannot be reasonably parsed.
     */
    protected Criteria(T value) {
        this(value, false, false);
    }

    Criteria(@Nullable T value, boolean nestable, boolean nullable) {
        if (value instanceof Criteria && (!nestable || value.getClass() == getClass())) {
            throw new IllegalArgumentException("You cannot nest " + value + " in " + this);
        }

        if (!nullable && value == null) {
            throw new NullPointerException("value");
        }

        this.value = value;
    }

    /**
     * Returns a predicate for the criteria value. Below is an example implementation:
     *
     * <pre>
     * return criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
     * </pre>
     *
     * @param path Entity property path. You can use this to inspect the target entity property.
     * @param criteriaBuilder So you can build a predicate with a {@link ParameterExpression}.
     * @param parameterBuilder You must use this to create a {@link ParameterExpression} for the criteria value.
     * @return A predicate for the criteria value, or <code>null</code> to impose no restriction.
     */
    @Nullable
    public abstract Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder);

    /**
     * Returns whether this criteria value would apply to the given model value. This must basically represent the "plain Java" equivalent of the SQL behavior
     * as achieved by {@link #build(Expression, CriteriaBuilder, ParameterBuilder)}.
     *
     * @param modelValue The model value to test this criteria on, may be <code>null</code>.
     * @return Whether this criteria value would apply to the given model value.
     * @throws IllegalArgumentException When given model value cannot be reasonably parsed.
     * @throws UnsupportedOperationException When this method is not implemented yet.
     */
    public boolean applies(@Nullable Object modelValue) {
        throw new UnsupportedOperationException("This method is not implemented yet.");
    }

    private static DateTimeFormatter withOffset(DateTimeFormatter formatter) {
        return new DateTimeFormatterBuilder().append(formatter).appendOffset("+HH:mm", "+00").toFormatter();
    }

    /**
     * Returns whether given path is an enum whose column does not hold the constant name, as determined by <code>JPA#isEnumeratedByName(Path)</code>, such as
     * an ordinal or a converted enum. A string based predicate cannot match such a path by constant name in SQL, so it has to be built by
     * {@link #buildByMatchingEnumConstants(Expression, CriteriaBuilder, ParameterBuilder)}.
     */
    static boolean isEnumNotStoredByName(Expression<?> path) {
        return path.getJavaType().isEnum() && path instanceof Path<?> p && !isEnumeratedByName(p);
    }

    /**
     * Returns <code>path IN (constants)</code> where the constants are those enum constants of the type of given path which this criteria applies to.
     */
    Predicate buildByMatchingEnumConstants(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        Set<?> matches = stream(path.getJavaType().getEnumConstants()).filter(this::applies).collect(toSet());
        return matches.isEmpty() ? matchNothing(criteriaBuilder, parameterBuilder) : path.in(parameterBuilder.create(matches));
    }

    /**
     * Returns the given string lowercased in {@link Locale#ROOT} and without accents, as {@link Like#applies(Object)} and {@link IgnoreCase#applies(Object)}
     * compare it. An accent is a combining diacritical mark (U+0300 to U+036F) after Unicode canonical decomposition. A letter such as <code>\u00f8</code> or
     * <code>\u0142</code>, which does not decompose, keeps its accent, and so does <code>\u0439</code>, which accent-insensitive collations treat as a letter
     * of its own. The vowel signs of a script such as Devanagari are no accents. The result is recomposed, so that a Hangul syllable stays one character.
     */
    static String fold(String string) {
        var withoutAccents = ACCENTS.matcher(Normalizer.normalize(string, NFD)).replaceAll("");
        return Normalizer.normalize(withoutAccents, NFC).toLowerCase(ROOT);
    }

    /**
     * Returns the value of this criteria as folded by {@link #fold(String)}, computed once.
     */
    String getFoldedValue() {
        if (foldedValue == null) {
            foldedValue = fold(String.valueOf(value));
        }

        return foldedValue;
    }

    /**
     * Returns the string form of the given model value as matched by a string based predicate. A temporal value has the date as <code>yyyy-MM-dd</code> and the
     * time as <code>HH:mm:ss</code>, with a space in between, followed by the offset, if any, as <code>+01</code> or <code>+05:30</code>. Whether the database
     * includes the offset and fractional seconds depends on the database.
     */
    static String asString(Object modelValue) {
        if (modelValue instanceof LocalTime localTime) {
            return LOCAL_TIME.format(localTime);
        }
        else if (modelValue instanceof LocalDateTime localDateTime) {
            return LOCAL_DATE_TIME.format(localDateTime);
        }
        else if (modelValue instanceof OffsetTime offsetTime) {
            return OFFSET_TIME.format(offsetTime);
        }
        else if (modelValue instanceof OffsetDateTime offsetDateTime) {
            return OFFSET_DATE_TIME.format(offsetDateTime);
        }
        else {
            return modelValue.toString();
        }
    }

    /**
     * Returns the exception for a model value which cannot be compared with the given criteria value, because their types differ.
     */
    static IllegalArgumentException incomparable(Object modelValue, Object criteriaValue, ClassCastException cause) {
        return new IllegalArgumentException(
            String.format(
                ERROR_INCOMPARABLE, modelValue, modelValue.getClass().getName(), criteriaValue,
                criteriaValue.getClass().getName()
            ), cause
        );
    }

    /**
     * Returns the criteria value.
     *
     * @return The criteria value.
     */
    public T getValue() {
        return value;
    }

    /**
     * Returns whether given value holds multiple values, i.e. is an {@link Iterable} or an array of objects, <code>int</code>, <code>long</code> or
     * <code>double</code>.
     *
     * @param value Any value.
     * @return Whether given value holds multiple values.
     * @since 2.0
     */
    public static boolean isMultiValued(@Nullable Object value) {
        return value instanceof Iterable || value instanceof Object[] || value instanceof int[] || value instanceof long[] || value instanceof double[];
    }

    /**
     * Unwraps the criteria value from given object which could possibly represent a {@link Criteria}.
     *
     * @param possibleCriteria Any object which could possibly represent a {@link Criteria}.
     * @return The unwrapped criteria value when given object actually represents a {@link Criteria}, else the original value unmodified.
     */
    public static Object unwrap(Object possibleCriteria) {
        Object value = possibleCriteria;

        while (value instanceof Criteria<?> criteria) {
            value = criteria.getValue();
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
     * Factory for creating {@link ParameterExpression} instances in {@link Criteria#build(Expression, CriteriaBuilder, ParameterBuilder)}. The implementation
     * is responsible for generating unique parameter names and tracking parameter values.
     */
    @FunctionalInterface
    public interface ParameterBuilder {

        /**
         * Creates a new {@link ParameterExpression} for the given value.
         *
         * @param <T> The generic parameter type.
         * @param value The parameter value, may be <code>null</code>.
         * @return A new parameter expression.
         */
        <T> ParameterExpression<T> create(@Nullable Object value);

    }

}
