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
 * Typed criteria value wrappers for use in {@link org.omnifaces.persistence.model.dto.Page} search maps.
 * <p>
 * Plain values in a criteria map produce exact-equality predicates. Wrap them in one of the concrete
 * {@link Criteria} subclasses for richer matching:
 * <ul>
 * <li>{@link Like} — substring, prefix or suffix match (case-insensitive).
 * <li>{@link Between} — range match on any {@link Comparable}.
 * <li>{@link Order} — less-than or greater-than comparison.
 * <li>{@link Not} — negation wrapper around any other criteria value.
 * <li>{@link IgnoreCase} — case-insensitive exact match.
 * <li>{@link Enumerated} — case-insensitive enum name match.
 * <li>{@link Numeric} — coerces a string value to a number before matching.
 * <li>{@link Bool} — coerces truthy strings ({@code "1"}, {@code "true"}, …) to boolean.
 * </ul>
 * Custom criteria can be added by extending {@link Criteria}.
 *
 * @see Criteria
 * @see org.omnifaces.persistence.model.dto.Page
 * @see org.omnifaces.persistence.service.BaseEntityService
 */
package org.omnifaces.persistence.criteria;
