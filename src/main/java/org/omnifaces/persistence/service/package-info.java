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
 * Service layer for Jakarta Persistence entities.
 * <p>
 * The sole public entry point is {@link BaseEntityService}, a generic CRUD service
 * that supports lookup, persist, update, delete, soft-delete, batch operations, JPQL shortcuts, lazy collection
 * fetching, and cursor- and offset-based pagination via {@link org.omnifaces.persistence.model.dto.Page}.
 * Extend it and annotate with {@code @Stateless} or {@code @ApplicationScoped} to get a fully functional
 * service for any {@link org.omnifaces.persistence.model.BaseEntity} subclass.
 * <p>
 * All other types in this package are package-private implementation helpers.
 *
 * @see BaseEntityService
 * @see org.omnifaces.persistence.model.BaseEntity
 * @see org.omnifaces.persistence.model.dto.Page
 */
package org.omnifaces.persistence.service;
