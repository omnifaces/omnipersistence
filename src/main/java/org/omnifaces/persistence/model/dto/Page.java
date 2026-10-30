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
package org.omnifaces.persistence.model.dto;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static org.omnifaces.persistence.model.Identifiable.ID;
import static org.omnifaces.utils.Lang.isEmpty;
import static org.omnifaces.utils.reflect.Reflections.accessField;
import static org.omnifaces.utils.reflect.Reflections.findGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeGetter;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import jakarta.annotation.Nullable;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * This class basically defines a paged view of a database based on a given offset, limit, ordering, required criteria and optional criteria. This is used by
 * {@link BaseEntityService#getPage(Page, boolean)} methods.
 * <p>
 * There are two convenience constants:
 * <ul>
 * <li>{@link #ALL} - returns all results (offset 0, limit {@link Integer#MAX_VALUE}).
 * <li>{@link #ONE} - returns at most one result (offset 0, limit 1).
 * </ul>
 * <p>
 * Usage examples:
 *
 * <pre>
 *
 * // Simplest form: get first 10 records.
 * Page first10Records = Page.of(0, 10);
 * PartialResultList&lt;Foo&gt; foos = fooService.getPage(first10Records, true);
 * </pre>
 *
 * <pre>
 * // Using the builder with ordering and criteria.
 * Map&lt;String, Object&gt; criteria = new HashMap&lt;&gt;();
 * criteria.put("name", Like.contains("john"));
 * criteria.put("active", Bool.value(true));
 *
 * Page page = Page.with()
 *     .range(0, 10)
 *     .orderBy("name", true)
 *     .allMatch(criteria)
 *     .build();
 * </pre>
 *
 * <pre>
 * // Using optional (OR) criteria.
 * Map&lt;String, Object&gt; optionalCriteria = new HashMap&lt;&gt;();
 * optionalCriteria.put("name", Like.contains("john"));
 * optionalCriteria.put("email", Like.contains("john"));
 *
 * Page page = Page.with()
 *     .range(0, 10)
 *     .anyMatch(optionalCriteria)
 *     .build();
 * </pre>
 *
 * <pre>
 *
 * // Cursor-based (keyset) paging: avoids SQL OFFSET for stable performance on large datasets.
 * Page page1 = Page.with().range(0, 10).orderBy("id", true).build();
 * PartialResultList&lt;Foo&gt; first = fooService.getPage(page1, false);
 *
 * Foo lastSeen = first.get(first.size() - 1);
 * Page page2 = Page.with().range(lastSeen, 10, false).orderBy("id", true).build();
 * PartialResultList&lt;Foo&gt; second = fooService.getPage(page2, false);
 * </pre>
 *
 * <pre>
 *
 * // Cursor-based (keyset) paging without holding the entity: only the ordering values of the last seen record are needed.
 * Page page2 = Page.with().range(10, 10, Map.of("id", lastSeenId), false).orderBy("id", true).build();
 * PartialResultList&lt;Foo&gt; second = fooService.getPage(page2, false);
 * </pre>
 *
 * <pre>
 *
 * // Jump to an unloaded page: take the nearest known record before the page as boundary and skip the remaining records.
 * Page page5 = Page.with().range(40, 10, Map.of("id", idOfRecordAtOffset29), 10, false).orderBy("id", true).build();
 * PartialResultList&lt;Foo&gt; fifth = fooService.getPage(page5, false);
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see BaseEntityService
 * @see Criteria
 */
public final class Page implements Serializable { // This class MAY NOT be mutable!

    // Constants ------------------------------------------------------------------------------------------------------

    private static final long serialVersionUID = 1L;

    /** All pages. */
    public final static Page ALL = Page.of(0, MAX_VALUE);
    /** One record (the first one). */
    public final static Page ONE = Page.of(0, 1);

    // Properties -----------------------------------------------------------------------------------------------------

    /** Zero-based offset of the page. */
    private final int offset;
    /** Maximum amount of records to be matched. */
    private final int limit;
    /** Ordering values of the record bounding the page, if any. */
    private final Map<String, Object> lastOrderingValues;
    /** Amount of records to skip after the last ordering values. */
    private final int residualOffset;
    /** Whether value based paging is reversed. */
    private final boolean reversed;
    /** Ordering of results, keyed by property path, valued by whether to sort ascending. */
    private final Map<String, Boolean> ordering;
    /** Criteria of which each entity must match all. */
    private final Map<String, Object> requiredCriteria;
    /** Criteria of which each entity must match at least one. */
    private final Map<String, Object> optionalCriteria;

    // Constructors ---------------------------------------------------------------------------------------------------

    private Page(
        @Nullable Integer offset, @Nullable Integer limit, @Nullable Map<String, Object> lastOrderingValues, @Nullable Integer residualOffset, boolean reversed,
        @Nullable LinkedHashMap<String, Boolean> ordering, // TODO: Use SequencedMap once Java 21 is the baseline.
        @Nullable Map<String, Object> requiredCriteria, @Nullable Map<String, Object> optionalCriteria
    )
    {
        this.offset = validateIntegerArgument("offset", offset, 0, 0);
        this.limit = validateIntegerArgument("limit", limit, 1, MAX_VALUE);
        this.ordering = orderingOrDefault(ordering);
        this.lastOrderingValues = validateLastOrderingValues(lastOrderingValues, this.ordering);
        // Not validated against offset: where the last ordering values sit in the current data is not cheaply verifiable by the page nor by its author.
        this.residualOffset = this.lastOrderingValues.isEmpty() ? 0 : validateIntegerArgument("residualOffset", residualOffset, 0, 0);
        this.reversed = !this.lastOrderingValues.isEmpty() && reversed;
        this.requiredCriteria = requiredCriteria != null ? unmodifiableMap(new LinkedHashMap<>(requiredCriteria)) : emptyMap();
        this.optionalCriteria = optionalCriteria != null ? unmodifiableMap(new LinkedHashMap<>(optionalCriteria)) : emptyMap();
    }

    private static Map<String, Boolean> orderingOrDefault(@Nullable Map<String, Boolean> ordering) {
        return !isEmpty(ordering) ? unmodifiableMap(new LinkedHashMap<>(ordering)) : singletonMap(ID, false);
    }

    private static Map<String, Object> validateLastOrderingValues(@Nullable Map<String, Object> lastOrderingValues, Map<String, Boolean> ordering) {
        if (lastOrderingValues == null) {
            return emptyMap();
        }

        if (!lastOrderingValues.keySet().equals(ordering.keySet())) {
            throw new IllegalArgumentException(
                "Argument 'lastOrderingValues' must have exactly the keys of ordering " + ordering.keySet() + " but has " + lastOrderingValues.keySet()
            );
        }

        return unmodifiableMap(collectOrderingValues(ordering, lastOrderingValues::get));
    }

    private static Map<String, Object> collectOrderingValues(Map<String, Boolean> ordering, Function<String, Object> valueOfField) {
        var orderingValues = new LinkedHashMap<String, Object>(ordering.size());

        for (var field : ordering.keySet()) {
            orderingValues.put(field, valueOfField.apply(field));
        }

        return orderingValues;
    }

    private static int validateIntegerArgument(String argumentName, Integer argumentValue, int minValue, int defaultValue) {
        if (argumentValue == null) {
            return defaultValue;
        }

        if (argumentValue < minValue) {
            throw new IllegalArgumentException("Argument '" + argumentName + "' may not be less than " + minValue);
        }

        return argumentValue;
    }

    // Getters --------------------------------------------------------------------------------------------------------

    /**
     * Returns the offset. Defaults to 0.
     *
     * @return The offset.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Returns the limit. Defaults to {@link Integer#MAX_VALUE}.
     *
     * @return The limit.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Returns the ordering values of the record bounding the page. The page starts after this record, after skipping {@link #getResidualOffset()} records. When
     * the residual offset is 0, this is the last record of the previous page. When reversed, the page instead ends before this record, after skipping the
     * residual offset towards the previous page; when the residual offset is 0, this is then the first record of the next page. This is empty when there is no
     * such record. Otherwise it has exactly the keys of {@link #getOrdering()}, in the same order, and map value represents the value of that property in the
     * record, which may be <code>null</code>. If not empty, then value based paging will be performed instead of offset based paging when applicable. Value
     * based paging is not applicable when the query is fine-grained with a <code>QueryBuilder</code> of {@link BaseEntityService}, or on Hibernate when the
     * query is fine-grained with a <code>MappedQueryBuilder</code> and the ordering contains an aggregated field.
     *
     * @return The ordering values of the record bounding the page, or an empty map if there is none.
     * @since 1.2
     */
    public Map<String, Object> getLastOrderingValues() {
        return lastOrderingValues;
    }

    /**
     * Returns the amount of records to skip after the record represented by {@link #getLastOrderingValues()}. Defaults to 0. This is only used when
     * {@link #getLastOrderingValues()} is not empty, and then applied as SQL {@code OFFSET} instead of {@link #getOffset()} when value based paging is
     * applicable.
     *
     * @return The amount of records to skip after the record represented by the last ordering values.
     * @since 1.2.1
     */
    public int getResidualOffset() {
        return residualOffset;
    }

    /**
     * Returns whether the value based paging is reversed. This is only used when {@link #getLastOrderingValues()} is not empty.
     *
     * @return Whether the value based paging is reversed.
     */
    public boolean isReversed() {
        return reversed;
    }

    /**
     * Returns the ordering. Map key represents property path and map value represents whether to sort ascending. Defaults to <code>{"id",false}</code>.
     *
     * @return The ordering.
     */
    public Map<String, Boolean> getOrdering() {
        return ordering;
    }

    /**
     * Returns the required criteria. Map key represents property path and map value represents criteria. Each entity must match all of given criteria. The
     * iteration order is the one of the map given to the builder.
     *
     * @return The required criteria.
     */
    public Map<String, Object> getRequiredCriteria() {
        return requiredCriteria;
    }

    /**
     * Returns the optional criteria. Map key represents property path and map value represents criteria. Each entity must match at least one of given criteria.
     * The iteration order is the one of the map given to the builder.
     *
     * @return The optional criteria.
     */
    public Map<String, Object> getOptionalCriteria() {
        return optionalCriteria;
    }

    // Object overrides -----------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Page other)) {
            return false;
        }

        if (object == this) {
            return true;
        }

        return Objects.equals(offset, other.offset)
            && Objects.equals(limit, other.limit)
            && Objects.equals(lastOrderingValues, other.lastOrderingValues)
            && Objects.equals(residualOffset, other.residualOffset)
            && Objects.equals(reversed, other.reversed)
            && Objects.equals(ordering, other.ordering)
            && Objects.equals(requiredCriteria, other.requiredCriteria)
            && Objects.equals(optionalCriteria, other.optionalCriteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Page.class, offset, limit, lastOrderingValues, residualOffset, reversed, ordering, requiredCriteria, optionalCriteria);
    }

    @Override
    public String toString() {
        return new StringBuilder("Page[")
            .append(offset).append(",")
            .append(limit).append(",")
            .append(lastOrderingValues).append(",")
            .append(residualOffset).append(",")
            .append(reversed).append(",")
            .append(ordering).append(",")
            .append(new TreeMap<>(requiredCriteria)).append(",")
            .append(new TreeMap<>(optionalCriteria)).append("]").toString();
    }

    // Builder --------------------------------------------------------------------------------------------------------

    /**
     * Returns a clone of the current page which returns all results matching the current ordering, required criteria and optional criteria.
     *
     * @return A clone of the current page which returns all results matching the current ordering, required criteria and optional criteria.
     */
    public Page all() {
        return new Page(null, null, null, null, false, new LinkedHashMap<>(ordering), requiredCriteria, optionalCriteria);
    }

    /**
     * Use this if you want to build a new page.
     *
     * @return A new page builder.
     */
    public static Builder with() {
        return new Builder();
    }

    /**
     * Use this if you want a page of given offset and limit.
     *
     * @param offset Zero-based offset of the page. May not be negative. Defaults to 0.
     * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
     * @return A new page of given offset and limit.
     */
    public static Page of(int offset, int limit) {
        return with().range(offset, limit).build();
    }

    /**
     * The page builder. Use {@link Page#with()} to get started.
     *
     * @author Bauke Scholtz
     * @since 1.0
     */
    public static class Builder {

        /**
         * Default constructor. Use {@link Page#with()} to get started.
         */
        public Builder() {
        }

        private static final String ERROR_NUMBER_AS_LAST = "Last entity may not be a number; for an offset use range(int, int) instead";

        private Integer offset;
        private Integer limit;
        private Object last;
        private Map<String, Object> lastOrderingValues;
        private Integer residualOffset;
        private boolean reversed;
        private final LinkedHashMap<String, Boolean> ordering = new LinkedHashMap<>(2);
        private Map<String, Object> requiredCriteria;
        private Map<String, Object> optionalCriteria;

        /**
         * Set the range for offset-based paging. Mutually exclusive with the other <code>range</code> methods.
         *
         * @param offset Zero-based offset of the page. May not be negative. Defaults to 0.
         * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
         * @throws IllegalStateException When a range is already set in this builder.
         * @return This builder.
         */
        public Builder range(int offset, int limit) {
            checkRangeNotSet();
            this.offset = offset;
            this.limit = limit;
            return this;
        }

        /**
         * Set the range for cursor-based (value-based) paging. Instead of SQL {@code OFFSET}, the query uses a keyset predicate derived from the last entity,
         * keeping performance stable regardless of page depth. Mutually exclusive with the other <code>range</code> methods.
         * <p>
         * The page does not retain the entity: {@link #build()} extracts the values of the ordering fields from it via their getters. When the last property of
         * a path has no getter, its field is read instead. Each intermediate property of a nested path must have a getter, because a lazy association is only
         * initialized via its getter. For the same reason, a lazy basic property without a getter gets its raw field state. A nested property path whose
         * intermediate value is <code>null</code> gets the value <code>null</code>. Without explicit ordering, the page is ordered by <code>id</code>, so the
         * entity must then have an <code>id</code> property.
         *
         * @param last Last entity of the previous page. May not be <code>null</code>.
         * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
         * @param reversed Whether to navigate in reverse order (i.e. towards the previous page).
         * @throws NullPointerException When last entity is <code>null</code>.
         * @throws IllegalArgumentException When last entity is a {@link Number}, which is likely an offset meant for {@link #range(int, int)}.
         * @throws IllegalStateException When a range is already set in this builder.
         * @return This builder.
         * @see Page#getLastOrderingValues()
         */
        public Builder range(Object last, int limit, boolean reversed) {
            checkRangeNotSet();

            if (requireNonNull(last, "last") instanceof Number) {
                throw new IllegalArgumentException(ERROR_NUMBER_AS_LAST);
            }

            this.last = last;

            this.limit = limit;
            this.reversed = reversed;
            return this;
        }

        /**
         * Set the range for cursor-based (value-based) paging. Instead of SQL {@code OFFSET}, the query uses a keyset predicate derived from the ordering
         * values of the last record of the previous page, keeping performance stable regardless of page depth. Mutually exclusive with the other
         * <code>range</code> methods.
         *
         * @param offset Zero-based offset of the page, as reported by the result. May not be negative. This is only applied as SQL {@code OFFSET} when value
         * based paging is not applicable.
         * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
         * @param lastOrderingValues Ordering values of the last record of the previous page. Map key represents property path and map value represents the
         * value of that property in the last record, which may be <code>null</code>. Each value must be of the Java type of the property, e.g. a
         * <code>Long</code> for a <code>Long</code> ID. The keys must be exactly the keys of the ordering. May not be <code>null</code>.
         * @param reversed Whether to navigate in reverse order (i.e. towards the previous page).
         * @throws NullPointerException When last ordering values is <code>null</code>.
         * @throws IllegalStateException When a range is already set in this builder.
         * @return This builder.
         * @see Page#getLastOrderingValues()
         * @since 1.2
         */
        public Builder range(int offset, int limit, Map<String, Object> lastOrderingValues, boolean reversed) {
            return range(offset, limit, lastOrderingValues, 0, reversed);
        }

        /**
         * Set the range for cursor-based (value-based) paging with a residual offset. Instead of SQL {@code OFFSET} of the page, the query uses a keyset
         * predicate derived from the ordering values of a record before the page, and skips the given residual offset of records after that record. This allows
         * jumping to a page whose previous page is not loaded, using the nearest known record before it. Mutually exclusive with the other <code>range</code>
         * methods.
         *
         * @param offset Zero-based offset of the page, as reported by the result. May not be negative. This is only applied as SQL {@code OFFSET} when value
         * based paging is not applicable.
         * @param limit Maximum amount of records to be matched. May not be less than 1. Defaults to {@link Integer#MAX_VALUE}.
         * @param lastOrderingValues Ordering values of the record bounding the page, from which the residual offset is skipped. Map key represents property
         * path and map value represents the value of that property in the record, which may be <code>null</code>. Each value must be of the Java type of the
         * property, e.g. a <code>Long</code> for a <code>Long</code> ID. The keys must be exactly the keys of the ordering. May not be <code>null</code>.
         * @param residualOffset Amount of records to skip after the record represented by the last ordering values. May not be negative. This is only applied
         * as SQL {@code OFFSET} when value based paging is applicable. When reversed, the records are skipped towards the previous page.
         * @param reversed Whether to navigate in reverse order (i.e. towards the previous page).
         * @throws NullPointerException When last ordering values is <code>null</code>.
         * @throws IllegalStateException When a range is already set in this builder.
         * @return This builder.
         * @see Page#getLastOrderingValues()
         * @see Page#getResidualOffset()
         * @since 1.2.1
         */
        public Builder range(int offset, int limit, Map<String, Object> lastOrderingValues, int residualOffset, boolean reversed) {
            checkRangeNotSet();
            this.offset = offset;
            this.limit = limit;
            this.lastOrderingValues = requireNonNull(lastOrderingValues, "lastOrderingValues");
            this.residualOffset = residualOffset;
            this.reversed = reversed;
            return this;
        }

        private void checkRangeNotSet() {
            if (limit != null) {
                throw new IllegalStateException("Range is already set");
            }
        }

        /**
         * Set the ordering. This can be invoked multiple times and will be remembered in same order. The default ordering is <code>{"id",false}</code>.
         *
         * @param field The field. May not be <code>null</code>.
         * @param ascending Whether to sort ascending.
         * @return This builder.
         * @throws NullPointerException When field is <code>null</code>.
         */
        public Builder orderBy(String field, boolean ascending) {
            ordering.put(requireNonNull(field, "field"), ascending);
            return this;
        }

        /**
         * Set the required criteria. Map key represents property path and map value represents criteria. Each entity must match all of given criteria.
         *
         * @param requiredCriteria Required criteria. May not be <code>null</code>.
         * @return This builder.
         * @throws NullPointerException When required criteria is <code>null</code>.
         * @throws IllegalStateException When another required criteria is already set in this builder.
         * @see Criteria
         */
        public Builder allMatch(Map<String, Object> requiredCriteria) {
            if (this.requiredCriteria != null) {
                throw new IllegalStateException("Required criteria is already set");
            }

            this.requiredCriteria = requireNonNull(requiredCriteria, "requiredCriteria");
            return this;
        }

        /**
         * Set the optional criteria. Map key represents property path and map value represents criteria. Each entity must match at least one of given criteria.
         *
         * @param optionalCriteria Optional criteria. May not be <code>null</code>.
         * @return This builder.
         * @throws NullPointerException When optional criteria is <code>null</code>.
         * @throws IllegalStateException When another optional criteria is already set in this builder.
         * @see Criteria
         */
        public Builder anyMatch(Map<String, Object> optionalCriteria) {
            if (this.optionalCriteria != null) {
                throw new IllegalStateException("Optional criteria is already set");
            }

            this.optionalCriteria = requireNonNull(optionalCriteria, "optionalCriteria");
            return this;
        }

        /**
         * Build the page.
         *
         * @return The built page.
         * @throws IllegalArgumentException When the keys of the last ordering values do not match the keys of the ordering.
         * @throws IllegalStateException When an intermediate property of an ordering field path has no getter, or its last property has neither a getter nor a
         * field.
         */
        public Page build() {
            var orderingValues = last != null
                ? collectOrderingValues(orderingOrDefault(ordering), field -> readNestedProperty(last, field))
                : lastOrderingValues;
            return new Page(offset, limit, orderingValues, residualOffset, reversed, ordering, requiredCriteria, optionalCriteria);
        }

        private static Object readNestedProperty(Object instance, String path) {
            var properties = path.split("\\.");
            var lastIndex = properties.length - 1;
            var value = instance;

            for (var i = 0; i < lastIndex && value != null; i++) {
                value = invokeGetter(value, properties[i]);
            }

            return value == null ? null : readProperty(value, properties[lastIndex]);
        }

        private static Object readProperty(Object instance, String property) {
            return findGetter(instance.getClass(), property)
                .map(getter -> invokeMethod(instance, getter))
                .orElseGet(() -> accessField(Provider.HIBERNATE.dereferenceProxy(instance), property));
        }

    }

}
