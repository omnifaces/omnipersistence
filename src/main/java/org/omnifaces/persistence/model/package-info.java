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
 * Mapped superclass hierarchy and associated model annotations.
 * <p>
 * Extend the appropriate base class depending on which columns your entity needs:
 * <ul>
 * <li>{@link BaseEntity} — identity only; {@code @Id} must be defined manually.
 * <li>{@link GeneratedIdEntity} — adds auto-generated {@code id}.
 * <li>{@link TimestampedBaseEntity} — adds {@code created} and {@code lastModified}; {@code @Id} manual.
 * <li>{@link TimestampedEntity} — adds auto-generated {@code id}, {@code created} and {@code lastModified}.
 * <li>{@link VersionedBaseEntity} — adds {@code @Version} on top of {@link TimestampedBaseEntity}; {@code @Id} manual.
 * <li>{@link VersionedEntity} — adds {@code @Version} on top of {@link TimestampedEntity}.
 * </ul>
 * <p>
 * All base classes provide correct {@code equals}, {@code hashCode}, {@code compareTo} and {@code toString}
 * based on entity ID by default, overridable via {@link BaseEntity#identityGetters()}.
 * <p>
 * Supporting model annotations:
 * <ul>
 * <li>{@link SoftDeletable} — marks the boolean field used for logical deletion.
 * <li>{@link NonDeletable} — prevents hard deletes at the service level.
 * </ul>
 *
 * @see BaseEntity
 * @see org.omnifaces.persistence.service.BaseEntityService
 */
package org.omnifaces.persistence.model;
