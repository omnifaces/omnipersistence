package org.omnifaces.persistence.constraint;

import java.util.Map;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;

import org.omnifaces.utils.data.Range;

public final class Between extends Constraint<Range<Comparable<?>>> {

	private Between(Range<Comparable<?>> value) {
		super(value, false);
	}

	public static Between value(Range<Comparable<?>> value) {
		return new Between(value);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Predicate build(String key, CriteriaBuilder criteriaBuilder, Path<?> path, Map<String, Object> parameterValues) {
		Range<Comparable<?>> searchValue = getValue();
		parameterValues.put("min_" + key, searchValue.getMin());
		parameterValues.put("max_" + key, searchValue.getMax());
		Path rawPath = path;
		return criteriaBuilder.between(rawPath,
			criteriaBuilder.parameter(searchValue.getMin().getClass(), "min_" + key),
			criteriaBuilder.parameter(searchValue.getMax().getClass(), "max_" + key));
	}

	@Override
	public boolean applies(Object value) {
		return value instanceof Comparable && getValue().contains((Comparable<?>) value);
	}

	@Override
	public String toString() {
		Range<Comparable<?>> range = getValue();
		return "BETWEEN " + range.getMin() + " AND " + range.getMax();
	}

}
