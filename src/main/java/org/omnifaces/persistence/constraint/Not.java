package org.omnifaces.persistence.constraint;

import java.util.Map;
import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

public final class Not extends Constraint<Object> {

	private Not(Object value) {
		super(value, true);
	}

	public static Not value(Object value) {
		return new Not(value);
	}

	@Override
	public Predicate build(Expression<?> expression, String key, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean applies(Object value) {
		if (value instanceof Constraint) {
			return !((Constraint<?>) value).applies(getValue());
		}
		else {
			return !Objects.equals(value, getValue());
		}
	}

	@Override
	public String toString() {
		return "NOT(" + getValue() + ")";
	}

}
