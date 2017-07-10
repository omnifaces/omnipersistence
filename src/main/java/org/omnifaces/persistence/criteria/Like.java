package org.omnifaces.persistence.criteria;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>path LIKE value</code>.
 *
 * @author Bauke Scholtz
 */
public final class Like extends Criteria<String> {

	private enum Type {
		STARTS_WITH,
		ENDS_WITH,
		CONTAINS;
	}

	private Type type;

	private Like(Type type, String value) {
		super(value);
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
	@SuppressWarnings("unchecked")
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		boolean lowercaseable = !Number.class.isAssignableFrom(path.getJavaType());
		String searchValue = (startsWith() ? "" : "%") + (lowercaseable ? getValue().toLowerCase() : getValue()) + (endsWith() ? "" : "%");
		Expression<String> pathAsString = (path.getJavaType() == String.class) ? (Expression<String>) path : path.as(String.class);
		return criteriaBuilder.like(lowercaseable ? criteriaBuilder.lower(pathAsString) : pathAsString, parameterBuilder.create(searchValue));
	}

	@Override
	public boolean applies(Object modelValue) {
		if (modelValue == null) {
			return false;
		}

		String lowerCasedValue = getValue().toLowerCase();
		String lowerCasedModelValue = modelValue.toString().toLowerCase();

		if (startsWith()) {
			return lowerCasedModelValue.startsWith(lowerCasedValue);
		}
		else if (endsWith()) {
			return lowerCasedModelValue.endsWith(lowerCasedValue);
		}
		else {
			return lowerCasedModelValue.contains(lowerCasedValue);
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
