/*
 * Copyright 2020 OmniFaces
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
 * Creates <code>path NOT criteria</code>.
 *
 * @author Bauke Scholtz
 */
public final class Not extends Criteria<Object> {

	private Not(Object value) {
		super(value, true);
	}

	public static Not value(Object value) {
		return new Not(value);
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean applies(Object modelValue) {
		if (modelValue instanceof Criteria) {
			return !((Criteria<?>) modelValue).applies(getValue());
		}
		else {
			return !Objects.equals(modelValue, getValue());
		}
	}

}
