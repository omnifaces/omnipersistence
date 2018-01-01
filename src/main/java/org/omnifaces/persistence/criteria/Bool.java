/*
 * Copyright 2018 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.criteria;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>path IS (NOT) TRUE</code>.
 *
 * @author Bauke Scholtz
 */
public final class Bool extends Criteria<Boolean> {

	private Bool(Boolean value) {
		super(value);
	}

	public static Bool value(Boolean value) {
		return new Bool(value);
	}

	public static Bool parse(Object searchValue) {
		return new Bool(parseBoolean(searchValue));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		Predicate predicate = criteriaBuilder.isTrue((Expression<Boolean>) path);
		return getValue() ? predicate : criteriaBuilder.not(predicate);
	}

	@Override
	public boolean applies(Object modelValue) {
		return Objects.equals(parseBoolean(modelValue), getValue());
	}

	private static Boolean parseBoolean(Object value) {
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		else if (value instanceof Number) {
			return ((Number) value).intValue() > 0;
		}
		else {
			return Boolean.parseBoolean(value.toString());
		}
	}

}
