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

import static java.util.Objects.requireNonNullElseGet;
import static java.util.stream.Collectors.joining;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

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
 * To base {@link #hashCode()}, {@link #equals(Object)}, {@link #compareTo(BaseEntity)} and {@link #toString()} on
 * specific fields rather than the entity ID, override {@link #identity()} to return those field values:
 * <pre>
 * &#64;Override
 * protected Object[] identity() {
 *     return new Object[]{ getType(), getNumber() };
 * }
 * </pre>
 * All four methods will automatically use the returned values. No other overrides are needed.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @see BaseEntityService
 */
@MappedSuperclass
@EntityListeners(BaseEntityListener.class)
public abstract class BaseEntity<I extends Comparable<I> & Serializable> implements Comparable<BaseEntity<I>>, Identifiable<I>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Returns the field values that define this entity's identity, used by {@link #hashCode()}, {@link #equals(Object)},
     * {@link #compareTo(BaseEntity)} and {@link #toString()}.
     * <p>
     * The default implementation returns the entity ID. Override this in subclasses to use different fields:
     * <pre>
     * &#64;Override
     * protected Object[] identity() {
     *     return new Object[]{ getType(), getNumber() };
     * }
     * </pre>
     * @return The field values that define this entity's identity.
     */
    protected Object[] identity() {
        return new Object[]{ getId() };
    }

    /**
     * Hashes by default the ID. Falls back to {@link System#identityHashCode(Object)} when all identity values are null.
     */
    @Override
    public int hashCode() {
        var values = identity();
        return Arrays.stream(values).allMatch(Objects::isNull) ? super.hashCode() : Arrays.hashCode(values);
    }

    /**
     * Compares by default by entity class (proxies taken into account) and ID.
     * Returns {@code false} when all identity values are null.
     */
    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }

        if (other == null || !getClass().isInstance(other) && !other.getClass().isInstance(this)) {
            return false;
        }

        var values = identity();

        if (Arrays.stream(values).allMatch(Objects::isNull)) {
            return false;
        }

        return Arrays.equals(values, ((BaseEntity<?>) other).identity());
    }

    /**
     * Orders by default with "nulls last".
     */
    @Override
    @SuppressWarnings("unchecked")
    public int compareTo(final BaseEntity<I> other) {
        if (other == null) {
            return -1;
        }

        var a = identity();
        var b = other.identity();

        return IntStream.range(0, a.length).map(i -> {
            if (a[i] == null && b[i] == null) return 0;
            if (a[i] == null) return 1;  // nulls last
            if (b[i] == null) return -1;
            return ((Comparable<Object>) a[i]).compareTo(b[i]);
        }).filter(c -> c != 0).findFirst().orElse(0);
    }

    /**
     * The default format is <code>ClassName[{id}]</code> where <code>{id}</code> defaults to <code>@hashcode</code> when null.
     * When {@link #identity()} is overridden, the format is <code>ClassName[v1, v2, …]</code>.
     */
    @Override
    public String toString() {
        var values = identity();

        if (values.length == 1 && Objects.equals(values[0], getId())) {
            return getClass().getSimpleName() + "[" + requireNonNullElseGet(getId(), () -> "@" + super.hashCode()) + "]";
        }

        return Arrays.stream(values).map(Objects::toString).collect(joining(", ", getClass().getSimpleName() + "[", "]"));
    }
}
