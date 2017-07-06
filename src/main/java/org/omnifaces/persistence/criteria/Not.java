package org.omnifaces.persistence.criteria;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

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
