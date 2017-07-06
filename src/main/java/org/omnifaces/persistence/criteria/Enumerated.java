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

	public static Enumerated value(Enum<?> value) {
		return new Enumerated(value);
	}

	public static Enumerated parse(Object searchValue, Class<Enum<?>> targetType) {
		return new Enumerated(parseEnum(searchValue, targetType));
	}

	@Override
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		return criteriaBuilder.equal(path, parameterBuilder.create(getValue()));
	}

	@Override
	public boolean applies(Object modelValue) {
		return modelValue != null && Objects.equals(parseEnum(modelValue, getValue().getClass()), getValue());
	}

	@SuppressWarnings("unchecked")
	private static Enum<?> parseEnum(Object searchValue, Class<?> targetType) throws IllegalArgumentException {
		if (searchValue instanceof Enum) {
			return (Enum<?>) searchValue;
		}
		else if (targetType.isEnum()) {
			for (Enum<?> enumConstant : ((Class<Enum<?>>) targetType).getEnumConstants()) {
				if (enumConstant.name().equalsIgnoreCase(searchValue.toString())) {
					return enumConstant;
				}
			}
		}

		throw new IllegalArgumentException(searchValue.toString());
	}

}
