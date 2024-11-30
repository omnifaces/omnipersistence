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
package org.omnifaces.persistence.model;

import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.Objects.requireNonNullElseGet;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.omnifaces.persistence.listener.BaseEntityListener;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Let all your entities extend from this.
 * Then you can make use of {@link BaseEntityService}.
 * <p>
 * There are five more mapped superclasses which may also be of interest.
 * <ul>
 * <li>{@link TimestampedBaseEntity} - extends {@link BaseEntity} with <code>created</code> and <code>lastModified</code> columns and automatically takes care of them.
 * <li>{@link VersionedBaseEntity} - extends {@link TimestampedBaseEntity} with a <code>@Version</code> column and automatically takes care of it.
 * <li>{@link GeneratedIdEntity} - extends {@link BaseEntity} with <code>id</code> column and automatically takes care of it.
 * <li>{@link TimestampedEntity} - extends {@link GeneratedIdEntity} with <code>created</code> and <code>lastModified</code> columns and automatically takes care of them.
 * <li>{@link VersionedEntity} - extends {@link TimestampedEntity} with a <code>@Version</code> column and automatically takes care of it.
 * </ul>
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 */
@MappedSuperclass
@EntityListeners(BaseEntityListener.class)
public abstract class BaseEntity<I extends Comparable<I> & Serializable> implements Comparable<BaseEntity<I>>, Identifiable<I>, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Hashes by default the ID.
     */
    @Override
    public int hashCode() {
        return hashCode(BaseEntity::getId);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected final <E extends BaseEntity<I>> int hashCode(final Function<E, Object>... getters) {
        final var values = stream(getters).map(getter -> getter.apply((E) this)).filter(Objects::nonNull).toArray();
        return values.length > 0 ? Objects.hash(values) : super.hashCode();
    }

    /**
     * Compares by default by entity class (proxies taken into account) and ID.
     */
    @Override
    public boolean equals(final Object other) {
        return equals(other, BaseEntity::getId);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected final <E extends BaseEntity<I>> boolean equals(final Object other, final Function<E, Object>... getters) {
        if (other == this) {
            return true;
        }

        if (!getClass().isInstance(other) && !other.getClass().isInstance(this)) {
            return false;
        }

        return stream(getters).map(getter -> Objects.equals(getters[0].apply((E) this), getters[0].apply((E) other))).allMatch(b -> b);
    }

    /**
     * Orders by default with "nulls last".
     */
    @Override
    public int compareTo(final BaseEntity<I> other) {
        return compareTo(other, BaseEntity::getId);
    }

    @SafeVarargs
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected final <E extends BaseEntity<I>> int compareTo(final Object other, final Function<E, Object>... getters) {
        if (other == null) {
            return -1;
        }

        Comparator<Object> comparator = null;

        for (final Function getter : getters) {
            if (comparator == null) {
                comparator = comparing(getter, nullsLast(naturalOrder()));
            }
            else {
                comparator = comparator.thenComparing(getter, nullsLast(naturalOrder()));
            }
        }

        return comparator.compare(this, other);
    }

    /**
     * The default format is <code>ClassName[{id}]</code> where <code>{id}</code> defaults to <code>@hashcode</code> when null.
     */
    @Override
    public String toString() {
        return toString(e -> requireNonNullElseGet(e.getId(), () -> ("@" + e.hashCode())));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected final <E extends BaseEntity<I>> String toString(final Function<E, Object>... getters) {
        final var builder = new StringBuilder(getClass().getSimpleName()).append("[");

        for (var i = 0; i < getters.length; i++) {
            builder.append(getters[i].apply((E) this));

            if (i + 1 < getters.length) {
                builder.append(", ");
            }
        }

        return builder.append("]").toString();
    }
}