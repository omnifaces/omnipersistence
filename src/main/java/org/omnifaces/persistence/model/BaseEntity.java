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
package org.omnifaces.persistence.model;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.joining;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.omnifaces.persistence.listener.BaseEntityListener;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Let all your entities extend from this. Then you can make use of {@link BaseEntityService}.
 * This is the root of the entity hierarchy and provides default implementations of {@link Object#hashCode()},
 * {@link Object#equals(Object)}, {@link Comparable#compareTo(Object)} and {@link Object#toString()} based on the entity ID.
 * <p>
 * There are five more mapped superclasses which may also be of interest.
 * <ul>
 * <li>{@link TimestampedBaseEntity} - extends {@link BaseEntity} with <code>created</code> and <code>lastModified</code> columns and automatically takes care of them.
 * <li>{@link VersionedBaseEntity} - extends {@link TimestampedBaseEntity} with a <code>@Version</code> column and automatically takes care of it.
 * <li>{@link GeneratedIdEntity} - extends {@link BaseEntity} with <code>id</code> column and automatically takes care of it.
 * <li>{@link TimestampedEntity} - extends {@link GeneratedIdEntity} with <code>created</code> and <code>lastModified</code> columns and automatically takes care of them.
 * <li>{@link VersionedEntity} - extends {@link TimestampedEntity} with a <code>@Version</code> column and automatically takes care of it.
 * </ul>
 * <p>
 * The first three ({@link BaseEntity}, {@link TimestampedBaseEntity} and {@link VersionedBaseEntity}) require you to
 * manually define the {@link jakarta.persistence.Id} column. The last three ({@link GeneratedIdEntity}, {@link TimestampedEntity}
 * and {@link VersionedEntity}) already provide an auto-generated <code>id</code> column.
 * <p>
 * Usage example:
 * <pre>
 * &#64;Entity
 * public class YourEntity extends GeneratedIdEntity&lt;Long&gt; {
 *
 *     private String name;
 *
 *     // Getters and setters omitted.
 * }
 * </pre>
 * <p>
 * Override {@link #identityGetters()} to base all four identity methods ({@code equals}, {@code hashCode},
 * {@code compareTo} and {@code toString}) on custom business-key properties — this is the preferred approach:
 * <pre>
 * &#64;Override
 * protected Stream&lt;Function&lt;YourEntity, Object&gt;&gt; identityGetters() {
 *     return Stream.of(YourEntity::getEmail);
 * }
 * </pre>
 * <p>
 * Use the protected final helpers ({@link #hashCode(Function...)}, {@link #equals(Object, Function...)},
 * {@link #compareTo(Object, Function...)}, {@link #toString(Function...)}) only when individual methods must behave
 * differently — for example when {@code compareTo} should order by different fields than those used for equality:
 * <pre>
 * &#64;Override
 * protected Stream&lt;Function&lt;YourEntity, Object&gt;&gt; identityGetters() {
 *     return Stream.of(YourEntity::getEmail); // equals, hashCode and toString identify by email
 * }
 *
 * &#64;Override
 * public int compareTo(BaseEntity&lt;I&gt; other) {
 *     return compareTo(other, YourEntity::getLastName, YourEntity::getFirstName); // sort by name for display
 * }
 * </pre>
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @since 1.0
 * @see BaseEntityService
 */
@MappedSuperclass
@EntityListeners(BaseEntityListener.class)
public abstract class BaseEntity<I extends Comparable<I> & Serializable> implements Comparable<BaseEntity<I>>, Identifiable<I>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Returns the getters that define entity identity.
     * The default implementation returns {@link BaseEntity#getId()}.
     * <p>
     * Override to use natural/business key(s), e.g.:
     * <pre>
     * &#64;Override
     * protected Stream&lt;Function&lt;Phone, Object&gt;&gt; identityGetters() {
     *     return Stream.of(Phone::getCountryCode, Phone::getNumber);
     * }
     * </pre>
     * @return The getters that define entity identity.
     */
    protected Stream<? extends Function<?, Object>> identityGetters() {
        return Stream.<Function<BaseEntity<I>, Object>>of(BaseEntity::getId);
    }

    /**
     * Hashes by default the ID.
     */
    @Override
    public int hashCode() {
        return hashCode(identityGetters());
    }

	/**
	 * Subclasses can use this convenience method to override the {@link #hashCode()} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param getters The property getters to determine the {@link #hashCode()} for.
	 * @return The {@link #hashCode()} of the given property getters.
	 */
    @SafeVarargs
    protected final <E extends BaseEntity<I>> int hashCode(final Function<E, Object>... getters) {
        return hashCode(Stream.of(getters));
    }

    @SuppressWarnings("unchecked")
    private int hashCode(final Stream<? extends Function<?, Object>> getters) {
        final var values = getters.map(getter -> ((Function<Object, Object>) getter).apply(this)).filter(Objects::nonNull).toArray();
        return values.length > 0 ? Objects.hash(values) : super.hashCode();
    }

    /**
     * Compares by default by entity class (proxies taken into account) and ID.
     */
    @Override
    public boolean equals(final Object other) {
        return equals(other, identityGetters());
    }

	/**
	 * Subclasses can use this convenience method to override the {@link #equals(Object)} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param other The reference object with which to compare.
	 * @param getters The property getters to determine the {@link #equals(Object)} for.
	 * @return {@code true} if this object is the same as the {@code other} argument; {@code false} otherwise.
	 */
    @SafeVarargs
    protected final <E extends BaseEntity<I>> boolean equals(final Object other, final Function<E, Object>... getters) {
        return equals(other, Stream.of(getters));
    }

    @SuppressWarnings("unchecked")
    private boolean equals(final Object other, final Stream<? extends Function<?, Object>> getters) {
        if (other == this) {
            return true;
        }

        if (other == null || !getClass().isInstance(other) && !other.getClass().isInstance(this)) {
            return false;
        }

        final var gettersList = getters.toList();
        final var values = gettersList.stream().map(getter -> ((Function<Object, Object>) getter).apply(this)).toArray();

        if (stream(values).allMatch(Objects::isNull)) {
            return false;
        }

        final var otherValues = gettersList.stream().map(getter -> ((Function<Object, Object>) getter).apply(other)).toArray();
        return Objects.deepEquals(values, otherValues);
    }

    /**
     * Orders by default with "nulls last".
     */
    @Override
    public int compareTo(final BaseEntity<I> other) {
        return compareTo(other, identityGetters());
    }

    /**
     * Subclasses can use this convenience method to override the {@link #compareTo(BaseEntity)} based on given property getters.
     * @param <E> The generic base entity type.
     * @param other The object to be compared.
     * @param getters The property getters to determine the {@link #compareTo(BaseEntity)} for.
     * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     */
    @SafeVarargs
    protected final <E extends BaseEntity<I>> int compareTo(final Object other, final Function<E, Object>... getters) {
        return compareTo(other, Stream.of(getters));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private int compareTo(final Object other, final Stream<? extends Function<?, Object>> getters) {
        if (other == null) {
            return -1;
        }

        return getters.map(getter -> comparing((Function) getter, nullsLast(naturalOrder()))).reduce(Comparator::thenComparing).orElseThrow().compare(this, other);
    }

    /**
     * The default format is <code>ClassName[{id}]</code> where <code>{id}</code> defaults to <code>@hashcode</code> when null.
     */
    @Override
    public String toString() {
        return toString(identityGetters());
    }

    /**
	 * Subclasses can use this convenience method to override the {@link #toString()} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param getters The property getters to determine the {@link #toString()} for.
     * @return The {@link Class#getSimpleName()}, then followed by {@code [}, then a comma separated string of the
     * results of all given getters, and finally followed by {@code ]}.
     */
    @SafeVarargs
    protected final <E extends BaseEntity<I>> String toString(final Function<E, Object>... getters) {
        return toString(Stream.of(getters));
    }

    @SuppressWarnings("unchecked")
    private String toString(final Stream<? extends Function<?, Object>> getters) {
        final var values = getters.map(getter -> ((Function<Object, Object>) getter).apply(this)).toArray();
        return getClass().getSimpleName() + "[" + (stream(values).allMatch(Objects::isNull) ? "@" + toHexString(identityHashCode(this)) : stream(values).map(Objects::toString).collect(joining(", "))) + "]";
    }
}