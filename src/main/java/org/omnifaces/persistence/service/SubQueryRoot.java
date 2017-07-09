package org.omnifaces.persistence.service;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Root;

/**
 * Fetch joins are not supported in subqueries, so delegate to normal joins.
 */
class SubQueryRoot<X> extends RootWrapper<X> {

	public SubQueryRoot(Root<X> wrapped) {
		super(wrapped);
	}

	@Override
	@SuppressWarnings({ "unchecked", "hiding" })
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return (Fetch<X, Y>) join(attributeName);
	}

}
