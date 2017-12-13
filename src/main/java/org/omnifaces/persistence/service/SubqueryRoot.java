package org.omnifaces.persistence.service;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Root;

/**
 * Fetch joins are not supported in subqueries, so delegate to normal joins.
 * @see JoinFetchAdapter
 */
class SubqueryRoot<X> extends RootWrapper<X> {

	public SubqueryRoot(Root<X> wrapped) {
		super(wrapped);
	}

	@Override
	@SuppressWarnings({ "hiding" })
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return new JoinFetchAdapter<>(join(attributeName));
	}

}
