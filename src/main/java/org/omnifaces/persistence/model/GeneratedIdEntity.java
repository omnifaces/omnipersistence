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

import static jakarta.persistence.GenerationType.IDENTITY;

import java.io.Serializable;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * <p>
 * Mapped superclass for entity with generated ID.
 * It extends from {@link BaseEntity}.
 * It specifies a {@link Id} column, named "id", with {@link GeneratedValue} strategy {@link jakarta.persistence.GenerationType#IDENTITY IDENTITY}.
 * JPA will automatically take care of the ID.
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
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 * @see BaseEntity
 * @see TimestampedEntity
 * @see VersionedEntity
 */
@MappedSuperclass
public abstract class GeneratedIdEntity<I extends Comparable<I> & Serializable> extends BaseEntity<I> {

    private static final long serialVersionUID = 1L;

    /** The JPA field name of the {@link #getId() id} property, to be used in JPQL queries and criteria maps. */
    public static final String ID = "id";

    @Id @GeneratedValue(strategy = IDENTITY)
    private I id;

    @Override
    public I getId() {
        return id;
    }

    @Override
    public void setId(I id) {
        this.id = id;
    }

}
