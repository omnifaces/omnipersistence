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
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * <p>
 * Mapped superclass for optimistically locked entity with generated ID. It extends from {@link GeneratedIdEntity} and implements {@link Versioned}. In addition
 * to the "id" column, it specifies a {@link Version} column, named "version". Unlike {@link VersionedEntity}, it has no "created" and "lastModified" columns.
 * On persist, Jakarta Persistence will automatically initialize the version. On update, Jakarta Persistence will automatically increment the version. This is
 * useful for optimistic locking; Jakarta Persistence will throw {@link jakarta.persistence.OptimisticLockException} when a concurrent update is detected.
 * <p>
 * Usage example:
 *
 * <pre>
 * &#64;Entity
 * public class YourEntity extends LockedEntity&lt;Long&gt; {
 *
 *     private String name;
 *
 *     // Getters and setters omitted.
 * }
 * </pre>
 * <p>
 * If you'd like to manually define the ID column instead, use {@link LockedBaseEntity}. If you'd like "created" and "lastModified" columns as well, use
 * {@link VersionedEntity}.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @since 1.2
 * @see LockedBaseEntity
 * @see GeneratedIdEntity
 * @see VersionedEntity
 * @see Versioned
 */
@MappedSuperclass
public abstract class LockedEntity<I extends Comparable<I> & Serializable> extends GeneratedIdEntity<I> implements Versioned {

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
    protected LockedEntity() {
    }

    @Override
    @Nullable
    public Long getVersion() {
        return version;
    }

    // No setter! Jakarta Persistence takes care of this.
}
