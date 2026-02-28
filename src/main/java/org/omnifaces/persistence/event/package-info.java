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
 * CDI qualifier annotations for entity lifecycle events fired by
 * {@link org.omnifaces.persistence.listener.BaseEntityListener}.
 * <p>
 * Observe {@code @}{@link Created},
 * {@code @}{@link Updated} or
 * {@code @}{@link Deleted} to react to Jakarta Persistence
 * {@code @PrePersist}, {@code @PreUpdate} and {@code @PreRemove} lifecycle callbacks respectively.
 *
 * @see org.omnifaces.persistence.listener.BaseEntityListener
 */
package org.omnifaces.persistence.event;
