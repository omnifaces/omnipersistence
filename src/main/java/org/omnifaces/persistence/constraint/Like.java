package org.omnifaces.persistence.constraint;

import java.util.Map;
import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

public final class Like extends Constraint<String> {

	private enum Type {
		STARTS_WITH,
		ENDS_WITH,
		CONTAINS;
	}

	private Type type;

	private Like(Type type, String value) {
		super(value, false);
		this.type = type;
	}

	public static Like startsWith(String value) {
		return new Like(Type.STARTS_WITH, value);
	}

	public static Like endsWith(String value) {
		return new Like(Type.ENDS_WITH, value);
	}

	public static Like contains(String value) {
		return new Like(Type.CONTAINS, value);
	}

	public boolean startsWith() {
		return type == Type.STARTS_WITH;
	}

	public boolean endsWith() {
		return type == Type.ENDS_WITH;
	}

	public boolean contains() {
		return type == Type.CONTAINS;
	}

	@Override
	public Predicate build(String key, CriteriaBuilder criteriaBuilder, Expression<?> expression, Map<String, Object> parameterValues) {
		String searchValue = (startsWith() ? "" : "%") + getValue().toLowerCase() + (endsWith() ? "" : "%");
		parameterValues.put(key, searchValue);
		return criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.function("str", String.class, expression)), criteriaBuilder.parameter(String.class, key));
	}

	@Override
	public boolean applies(Object value) {
		if (value == null) {
			return false;
		}

		String lowerCasedValue = getValue().toLowerCase();
		String valueAsLowerCasedString = value.toString().toLowerCase();

		if (startsWith()) {
			return valueAsLowerCasedString.startsWith(lowerCasedValue);
		}
		else if (endsWith()) {
			return valueAsLowerCasedString.endsWith(lowerCasedValue);
		}
		else {
			return valueAsLowerCasedString.contains(lowerCasedValue);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), type);
	}

	@Override
	public boolean equals(Object object) {
		return super.equals(object) && Objects.equals(type, ((Like) object).type);
	}

	@Override
	public String toString() {
		return "LIKE " + (startsWith() ? "" : "%") + getValue() + (endsWith() ? "" : "%");
	}

}