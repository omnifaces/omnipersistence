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
/**
 * Mapped superclasses for entities, and the model annotations.
 * <p>
 * Extend the base class which provides the columns the entity needs:
 * <ul>
 * <li>{@link BaseEntity} - identity only; {@code @Id} manual</li>
 * <li>{@link GeneratedIdEntity} - adds a generated {@code id}</li>
 * <li>{@link TimestampedBaseEntity} - adds {@code created} and {@code lastModified}; {@code @Id} manual</li>
 * <li>{@link TimestampedEntity} - adds a generated {@code id}, {@code created} and {@code lastModified}</li>
 * <li>{@link VersionedBaseEntity} - adds {@code @Version} on top of {@link TimestampedBaseEntity}; {@code @Id} manual</li>
 * <li>{@link VersionedEntity} - adds {@code @Version} on top of {@link TimestampedEntity}</li>
 * <li>{@link LockedBaseEntity} - adds {@code @Version} on top of {@link BaseEntity}; {@code @Id} manual</li>
 * <li>{@link LockedEntity} - adds {@code @Version} on top of {@link GeneratedIdEntity}</li>
 * </ul>
 * "{@code @Id} manual" means that the entity must declare its own {@code @Id}.
 * <p>
 * All base classes implement {@code equals}, {@code hashCode}, {@code compareTo} and {@code toString} based on the entity ID, which
 * {@link BaseEntity#identityGetters()} can override.
 * <p>
 * The base interfaces:
 * <ul>
 * <li>{@link Timestamped} - implemented by the timestamped base classes</li>
 * <li>{@link Versioned} - implemented by the versioned and locked base classes</li>
 * </ul>
 * <p>
 * The model annotations:
 * <ul>
 * <li>{@link SoftDeletable} - marks the boolean field or getter which holds the deleted or active state, for soft delete and undelete</li>
 * <li>{@link NonDeletable} - makes {@link org.omnifaces.persistence.service.BaseEntityService#delete(Object)} throw
 * {@link org.omnifaces.persistence.exception.NonDeletableEntityException}</li>
 * </ul>
 *
 * @see org.omnifaces.persistence.service.BaseEntityService
 */
package org.omnifaces.persistence.model;
