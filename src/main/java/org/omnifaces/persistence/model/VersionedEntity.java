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
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * <p>
 * Mapped superclass for versioned entity with generated ID.
 * It extends from {@link TimestampedEntity} which in turn extends from {@link GeneratedIdEntity}, and implements {@link Versioned}.
 * In addition to the "id", "created" and "lastModified" columns, it specifies a {@link Version} column, named "version".
 * On pre persist, Jakarta Persistence will automatically set version to 0.
 * On pre update, Jakarta Persistence will automatically increment version with 1.
 * This is useful for optimistic locking; Jakarta Persistence will throw {@link jakarta.persistence.OptimisticLockException} when
 * a concurrent update is detected.
 * <p>
 * Usage example:
 * <pre>
 * &#64;Entity
 * public class YourEntity extends VersionedEntity&lt;Long&gt; {
 *
 *     private String name;
 *
 *     // Getters and setters omitted.
 * }
 * </pre>
 * <p>
 * If you'd like to manually define the ID column instead, use {@link VersionedBaseEntity}.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @since 1.0
 * @see VersionedBaseEntity
 * @see TimestampedEntity
 * @see Versioned
 */
@MappedSuperclass
public abstract class VersionedEntity<I extends Comparable<I> & Serializable> extends TimestampedEntity<I> implements Versioned {

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