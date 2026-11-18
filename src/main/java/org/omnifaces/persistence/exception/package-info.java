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
 * Unchecked exceptions thrown by the persistence layer.
 * <p>
 * All exceptions extend {@link BaseEntityException}:
 * <ul>
 * <li>{@link IllegalEntityStateException} - the entity is in an illegal state for the requested action</li>
 * <li>{@link NonDeletableEntityException} - a delete of a {@link org.omnifaces.persistence.model.NonDeletable} entity</li>
 * <li>{@link NonSoftDeletableEntityException} - a soft delete action on an entity without a {@link org.omnifaces.persistence.model.SoftDeletable} field</li>
 * </ul>
 */
package org.omnifaces.persistence.exception;
