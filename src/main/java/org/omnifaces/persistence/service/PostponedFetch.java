package org.omnifaces.persistence.service;

import java.util.Set;

import javax.persistence.criteria.Fetch;

/**
 * This class postpones all {@link Fetch#fetch(String)} calls.
 * @see EclipseLinkRoot
 */
class PostponedFetch<Z, X> extends FetchWrapper<Z, X> {

	private Set<String> postponedFetches;
	private String path;

	public PostponedFetch(Set<String> postponedFetches, String path) {
		super(null);
		this.postponedFetches = postponedFetches;
		this.path = path;
		postponedFetches.add(path);
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return new PostponedFetch<>(postponedFetches, path + "." + attributeName);
	}

}
