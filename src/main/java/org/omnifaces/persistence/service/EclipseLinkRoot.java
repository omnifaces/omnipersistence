/*
 * Copyright 2020 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.Query;
import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;

/**
 * EclipseLink stubbornly refuses to perform a join when a range (offset/limit) is fetched, resulting in cartesian products.
 * This root will postpone all issued fetches so BaseEntityService can ultimately set them as an EclipseLink-specific query hint.
 * The only disadvantage is that you cannot anymore sort on them when used in a lazy model. This is a technical limitation.
 * @see PostponedFetch
 */
class EclipseLinkRoot<X> extends RootWrapper<X> {

	private Set<String> postponedFetches;

	public EclipseLinkRoot(Root<X> wrapped) {
		super(wrapped);
		postponedFetches = new HashSet<>(2);
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return new PostponedFetch<>(postponedFetches, attributeName);
	}

	public boolean hasPostponedFetches() {
		return !postponedFetches.isEmpty();
	}

	public void runPostponedFetches(Query query) {
		postponedFetches.forEach(fetch -> query.setHint("eclipselink.batch", "e." + fetch));
	}

	public void collectPostponedFetches(Map<String, Path<?>> paths) {
		postponedFetches.forEach(fetch -> {
			Path<?> path = this;

			for (String attribute : fetch.split("\\.")) {
				path = path.get(attribute);
			}

			paths.put(fetch, path);
		});
	}

}
