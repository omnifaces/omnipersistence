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
 * CDI qualifiers for the entity lifecycle events fired by {@link org.omnifaces.persistence.listener.BaseEntityListener}.
 * <p>
 * This package contains:
 * <ul>
 * <li>{@link Created} - qualifies the event fired on the Jakarta Persistence {@code @PostPersist} callback</li>
 * <li>{@link Updated} - qualifies the event fired on the Jakarta Persistence {@code @PostUpdate} callback</li>
 * <li>{@link Deleted} - qualifies the event fired on the Jakarta Persistence {@code @PostRemove} callback</li>
 * </ul>
 *
 * @see org.omnifaces.persistence.listener
 */
package org.omnifaces.persistence.event;
