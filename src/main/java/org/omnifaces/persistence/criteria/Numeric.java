package org.omnifaces.persistence.criteria;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>path = number</code>.
 *
 * @author Bauke Scholtz
 */
public final class Numeric extends Criteria<Number> {

	private Numeric(Number value) {
		super(value);
	}

	public static Numeric value(Number value) {
		return new Numeric(value);
	}

	public static Numeric parse(Object searchValue, Class<Number> targetType) {
		return new Numeric(parseNumber(searchValue, targetType));
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
	}

	@Override
	public boolean applies(Object modelValue) {
		return modelValue != null && Objects.equals(parseNumber(modelValue, getValue().getClass()), getValue());
	}

	private static Number parseNumber(Object searchValue, Class<?> targetType) throws NumberFormatException {
		if (searchValue instanceof Number) {
			return (Number) searchValue;
		}

		try {
			if (BigDecimal.class.isAssignableFrom(targetType)) {
				return new BigDecimal(searchValue.toString());
			}
			else if (BigInteger.class.isAssignableFrom(targetType)) {
				return new BigInteger(searchValue.toString());
			}
			else if (Integer.class.isAssignableFrom(targetType)) {
				return Integer.valueOf(searchValue.toString());
			}
			else {
				return Long.valueOf(searchValue.toString());
			}
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException(searchValue.toString(), e);
		}
	}

}
