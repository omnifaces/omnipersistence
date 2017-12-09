package org.omnifaces.persistence.service;

import java.util.Set;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

/**
 * <p>
 * A wrapper for {@link Fetch}, useful in case you intend to decorate it.
 *
 * @author Bauke Scholtz
 * @param <Z> Generic entity type of fetch source.
 * @param <X> Generic entity type of fetch target.
 */
public class FetchWrapper<Z, X> implements Fetch<Z, X> {

	private Fetch<Z, X> wrapped;

	public FetchWrapper(Fetch<Z, X> wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public Attribute<? super Z, ?> getAttribute() {
		return wrapped.getAttribute();
	}

	@Override
	public Set<Fetch<X, ?>> getFetches() {
		return wrapped.getFetches();
	}

	@Override
	public FetchParent<?, Z> getParent() {
		return wrapped.getParent();
	}

	@Override
	public JoinType getJoinType() {
		return wrapped.getJoinType();
	}

	@Override
	public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute) {
		return wrapped.fetch(attribute);
	}

	@Override
	public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute, JoinType jt) {
		return wrapped.fetch(attribute, jt);
	}

	@Override
	public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute) {
		return wrapped.fetch(attribute);
	}

	@Override
	public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute, JoinType jt) {
		return wrapped.fetch(attribute, jt);
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return wrapped.fetch(attributeName);
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
		return wrapped.fetch(attributeName, jt);
	}

}
