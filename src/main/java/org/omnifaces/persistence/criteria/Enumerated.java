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
package org.omnifaces.persistence.criteria;

import java.util.Objects;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Creates <code>path = enum</code>.
 * <p>
 * Supports case insensitive enum name matching when parsing from a string value.
 * <p>
 * Usage examples:
 * <pre>
 * criteria.put("status", Enumerated.value(Status.ACTIVE));    // status = 'ACTIVE'
 * criteria.put("status", Enumerated.parse("active", type));   // status = 'ACTIVE' (case insensitive)
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Criteria
 */
public final class Enumerated extends Criteria<Enum<?>> {

    private Enumerated(Enum<?> value) {
        super(value, false, true);
    }

    /**
     * Returns a new enumerated criteria for the given enum constant.
     * @param value The enum constant.
     * @return A new enumerated criteria.
     */
    public static Enumerated value(Enum<?> value) {
        return new Enumerated(value);
    }

    /**
     * Returns a new enumerated criteria for the given search value, parsed against the given target enum type.
     * <p>
     * Parsing is case-insensitive by default.
     * @param searchValue The search value to parse (usually a String).
     * @param targetType The target enum class.
     * @return A new enumerated criteria.
     * @throws IllegalArgumentException If the search value cannot be mapped to an enum constant.
     */
    public static Enumerated parse(Object searchValue, Class<Enum<?>> targetType) {
        return new Enumerated(parseEnum(searchValue, targetType));
    }

    @Override
    public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        return getValue() == null ? null : criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
    }

    @Override
    public boolean applies(Object modelValue) {
        return getValue() == null ? modelValue == null : modelValue != null && Objects.equals(parseEnum(modelValue, getValue().getClass()), getValue());
    }

    @SuppressWarnings("unchecked")
    private static Enum<?> parseEnum(Object searchValue, Class<?> targetType) throws IllegalArgumentException {
        if (searchValue instanceof Enum) {
            return (Enum<?>) searchValue;
        }
        else if (targetType.isEnum()) {
            var criteria = searchValue instanceof Criteria c ? c : IgnoreCase.value(searchValue.toString());

            for (Enum<?> enumConstant : ((Class<Enum<?>>) targetType).getEnumConstants()) {
                if (criteria.applies(enumConstant.name())) {
                    return enumConstant;
                }
            }

            if (searchValue instanceof Criteria) {
                return null;
            }
        }

        throw new IllegalArgumentException(searchValue.toString());
    }

}
