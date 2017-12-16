package org.omnifaces.persistence.criteria;

import static java.util.stream.Collectors.toSet;
import static org.omnifaces.persistence.JPA.castAsString;
import static org.omnifaces.persistence.JPA.isEnumeratedByOrdinal;

import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
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
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		Class<?> type = path.getJavaType();

		if (type.isEnum() && path instanceof Path && isEnumeratedByOrdinal((Path<?>) path)) {
			Object[] values = type.getEnumConstants();
			Set<Integer> matchingOrdinals = IntStream.range(0, values.length).filter(i -> applies(values[i])).boxed().collect(toSet());
			return path.in(matchingOrdinals);
		}
		else {
			boolean lowercaseable = !Number.class.isAssignableFrom(type);
			String searchValue = (startsWith() ? "" : "%") + (lowercaseable ? getValue().toLowerCase() : getValue()) + (endsWith() ? "" : "%");
			Expression<String> pathAsString = castAsString(criteriaBuilder, path);
			return criteriaBuilder.like(lowercaseable ? criteriaBuilder.lower(pathAsString) : pathAsString, parameterBuilder.create(searchValue));
		}
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
