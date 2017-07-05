package org.omnifaces.persistence.criteria;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>path = enum</code>.
 *
 * @author Bauke Scholtz
 */
public final class Enumerated extends Criteria<Enum<?>> {

	private Enumerated(Enum<?> value) {
		super(value);
	}

	public static Enumerated value(Class<Enum<?>> type, Object value) {
		return new Enumerated(parseEnum(type, value));
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(path, parameterBuilder.build(getValue()));
	}

	@Override
	public boolean applies(Object value) {
		return value != null && Objects.equals(parseEnum(getValue().getClass(), value), getValue());
	}

	@SuppressWarnings("unchecked")
	private static Enum<?> parseEnum(Class<?> type, Object value) throws IllegalArgumentException {
		if (value instanceof Enum) {
			return (Enum<?>) value;
		}
		else if (type.isEnum()) {
			for (Enum<?> enumConstant : ((Class<Enum<?>>) type).getEnumConstants()) {
				if (enumConstant.name().equalsIgnoreCase(value.toString())) {
					return enumConstant;
				}
			}
		}

		throw new IllegalArgumentException(value.toString());
	}

}
