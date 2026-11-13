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

import static org.omnifaces.utils.stream.Streams.stream;

import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Creates <code>NOT (predicate) OR path IS NULL</code>, or <code>path IS NOT NULL</code> for <code>Not.value(null)</code>.
 * <p>
 * This wraps any value or {@link Criteria} to negate it. The value can be a plain object, or another {@link Criteria} instance. The negation also matches
 * <code>NULL</code>, the same as {@link #applies(Object)}, except when the value is or contains <code>null</code> outside a nested negation.
 * <p>
 * Usage examples:
 *
 * <pre>
 * criteria.put("status", Not.value("INACTIVE")); // status &lt;&gt; 'INACTIVE' OR status IS NULL
 * criteria.put("name", Not.value(Like.contains("test"))); // NOT (LOWER(name) LIKE '%test%') OR name IS NULL
 * criteria.put("role", Not.value(Enumerated.value(Role.ADMIN))); // role &lt;&gt; 'ADMIN' OR role IS NULL
 * criteria.put("email", Not.value(null)); // email IS NOT NULL
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Criteria
 */
public final class Not extends Criteria<Object> {

    private static final long serialVersionUID = 1L;

    private Not(Object value) {
        super(value, true, true);
    }

    /**
     * Returns a new NOT criteria for the given value.
     *
     * @param value The value or {@link Criteria} to negate, may be <code>null</code> to match <code>path IS NOT NULL</code>.
     * @return A new NOT criteria.
     */
    public static Not value(@Nullable Object value) {
        return new Not(value);
    }

    @Override
    public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean applies(@Nullable Object modelValue) {
        return appliesNegated(getValue(), modelValue);
    }

    /**
     * Returns whether given model value does not match given value, the same as the negation in SQL: a multi-value matches when none of its values matches, and
     * a nested negation matches <code>NULL</code> as well.
     */
    private static boolean appliesNegated(@Nullable Object value, @Nullable Object modelValue) {
        if (value instanceof Not not) {
            return modelValue == null || !not.applies(modelValue);
        }
        else if (isMultiValued(value)) {
            return stream(value).allMatch(item -> appliesNegated(item, modelValue));
        }
        else if (value instanceof Criteria<?> criteria) {
            return !criteria.applies(modelValue);
        }
        else {
            return !Objects.equals(modelValue, value);
        }
    }

}
