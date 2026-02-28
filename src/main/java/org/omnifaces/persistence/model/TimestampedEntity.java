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

import static java.time.Instant.now;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;

/**
 * <p>
 * Mapped superclass for timestamped entity with generated ID.
 * It extends from {@link GeneratedIdEntity} which in turn extends from {@link BaseEntity}, and implements {@link Timestamped}.
 * In addition to the "id" column, it specifies two timestamp columns, named "created" and "lastModified".
 * On pre persist, the both columns will be set to current timestamp.
 * On pre update, the "lastModified" column will be set to current timestamp, unless {@link #skipAdjustLastModified()} is called beforehand.
 * <p>
 * Usage example:
 * <pre>
 * &#64;Entity
 * public class YourEntity extends TimestampedEntity&lt;Long&gt; {
 *
 *     private String name;
 *
 *     // Getters and setters omitted.
 * }
 * </pre>
 * <p>
 * If you'd like to manually define the ID column instead, use {@link TimestampedBaseEntity}.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @since 1.0
 * @see TimestampedBaseEntity
 * @see VersionedEntity
 * @see Timestamped
 */
@MappedSuperclass
public abstract class TimestampedEntity<I extends Comparable<I> & Serializable> extends GeneratedIdEntity<I> implements Timestamped {

    private static final long serialVersionUID = 1L;

    /** The Jakarta Persistence field name of the {@link #getCreated() created} property, to be used in JPQL queries and criteria maps. */
    public static final String CREATED = "created";

    /** The Jakarta Persistence field name of the {@link #getLastModified() lastModified} property, to be used in JPQL queries and criteria maps. */
    public static final String LAST_MODIFIED = "lastModified";

    /** The timestamp when this entity was first persisted. */
    @Column(nullable = false)
    private Instant created;

    /** The timestamp when this entity was last modified. */
    @Column(nullable = false)
    private Instant lastModified;

    /** Internal flag to skip the automatic adjustment of the last modified timestamp during {@link #onPreUpdate()}. */
    @Transient
    private boolean skipAdjustLastModified;

    /**
     * Sets the created and lastModified timestamps to the current time before persisting.
     */
    @PrePersist
    public void onPrePersist() {
        Instant timestamp = now();
        setCreated(timestamp);
        setLastModified(timestamp);
    }

    /**
     * Sets the lastModified timestamp to the current time before updating, unless skipped.
     */
    @PreUpdate
    public void onPreUpdate() {
        if (!skipAdjustLastModified) {
            Instant timestamp = now();
            setLastModified(timestamp);
        }
    }

    /**
     * Invoke this method if you need to skip adjusting the "last modified" timestamp during any update event on this
     * instance. In case you intend to reset this later on, simply obtain a new instance from the entity manager.
     */
    public void skipAdjustLastModified() {
        this.skipAdjustLastModified = true;
    }

    @Override
    public void setCreated(Instant created) {
        this.created = created;
    }

    @Override
    public Instant getCreated() {
        return created;
    }

    @Override
    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public Instant getLastModified() {
        return lastModified;
    }

}