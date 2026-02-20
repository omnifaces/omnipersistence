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
 * Data transfer objects for the persistence layer.
 * <p>
 * Contains {@link org.omnifaces.persistence.model.dto.Page}, an immutable value object that bundles a pagination range,
 * ordering directives and search criteria maps for use with
 * {@link org.omnifaces.persistence.service.BaseEntityService#getPage(Page, boolean)}.
 * Build instances via {@link org.omnifaces.persistence.model.dto.Page#with()}.
 *
 * @see org.omnifaces.persistence.model.dto.Page
 * @see org.omnifaces.persistence.service.BaseEntityService
 * @see org.omnifaces.persistence.criteria
 */
package org.omnifaces.persistence.model.dto;
