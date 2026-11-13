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

import static java.util.stream.Collectors.toSet;
import static org.omnifaces.persistence.JPA.LIKE_ESCAPE;
import static org.omnifaces.persistence.JPA.castAsString;
import static org.omnifaces.persistence.JPA.escapeLike;
import static org.omnifaces.persistence.JPA.escapeLikePattern;
import static org.omnifaces.persistence.JPA.isEnumeratedByOrdinal;
import static org.omnifaces.persistence.JPA.lower;
import static org.omnifaces.persistence.JPA.matchNothing;
import static org.omnifaces.utils.stream.Streams.stream;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.omnifaces.persistence.JPA;

/**
 * Creates <code>path LIKE value</code>.
 * <p>
 * The search is case insensitive by default. There are three types of LIKE search:
 * <ul>
 * <li>{@link #startsWith(String)} - matches values starting with the given string.
 * <li>{@link #endsWith(String)} - matches values ending with the given string.
 * <li>{@link #contains(String)} - matches values containing the given string.
 * </ul>
 * <p>
 * Usage examples:
 *
 * <pre>
 * criteria.put("name", Like.contains("john")); // LOWER(name) LIKE '%john%'
 * criteria.put("name", Like.startsWith("john")); // LOWER(name) LIKE 'john%'
 * criteria.put("email", Like.endsWith("@foo.com")); // LOWER(email) LIKE '%@foo.com'
 * </pre>
 * <p>
 * This also works on enum and boolean fields, the same way as {@link #applies(Object)}. For enums, it will match enum constants whose name contains the search
 * string and generate an IN predicate. For booleans, it will match <code>true</code> and/or <code>false</code> when their name contains the search string, e.g.
 * <code>Like.contains("tru")</code> matches only <code>true</code>, and a search string which matches neither, such as <code>Like.contains("foo")</code>,
 * matches nothing. To match a boolean by a truthy or falsy value such as <code>"1"</code>, use {@link Bool#parse(Object)} instead.
 * <p>
 * The value of {@link #contains(String)}, {@link #startsWith(String)} and {@link #endsWith(String)} matches literally: <code>%</code> and <code>_</code>, and
 * <code>[</code> on SQL Server, are no wildcards in it, see {@link JPA#escapeLike(String)}. Use {@link #pattern(String)} to match by wildcards.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Criteria
 * @see IgnoreCase
 */
public final class Like extends Criteria<String> {

    private static final long serialVersionUID = 1L;
    private static final char ANY_CHARACTERS = '%';
    private static final char ANY_CHARACTER = '_';
    private static final String ANY = String.valueOf(ANY_CHARACTERS);

    private enum Type {
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        PATTERN;
    }

    /** The like type. */
    private final Type type;

    private Like(Type type, String value) {
        super(value);
        this.type = type;
    }

    /**
     * Returns a new LIKE criteria that matches values starting with the given string.
     *
     * @param value The string value.
     * @return A new STARTS_WITH LIKE criteria.
     */
    public static Like startsWith(String value) {
        return new Like(Type.STARTS_WITH, value);
    }

    /**
     * Returns a new LIKE criteria that matches values ending with the given string.
     *
     * @param value The string value.
     * @return A new ENDS_WITH LIKE criteria.
     */
    public static Like endsWith(String value) {
        return new Like(Type.ENDS_WITH, value);
    }

    /**
     * Returns a new LIKE criteria that matches values containing the given string.
     *
     * @param value The string value.
     * @return A new CONTAINS LIKE criteria.
     */
    public static Like contains(String value) {
        return new Like(Type.CONTAINS, value);
    }

    /**
     * Returns a new LIKE criteria that matches values matching the given pattern. In the pattern, <code>%</code> matches any characters, <code>_</code> matches
     * any single character, and {@link JPA#LIKE_ESCAPE} escapes the next character, so that <code>\%</code>, <code>\_</code> and <code>\\</code> match
     * literally. Any other character matches literally, on every database. On DB2, {@link JPA#LIKE_ESCAPE} may only precede <code>%</code>, <code>_</code> or
     * itself. A trailing {@link JPA#LIKE_ESCAPE} matches itself.
     *
     * @param pattern The pattern.
     * @return A new PATTERN LIKE criteria.
     * @since 2.0
     */
    public static Like pattern(String pattern) {
        return new Like(Type.PATTERN, pattern);
    }

    /**
     * Returns whether this criteria is a STARTS_WITH type.
     *
     * @return True if STARTS_WITH, false otherwise.
     */
    public boolean startsWith() {
        return type == Type.STARTS_WITH;
    }

    /**
     * Returns whether this criteria is an ENDS_WITH type.
     *
     * @return True if ENDS_WITH, false otherwise.
     */
    public boolean endsWith() {
        return type == Type.ENDS_WITH;
    }

    /**
     * Returns whether this criteria is a CONTAINS type.
     *
     * @return True if CONTAINS, false otherwise.
     */
    public boolean contains() {
        return type == Type.CONTAINS;
    }

    /**
     * Returns whether this criteria is a PATTERN type.
     *
     * @return True if PATTERN, false otherwise.
     * @since 2.0
     */
    public boolean pattern() {
        return type == Type.PATTERN;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        Class<?> type = path.getJavaType();

        if (type.isEnum() && path instanceof Path<?> p && isEnumeratedByOrdinal(p)) {
            Set<?> matches = stream(type.getEnumConstants()).filter(this::applies).collect(toSet());
            return matches.isEmpty() ? matchNothing(criteriaBuilder, parameterBuilder) : path.in(parameterBuilder.create(matches));
        }
        else if (Bool.is(type)) {
            return buildBooleanPredicate((Expression<Boolean>) path, criteriaBuilder, parameterBuilder);
        }
        else {
            boolean lowercaseable = !Numeric.is(type);
            String value = lowercaseable ? getValue().toLowerCase(Locale.ROOT) : getValue();
            String searchValue = pattern() ? escapeLikePattern(value) : (startsWith() ? "" : ANY) + escapeLike(value) + (endsWith() ? "" : ANY);
            Expression<String> pathAsString = castAsString(criteriaBuilder, path);
            return criteriaBuilder.like(lowercaseable ? lower(criteriaBuilder, pathAsString) : pathAsString, parameterBuilder.create(searchValue), LIKE_ESCAPE);
        }
    }

    private Predicate buildBooleanPredicate(Expression<Boolean> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        var matchesTrue = applies(true);
        var matchesFalse = applies(false);

        if (matchesTrue && matchesFalse) {
            return criteriaBuilder.isNotNull(path);
        }
        else if (matchesTrue) {
            return criteriaBuilder.isTrue(path);
        }
        else if (matchesFalse) {
            return criteriaBuilder.isFalse(path);
        }
        else {
            return matchNothing(criteriaBuilder, parameterBuilder);
        }
    }

    @Override
    public boolean applies(@Nullable Object modelValue) {
        if (modelValue == null) {
            return false;
        }

        String lowerCasedValue = getValue().toLowerCase(Locale.ROOT);
        String lowerCasedModelValue = modelValue.toString().toLowerCase(Locale.ROOT);

        if (pattern()) {
            return matches(lowerCasedValue, lowerCasedModelValue);
        }
        else if (startsWith()) {
            return lowerCasedModelValue.startsWith(lowerCasedValue);
        }
        else if (endsWith()) {
            return lowerCasedModelValue.endsWith(lowerCasedValue);
        }
        else {
            return lowerCasedModelValue.contains(lowerCasedValue);
        }
    }

    private static boolean matches(String pattern, String value) {
        var regex = new StringBuilder();

        for (var i = 0; i < pattern.length(); i++) {
            var character = pattern.charAt(i);

            if (character == LIKE_ESCAPE && i + 1 < pattern.length()) {
                regex.append(Pattern.quote(String.valueOf(pattern.charAt(++i))));
            }
            else if (character == ANY_CHARACTERS) {
                regex.append(".*");
            }
            else if (character == ANY_CHARACTER) {
                regex.append('.');
            }
            else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }

        return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(value).matches();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type);
    }

    @Override
    public boolean equals(Object object) {
        return super.equals(object) && type == ((Like) object).type;
    }

    @Override
    public String toString() {
        return "LIKE " + (pattern() ? getValue() : (startsWith() ? "" : ANY) + getValue() + (endsWith() ? "" : ANY));
    }

}
