/*
 * Copyright 2021 OmniFaces
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
package org.omnifaces.persistence.service;

import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.ParameterExpression;

import org.omnifaces.persistence.criteria.Criteria.ParameterBuilder;

/**
 * Helper class of {@link BaseEntityService}.
 */
class UncheckedParameterBuilder implements ParameterBuilder {

	private final String field;
	private final CriteriaBuilder criteriaBuilder;
	private final Map<String, Object> parameters;

	public UncheckedParameterBuilder(String field, CriteriaBuilder criteriaBuilder, Map<String, Object> parameters) {
		this.field = field.replace('.', '$') + "_";
		this.criteriaBuilder = criteriaBuilder;
		this.parameters = parameters;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> ParameterExpression<T> create(Object value) {
		String name = field + parameters.size();
		parameters.put(name, value);
		Class<? extends Object> type = (value == null) ? Object.class : value.getClass();
		return (ParameterExpression<T>) criteriaBuilder.parameter(type, name);
	}

}

