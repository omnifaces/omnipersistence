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
 * Query definitions which {@link org.omnifaces.persistence.service.BaseEntityService} executes.
 * <p>
 * Contains {@link org.omnifaces.persistence.query.Select}, an immutable value object that bundles a JPQL statement, a criteria query builder or all entities
 * with query options. It is used by {@link org.omnifaces.persistence.service.BaseEntityService#list(Select)} and its <code>find</code> and
 * <code>findFirst</code> siblings. Build instances via {@link org.omnifaces.persistence.query.Select#jpql(String, Object...)} and its siblings.
 *
 * @see org.omnifaces.persistence.query.Select
 * @see org.omnifaces.persistence.service.BaseEntityService
 */
package org.omnifaces.persistence.query;
