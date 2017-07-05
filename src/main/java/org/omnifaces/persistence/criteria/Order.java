package org.omnifaces.persistence.criteria;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Predicate;

/**
 * Creates <code>path LT|LTE|GT|GTE enum</code>.
 *
 * @author Bauke Scholtz
 */
public final class Order extends Criteria<Comparable<?>> {

	private enum Type {
		LT,
		LTE,
		GT,
		GTE
	}

	private Type type;

	private Order(Type type, Comparable<?> value) {
		super(value);
		this.type = type;
	}

	public static Order lessThan(Comparable<?> value) {
		return new Order(Type.LT, value);
	}

	public static Order lessThanOrEqualTo(Comparable<?> value) {
		return new Order(Type.LTE, value);
	}

	public static Order greaterThanOrEqualTo(Comparable<?> value) {
		return new Order(Type.GTE, value);
	}

	public static Order greaterThan(Comparable<?> value) {
		return new Order(Type.GT, value);
	}

	public boolean lessThan() {
		return type == Type.LT;
	}

	public boolean lessThanOrEqualTo() {
		return type == Type.LTE;
	}

	public boolean greaterThanOrEqualTo() {
		return type == Type.GTE;
	}

	public boolean greaterThan() {
		return type == Type.GT;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		Comparable searchValue = getValue();
		Expression<? extends Comparable> rawPath = (Expression<? extends Comparable>) path;
		ParameterExpression<? extends Comparable> parameter = parameterBuilder.build(searchValue);

		if (lessThan()) {
			return criteriaBuilder.lessThan(rawPath, parameter);
		}
		else if (lessThanOrEqualTo()) {
			return criteriaBuilder.lessThanOrEqualTo(rawPath, parameter);
		}
		else if (greaterThanOrEqualTo()) {
			return criteriaBuilder.greaterThanOrEqualTo(rawPath, parameter);
		}
		else {
			return criteriaBuilder.greaterThan(rawPath, parameter);
		}
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public boolean applies(Object value) {
		if (!(value instanceof Comparable)) {
			return false;
		}

		Comparable valueAsComparable = (Comparable) value;

		if (greaterThan()) {
			return valueAsComparable.compareTo(getValue()) > 0;
		}
		else if (greaterThanOrEqualTo()) {
			return valueAsComparable.compareTo(getValue()) >= 0;
		}
		else if (lessThan()) {
			return valueAsComparable.compareTo(getValue()) < 0;
		}
		else {
			return valueAsComparable.compareTo(getValue()) <= 0;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), type);
	}

	@Override
	public boolean equals(Object object) {
		return super.equals(object) && Objects.equals(type, ((Order) object).type);
	}

	@Override
	public String toString() {
		return (type == Type.GT ? ">" : type == Type.GTE ? ">=" : type==Type.LT ? "<" : "<=") + " " + getValue();
	}

}
