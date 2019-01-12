/*
 * Copyright 2019 OmniFaces
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

import static org.omnifaces.persistence.JPA.castAsString;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>LOWER(path) = LOWER(value)</code>.
 *
 * @author Bauke Scholtz
 */
public final class IgnoreCase extends Criteria<String> {

	private IgnoreCase(String value) {
		super(value);
	}

	public static IgnoreCase value(String value) {
		return new IgnoreCase(value);
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(criteriaBuilder.lower(castAsString(criteriaBuilder, path)), criteriaBuilder.lower(parameterBuilder.create(getValue())));
	}

	@Override
	public boolean applies(Object modelValue) {
		return modelValue != null && modelValue.toString().equalsIgnoreCase(getValue());
	}

}
