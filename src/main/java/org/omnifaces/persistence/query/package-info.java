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
 * This package contains:
 * <ul>
 * <li>{@link Page} - immutable paged view with a range, an ordering and search criteria, for use with
 * {@link org.omnifaces.persistence.service.BaseEntityService#getPage(Page, boolean)}, built via {@link Page#with()}</li>
 * <li>{@link Select} - immutable selection by a JPQL statement, a criteria query builder or all entities, with query options, built via
 * {@link Select#jpql(String, Object...)} and its siblings, for use with {@link org.omnifaces.persistence.service.EntityRepository#list(Select)} and its
 * {@code find} and {@code findFirst} siblings, or with the same protected methods of {@link org.omnifaces.persistence.service.BaseEntityService} in a
 * subclass</li>
 * </ul>
 *
 * @see org.omnifaces.persistence.criteria
 * @see org.omnifaces.persistence.service.BaseEntityService
 */
package org.omnifaces.persistence.query;
