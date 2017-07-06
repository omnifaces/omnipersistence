package org.omnifaces.persistence.criteria;

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
	@SuppressWarnings("unchecked")
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(criteriaBuilder.lower((Expression<String>) path), criteriaBuilder.lower(parameterBuilder.create(getValue())));
	}

	@Override
	public boolean applies(Object modelValue) {
		return modelValue != null && modelValue.toString().equalsIgnoreCase(getValue());
	}

}
