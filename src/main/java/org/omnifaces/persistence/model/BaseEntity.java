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

import java.io.Serializable;
import java.util.Objects;

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
        return (getId() != null)
            ? Objects.hash(getId())
            : super.hashCode();
    }

    /**
     * Compares by default by entity class (proxies taken into account) and ID.
     */
    @Override
    public boolean equals(Object other) {
        return (getId() != null && getClass().isInstance(other) && other.getClass().isInstance(this))
            ? getId().equals(((BaseEntity<?>) other).getId())
            : (other == this);
    }

    /**
     * Orders by default with "nulls last".
     */
    @Override
    public int compareTo(BaseEntity<I> other) {
        return (other == null)
            ? -1
            : (getId() == null)
                ? (other.getId() == null ? 0 : 1)
                : getId().compareTo(other.getId());
    }

    /**
     * The default format is <code>ClassName[{id}]</code> where <code>{id}</code> defaults to <code>@hashcode</code> when null.
     */
    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), (getId() != null) ? getId() : ("@" + hashCode()));
    }

}