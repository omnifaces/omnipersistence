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

	public static Numeric value(Class<Number> type, Object value) {
		return new Numeric(parseNumber(type, value));
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(path, parameterBuilder.build(getValue()));
	}

	@Override
	public boolean applies(Object value) {
		return value != null && Objects.equals(parseNumber(getValue().getClass(), value), getValue());
	}

	private static Number parseNumber(Class<?> type, Object value) throws NumberFormatException {
		if (value instanceof Number) {
			return (Number) value;
		}

		try {
			if (BigDecimal.class.isAssignableFrom(type)) {
				return new BigDecimal(value.toString());
			}
			else if (BigInteger.class.isAssignableFrom(type)) {
				return new BigInteger(value.toString());
			}
			else if (Integer.class.isAssignableFrom(type)) {
				return Integer.valueOf(value.toString());
			}
			else {
				return Long.valueOf(value.toString());
			}
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException(value.toString(), e);
		}
	}

}
