package org.omnifaces.persistence.service;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Root;

/**
 * EclipseLink stubbornly refuses to perform a join when a range (offset/limit) is fetched, resulting in cartesian products.
 * This root will postpone all issued fetches so BaseEntityService can ultimately set them as an EclipseLink-specific query hint.
 * The only disadvantage is that you cannot anymore sort on them when used in a lazy model. This is a technical limitation.
 */
class EclipseLinkRoot<X> extends RootWrapper<X> {

	private Set<String> postponedFetches;

	public EclipseLinkRoot(Root<X> wrapped) {
		super(wrapped);
		postponedFetches = new HashSet<>(2);
	}

	@Override
	@SuppressWarnings({ "unchecked", "hiding" })
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		postponedFetches.add(attributeName);
		return null;
	}

	public Set<String> getPostponedFetches() {
		return postponedFetches;
	}

}
