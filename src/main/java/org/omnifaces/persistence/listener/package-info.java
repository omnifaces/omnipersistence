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
 * Jakarta Persistence entity listener which fires the lifecycle callbacks as CDI events.
 * <p>
 * This package contains:
 * <ul>
 * <li>{@link BaseEntityListener} - fires a CDI event qualified with {@link org.omnifaces.persistence.event.Created},
 * {@link org.omnifaces.persistence.event.Updated} or {@link org.omnifaces.persistence.event.Deleted} after the entity is persisted, updated or removed</li>
 * </ul>
 * It is registered on all {@link org.omnifaces.persistence.model.BaseEntity} subclasses via {@code @EntityListeners}.
 *
 * @see org.omnifaces.persistence.event
 */
package org.omnifaces.persistence.listener;
