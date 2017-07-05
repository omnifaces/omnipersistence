package org.omnifaces.persistence.constraint;

import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import org.omnifaces.utils.data.Range;

public final class Between extends Constraint<Range<? extends Comparable<?>>> {

	private Between(Range<? extends Comparable<?>> value) {
		super(value, false);
	}

	public static Between value(Range<? extends Comparable<?>> value) {
		return new Between(value);
	}

	public static <T extends Comparable<T>> Between range(T min, T max) {
		return new Between(Range.ofClosed(min, max));
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Predicate build(Expression<?> expression, String key, CriteriaBuilder criteriaBuilder, Map<String, Object> parameterValues) {
		Range<? extends Comparable<?>> searchValue = getValue();
		parameterValues.put("min_" + key, searchValue.getMin());
		parameterValues.put("max_" + key, searchValue.getMax());
		Expression rawExpression = expression;
		return criteriaBuilder.between(rawExpression,
			criteriaBuilder.parameter(searchValue.getMin().getClass(), "min_" + key),
			criteriaBuilder.parameter(searchValue.getMax().getClass(), "max_" + key));
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean applies(Object value) {
		Range rawRange = getValue();
		return value instanceof Comparable && rawRange.contains(value);
	}

	@Override
	public String toString() {
		Range<? extends Comparable<?>> range = getValue();
		return "BETWEEN " + range.getMin() + " AND " + range.getMax();
	}

}
