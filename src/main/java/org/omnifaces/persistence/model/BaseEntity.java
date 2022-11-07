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
import static java.util.stream.Collectors.joining;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Function;

import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;

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

	/**
	 * Subclasses can use this convenience method to override the {@link #hashCode()} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param getters The property getters to determine the {@link #hashCode()} for.
	 * @return The {@link #hashCode()} of the given property getters.
	 */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected final <E extends BaseEntity<I>> int hashCode(Function<E, Object>... getters) {
        Object[] values = stream(getters).map(getter -> getter.apply((E) this)).filter(Objects::nonNull).toArray();
        return values.length > 0 ? Objects.hash(values) : super.hashCode();
    }

	/**
	 * Compares by default by entity class (proxies taken into account) and ID.
	 */
	@Override
    public boolean equals(Object other) {
        return equals(other, BaseEntity::getId);
    }

	/**
	 * Subclasses can use this convenience method to override the {@link #equals(Object)} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param other The reference object with which to compare.
	 * @param getters The property getters to determine the {@link #equals(Object)} for.
	 * @return {@code true} if this object is the same as the {@code other} argument; {@code false} otherwise.
	 */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected final <E extends BaseEntity<I>> boolean equals(Object other, Function<E, Object>... getters) {
        return other == this || (getClass().isInstance(other) && other.getClass().isInstance(this) && stream(getters)
        	.map(getter -> Objects.equals(getter.apply((E) this), getter.apply((E) other)))
        	.allMatch(b -> b));
    }

	/**
	 * Orders by default with "nulls last".
	 */
	@Override
	public int compareTo(BaseEntity<I> other) {
        return compareTo(other, BaseEntity::getId);
	}

	/**
	 * Subclasses can use this convenience method to override the {@link #compareTo(BaseEntity)} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param other The object to be compared.
	 * @param getters The property getters to determine the {@link #compareTo(BaseEntity)} for.
	 * @return A negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
	 */
	@SafeVarargs
	@SuppressWarnings({ "unchecked", "rawtypes" })
    protected final <E extends BaseEntity<I>> int compareTo(BaseEntity<I> other, Function<E, Object>... getters) {
		return stream(getters)
			.reduce(
				comparing((Function) getters[0], nullsLast(naturalOrder())),
				(comparator, getter) -> comparator.thenComparing(getter, nullsLast(naturalOrder())),
				(l, r) -> l
			).compare(this, other);
    }

	/**
	 * The default format is <code>ClassName[{id}]</code> where <code>{id}</code> defaults to <code>@hashcode</code> when null.
	 */
    @Override
    public String toString() {
        return toString(e -> requireNonNullElseGet(e.getId(), () -> ("@" + e.hashCode())));
    }

    /**
	 * Subclasses can use this convenience method to override the {@link #toString()} based on given property getters.
	 * @param <E> The generic base entity type.
	 * @param getters The property getters to determine the {@link #toString()} for.
     * @return The {@link Class#getSimpleName()}, then followed by {@code [}, then a comma separated string of the
     * results of all given getters, and finally followed by {@code ]}.
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    protected final <E extends BaseEntity<I>> String toString(Function<E, Object>... getters) {
        return new StringBuilder(getClass().getSimpleName())
        	.append("[")
        	.append(stream(getters).map(getter -> Objects.toString(getter.apply((E) this))).collect(joining(", ")))
        	.append("]")
        	.toString();
    }

}