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
public final class Between extends Criteria<Range<? extends Comparable<?>>> {

	private Between(Range<? extends Comparable<?>> value) {
		super(value);
	}

	public static Between value(Range<? extends Comparable<?>> value) {
		return new Between(value);
	}

	public static <T extends Comparable<T>> Between range(T min, T max) {
		return new Between(Range.ofClosed(min, max));
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		Range<? extends Comparable> searchValue = getValue();
		Expression<? extends Comparable> rawPath = (Expression<? extends Comparable>) path;
		return criteriaBuilder.between(rawPath, parameterBuilder.build(searchValue.getMin()), parameterBuilder.build(searchValue.getMax()));
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
