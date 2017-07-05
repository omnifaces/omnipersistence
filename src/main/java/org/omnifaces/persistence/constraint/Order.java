package org.omnifaces.persistence.constraint;

import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Predicate;

public final class Order extends Constraint<Comparable<?>> {

	private enum Type {
		GT,
		GTE,
		LT,
		LTE;
	}

	private Type type;

	private Order(Type type, Comparable<?> value) {
		super(value);
		this.type = type;
	}

	public static Order greaterThan(Comparable<?> value) {
		return new Order(Type.GT, value);
	}

	public static Order greaterThanOrEqualTo(Comparable<?> value) {
		return new Order(Type.GTE, value);
	}

	public static Order lessThan(Comparable<?> value) {
		return new Order(Type.LT, value);
	}

	public static Order lessThanOrEqualTo(Comparable<?> value) {
		return new Order(Type.LTE, value);
	}

	public boolean greaterThan() {
		return type == Type.GT;
	}

	public boolean greaterThanOrEqualTo() {
		return type == Type.GTE;
	}

	public boolean lessThan() {
		return type == Type.LT;
	}

	public boolean lessThanOrEqualTo() {
		return type == Type.LTE;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Predicate build(Expression<?> expression, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
		Comparable<?> searchValue = getValue();
		Expression rawExpression = expression;
		ParameterExpression<? extends Comparable> parameter = parameterBuilder.create(searchValue);

		if (greaterThan()) {
			return criteriaBuilder.greaterThan(rawExpression, parameter);
		}
		else if (greaterThanOrEqualTo()) {
			return criteriaBuilder.greaterThanOrEqualTo(rawExpression, parameter);
		}
		else if (lessThan()) {
			return criteriaBuilder.lessThan(rawExpression, parameter);
		}
		else {
			return criteriaBuilder.lessThanOrEqualTo(rawExpression, parameter);
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
