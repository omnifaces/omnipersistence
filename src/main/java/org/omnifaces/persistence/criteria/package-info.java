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
 * Criteria values for use in {@link org.omnifaces.persistence.query.Page} search criteria maps.
 * <p>
 * A plain value in a criteria map matches by equality. Wrap it in one of the {@link Criteria} subclasses for other matching:
 * <ul>
 * <li>{@link Like} - case-insensitive substring, prefix or suffix match</li>
 * <li>{@link Between} - range match on any {@link Comparable}</li>
 * <li>{@link Order} - less than or greater than comparison, with or without equality ({@code <}, {@code <=}, {@code >}, {@code >=})</li>
 * <li>{@link Not} - negation of a plain value or of another criteria value other than {@link Not}</li>
 * <li>{@link IgnoreCase} - case-insensitive exact match</li>
 * <li>{@link Enumerated} - match on an enum constant, optionally parsed case-insensitively from a string</li>
 * <li>{@link Numeric} - converts a string value to a number before matching</li>
 * <li>{@link Bool} - converts a truthy string such as {@code "1"} or {@code "true"} to a boolean before matching</li>
 * </ul>
 * Custom criteria can be added by extending {@link Criteria}.
 *
 * @see org.omnifaces.persistence.query.Page
 * @see org.omnifaces.persistence.service.BaseEntityService
 */
package org.omnifaces.persistence.criteria;
