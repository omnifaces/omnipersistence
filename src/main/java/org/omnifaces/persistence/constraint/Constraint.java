package org.omnifaces.persistence.constraint;

import java.util.Map;
import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;

import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * This is used by {@link Page} and {@link BaseEntityService#getPage(Page, boolean)}. It defines a set of constraints
 * which could be supplied as value of "required criteria" and "optional criteria" maps.
 * <p>
 * There are so far the following constraints:
 * <ul>
 * <li>{@link Not} - to negate the value
 * <li>{@link Like} - to search a string value
 * <li>{@link Order} - to perform "less than" or "greater than" searches
 * <li>{@link Between} - to perform "between" searches
 * </ul>
 * <p>
 * An elaborate use case can be found in <a href="https://github.com/omnifaces/optimusfaces">OptimusFaces</a> project.
 *
 * @author Bauke Scholtz
 * @param <T> The generic constraint value type.
 */
public abstract class Constraint<T> {

	private T value;

	protected Constraint(T value, boolean nestable) {
		if (value instanceof Constraint && (!nestable || value.getClass() == getClass())) {
			throw new IllegalArgumentException("You cannot nest " + value + " in " + this);
		}

		if (!nestable && value == null) {
			throw new NullPointerException("value");
		}

		this.value = value;
	}

	public abstract Predicate build(String key, CriteriaBuilder criteriaBuilder, Path<?> path, Map<String, Object> parameterValues);

	public abstract boolean applies(Object value);

	public T getValue() {
		return value;
	}

	public static Object unwrap(Object possibleConstraint) {
		Object value = possibleConstraint;

		while (value instanceof Constraint) {
			value = ((Constraint<?>) possibleConstraint).getValue();
		}

		return value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(getClass(), value);
	}

	@Override
	public boolean equals(Object object) {
		return object != null
			&& getClass().isAssignableFrom(object.getClass())
			&& (object == this || (Objects.equals(value, ((Constraint<?>) object).value)));
	}

}

