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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import org.omnifaces.utils.data.Range;

/**
 * Creates <code>path BETWEEN range.min AND range.max</code>.
 *
 * @author Bauke Scholtz
 */
public final class Between<T extends Comparable<T>> extends Criteria<Range<T>> {

	private Between(Range<T> value) {
		super(value);
	}

	public static <T extends Comparable<T>> Between<T> value(Range<T> value) {
		return new Between<>(value);
	}

	public static <T extends Comparable<T>> Between<T> range(T min, T max) {
		return new Between<>(Range.ofClosed(min, max));
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.between((Expression<T>) path, parameterBuilder.create(getValue().getMin()), parameterBuilder.create(getValue().getMax()));
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean applies(Object modelValue) {
		return modelValue instanceof Comparable && getValue().contains((T) modelValue);
	}

	@Override
	public String toString() {
		Range<? extends Comparable<?>> range = getValue();
		return "BETWEEN " + range.getMin() + " AND " + range.getMax();
	}

}
