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

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * <p>
 * Mapped superclass for optimistically locked entity. It extends from {@link BaseEntity} and implements {@link Versioned}. It specifies a {@link Version}
 * column, named "version". Unlike {@link VersionedBaseEntity}, it has no "created" and "lastModified" columns. The {@link Id} column needs to be manually taken
 * care of. On persist, Jakarta Persistence will automatically initialize the version. On update, Jakarta Persistence will automatically increment the version.
 * This is useful for optimistic locking; Jakarta Persistence will throw {@link jakarta.persistence.OptimisticLockException} when a concurrent update is
 * detected.
 * <p>
 * Usage example:
 *
 * <pre>
 * &#64;Entity
 * public class YourEntity extends LockedBaseEntity&lt;Long&gt; {
 *
 *     &#64;Id
 *     private Long id;
 *
 *     private String name;
 *
 *     &#64;Override
 *     public Long getId() {
 *         return id;
 *     }
 *
 *     &#64;Override
 *     public void setId(Long id) {
 *         this.id = id;
 *     }
 *
 *     // Other getters and setters omitted.
 * }
 * </pre>
 * <p>
 * If you'd like a generated ID instead, use {@link LockedEntity}. If you'd like "created" and "lastModified" columns as well, use {@link VersionedBaseEntity}.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @since 1.2
 * @see LockedEntity
 * @see BaseEntity
 * @see VersionedBaseEntity
 * @see Versioned
 */
@MappedSuperclass
public abstract class LockedBaseEntity<I extends Comparable<I> & Serializable> extends BaseEntity<I> implements Versioned {

    private static final long serialVersionUID = 1L;

    /** The Jakarta Persistence field name of the {@link #getVersion() version} property, to be used in JPQL queries and criteria maps. */
    public static final String VERSION = "version";

    /** The version for optimistic locking, automatically managed by Jakarta Persistence. */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Default constructor.
     */
    protected LockedBaseEntity() {
    }

    @Override
    @Nullable
    public Long getVersion() {
        return version;
    }

    // No setter! Jakarta Persistence takes care of this.
}
