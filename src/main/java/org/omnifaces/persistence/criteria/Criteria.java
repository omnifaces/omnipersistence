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
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;

import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * This is used by {@link Page} and {@link BaseEntityService#getPage(Page, boolean)}. It defines a set of criteria
 * which could be supplied as value of "required criteria" and "optional criteria" maps.
 * <p>
 * There are so far the following criteria:
 * <ul>
 * <li>{@link Not} - to negate the value
 * <li>{@link Like} - to search a string value
 * <li>{@link Order} - to perform "less than" or "greater than" searches
 * <li>{@link Between} - to perform "between" searches
 * <li>{@link Enumerated} - to parse value as enum
 * <li>{@link Numeric} - to parse value as number
 * <li>{@link Bool} - to parse value as boolean
 * <li>{@link IgnoreCase} - to perform "ignore case" searches
 * </ul>
 * <p>
 * You can create your own ones if you want to have more fine grained control over how criteria values are parsed and turned into a predicate.
 * <p>
 * An elaborate use case can be found in <a href="https://github.com/omnifaces/optimusfaces">OptimusFaces</a> project.
 *
 * @author Bauke Scholtz
 * @param <T> The generic criteria value type.
 * @see Page
 * @see BaseEntityService
 */
public abstract class Criteria<T> {

	private T value;

	/**
	 * Create criteria based on given value.
	 * @param value The criteria value.
	 * @throws IllegalArgumentException When given criteria value cannot be reasonably parsed.
	 */
	protected Criteria(T value) {
		this(value, false);
	}

	Criteria(T value, boolean nestable) {
		if (value instanceof Criteria && (!nestable || value.getClass() == getClass())) {
			throw new IllegalArgumentException("You cannot nest " + value + " in " + this);
		}

		if (!nestable && value == null) {
			throw new NullPointerException("value");
		}

		this.value = value;
	}

	/**
	 * Returns a predicate for the criteria value. Below is an example implementation:
	 * <pre>
	 * return criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
	 * </pre>
	 * @param path Entity property path. You can use this to inspect the target entity property.
	 * @param criteriaBuilder So you can build a predicate with a {@link ParameterExpression}.
	 * @param parameterBuilder You must use this to create a {@link ParameterExpression} for the criteria value.
	 * @return A predicate for the criteria value.
	 */
	public abstract Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder);

	/**
	 * Returns whether this criteria value would apply to the given model value. This must basically represent the "plain Java"
	 * equivalent of the SQL behavior as achieved by {@link #build(Expression, CriteriaBuilder, ParameterBuilder)}.
	 * @param modelValue The model value to test this criteria on.
	 * @return Whether this criteria value would apply to the given model value.
	 * @throws IllegalArgumentException When given model value cannot be reasonably parsed.
	 * @throws UnsupportedOperationException When this method is not implemented yet.
	 */
	public boolean applies(Object modelValue) {
		throw new UnsupportedOperationException("This method is not implemented yet.");
	}

	/**
	 * Returns the criteria value.
	 * @return The criteria value.
	 */
	public T getValue() {
		return value;
	}

	/**
	 * Unwraps the criteria value from given object which could possibly represent a {@link Criteria}.
	 * @param possibleCriteria Any object which could possibly represent a {@link Criteria}.
	 * @return The unwrapped criteria value when given object actually represents a {@link Criteria}, else the original value unmodified.
	 */
	public static Object unwrap(Object possibleCriteria) {
		Object value = possibleCriteria;

		while (value instanceof Criteria) {
			value = ((Criteria<?>) possibleCriteria).getValue();
		}

		return value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(getClass(), value);
	}

	@Override
	public boolean equals(Object object) {
		return getClass().isInstance(object) && (object == this || (Objects.equals(value, ((Criteria<?>) object).value)));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName().toUpperCase() + "(" + getValue() + ")";
	}

	/**
	 * This is used in {@link Criteria#build(Expression, CriteriaBuilder, ParameterBuilder)}.
	 */
	@FunctionalInterface
	public interface ParameterBuilder {
		<T> ParameterExpression<T> create(Object value);
	}

}
