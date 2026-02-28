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

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * <p>
 * Mapped superclass for versioned entity.
 * It extends from {@link TimestampedBaseEntity} and implements {@link Versioned}.
 * In addition to the "created" and "lastModified" columns, it specifies a {@link Version} column, named "version".
 * The {@link Id} column needs to be manually taken care of.
 * On pre persist, Jakarta Persistence will automatically set version to 0.
 * On pre update, Jakarta Persistence will automatically increment version with 1.
 * This is useful for optimistic locking; Jakarta Persistence will throw {@link jakarta.persistence.OptimisticLockException} when
 * a concurrent update is detected.
 * <p>
 * Usage example:
 * <pre>
 * &#64;Entity
 * public class YourEntity extends VersionedBaseEntity&lt;Long&gt; {
 *
 *     &#64;Id
 *     private Long id;
 *
 *     private String name;
 *
 *     &#64;Override
 *     public Long getId() { return id; }
 *
 *     &#64;Override
 *     public void setId(Long id) { this.id = id; }
 *
 *     // Other getters and setters omitted.
 * }
 * </pre>
 * <p>
 * If you'd like a generated ID instead, use {@link VersionedEntity}.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @since 1.0
 * @see VersionedEntity
 * @see TimestampedBaseEntity
 * @see Versioned
 */
@MappedSuperclass
public abstract class VersionedBaseEntity<I extends Comparable<I> & Serializable> extends TimestampedBaseEntity<I> implements Versioned {

    private static final long serialVersionUID = 1L;

    /** The Jakarta Persistence field name of the {@link #getVersion() version} property, to be used in JPQL queries and criteria maps. */
    public static final String VERSION = "version";

    /** The version for optimistic locking, automatically managed by Jakarta Persistence. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Override
    public Long getVersion() {
        return version;
    }

    // No setter! Jakarta Persistence takes care of this.
}