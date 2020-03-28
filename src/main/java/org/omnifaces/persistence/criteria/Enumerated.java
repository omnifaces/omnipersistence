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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>path = enum</code>.
 *
 * @author Bauke Scholtz
 */
public final class Enumerated extends Criteria<Enum<?>> {

	private Enumerated(Enum<?> value) {
		super(value);
	}

	public static Enumerated value(Enum<?> value) {
		return new Enumerated(value);
	}

	public static Enumerated parse(Object searchValue, Class<Enum<?>> targetType) {
		return new Enumerated(parseEnum(searchValue, targetType));
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
	}

	@Override
	public boolean applies(Object modelValue) {
		return modelValue != null && Objects.equals(parseEnum(modelValue, getValue().getClass()), getValue());
	}

	@SuppressWarnings("unchecked")
	private static Enum<?> parseEnum(Object searchValue, Class<?> targetType) throws IllegalArgumentException {
		if (searchValue instanceof Enum) {
			return (Enum<?>) searchValue;
		}
		else if (targetType.isEnum()) {
			for (Enum<?> enumConstant : ((Class<Enum<?>>) targetType).getEnumConstants()) {
				if (enumConstant.name().equalsIgnoreCase(searchValue.toString())) {
					return enumConstant;
				}
			}
		}

		throw new IllegalArgumentException(searchValue.toString());
	}

}
