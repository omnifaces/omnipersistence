package org.omnifaces.persistence.service;

import java.util.Set;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

/**
 * This class adapts from {@link Join} to {@link Fetch}.
 * @see SubqueryRoot
 */
class JoinFetchAdapter<X, Y> implements Fetch<X, Y> {

	private Join<X, Y> join;

	public JoinFetchAdapter(Join<X, Y> join) {
		this.join = join;
	}

	@Override
	public Attribute<? super X, ?> getAttribute() {
		return join.getAttribute();
	}

	@Override
	public FetchParent<?, X> getParent() {
		return join.getParent();
	}

	@Override
	public JoinType getJoinType() {
		return join.getJoinType();
	}

	@Override
	public Set<Fetch<Y, ?>> getFetches() {
		return join.getFetches();
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(SingularAttribute<? super Y, Z> attribute) {
		return new JoinFetchAdapter<>(join.join(attribute));
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(SingularAttribute<? super Y, Z> attribute, JoinType jt) {
		return new JoinFetchAdapter<>(join.join(attribute, jt));
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(PluralAttribute<? super Y, ?, Z> attribute) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(PluralAttribute<? super Y, ?, Z> attribute, JoinType jt) {
		throw new UnsupportedOperationException();
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return new JoinFetchAdapter<>(join.join(attributeName));
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
		return new JoinFetchAdapter<>(join.join(attributeName, jt));
	}

}
