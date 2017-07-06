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
