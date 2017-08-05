package org.omnifaces.persistence.service;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;

/**
 * Fetch joins are not supported in subqueries, so delegate to normal joins.
 */
class SubqueryRoot<X> extends RootWrapper<X> {

	public SubqueryRoot(Root<X> wrapped) {
		super(wrapped);
	}

	@Override
	@SuppressWarnings({ "unchecked", "hiding" })
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		Join<X, Y> join = join(attributeName);
		return join instanceof Fetch ? (Fetch<X, Y>) join : new JoinFetchAdapter<>(join);
	}

}
