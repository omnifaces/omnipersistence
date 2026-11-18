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
 * Services for Jakarta Persistence entities.
 * <p>
 * This package contains:
 * <ul>
 * <li>{@link BaseEntityService} - generic service with find, list, persist, update, save, delete, soft delete, batch and paging actions; extend it and annotate
 * it with {@code @Stateless} or {@code @ApplicationScoped}</li>
 * <li>{@link EntityRepository} - offers the actions of {@link BaseEntityService} for a given entity type without extending it; inject it with concrete type
 * arguments</li>
 * <li>{@link EntityRepositoryExtension} - CDI extension which makes the {@link EntityRepository} injection work on OpenWebBeans</li>
 * <li>{@link RootWrapper} - wrapper of a {@link jakarta.persistence.criteria.Root}, to decorate it</li>
 * <li>{@link FetchWrapper} - wrapper of a {@link jakarta.persistence.criteria.Fetch}, to decorate it</li>
 * </ul>
 * All other types in this package are package-private implementation helpers.
 *
 * @see org.omnifaces.persistence.model.BaseEntity
 * @see org.omnifaces.persistence.query
 */
package org.omnifaces.persistence.service;
