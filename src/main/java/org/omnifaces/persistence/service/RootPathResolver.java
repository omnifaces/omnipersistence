/*
 * Copyright 2021 OmniFaces
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

import static java.lang.String.format;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

/**
 * Helper class of {@link BaseEntityService}.
 */
class RootPathResolver implements PathResolver {

	private static final String ERROR_UNKNOWN_FIELD =
		"Field %s cannot be found on %s. If this represents a transient field, make sure that it is delegating to @ManyToOne/@OneToOne children.";

	private final Root<?> root;
	private final Map<String, Path<?>> joins;
	private final Map<String, Path<?>> paths;
	private final Set<String> elementCollections;
	private final Set<String> manyOrOneToOnes;

	public RootPathResolver(Root<?> root, Set<String> elementCollections, Set<String> manyOrOneToOnes) {
		this.root = root;
		this.joins = getJoins(root);
		this.paths = new HashMap<>();
		this.elementCollections = elementCollections;
		this.manyOrOneToOnes = manyOrOneToOnes;
	}

	@Override
	public Expression<?> get(String field) {
		if (field == null) {
			return root;
		}

		Path<?> path = paths.get(field);

		if (path != null) {
			return path;
		}

		path = root;
		boolean explicitJoin = field.charAt(0) == '@';
		String originalField = explicitJoin ? field.substring(1) : field;
		String[] attributes = originalField.split("\\.");
		int depth = attributes.length;

		for (int i = 0; i < depth; i++) {
			String attribute = attributes[i];

			if (i + 1 < depth || elementCollections.contains(originalField)) {
				path = explicitJoin || !joins.containsKey(attribute) ? ((From<?, ?>) path).join(attribute) : joins.get(attribute);
			}
			else {
				try {
					path = path.get(attribute);
				}
				catch (IllegalArgumentException e) {
					if (depth == 1 && isTransient(path.getModel().getBindableJavaType(), attribute)) {
						path = guessManyOrOneToOnePath(attribute);
					}

					if (depth != 1 || path == null) {
						throw new IllegalArgumentException(format(ERROR_UNKNOWN_FIELD, field, root.getJavaType()), e);
					}
				}
			}
		}

		paths.put(field, path);
		return path;
	}

	private boolean isTransient(Class<?> type, String property) {
		return true; // TODO implement?
	}

	private Path<?> guessManyOrOneToOnePath(String attribute) {
		for (String manyOrOneToOne : manyOrOneToOnes) {
			try {
				return (Path<?>) get(manyOrOneToOne + "." + attribute);
			}
			catch (IllegalArgumentException ignore) {
				continue;
			}
		}

		return null;
	}

	private static Map<String, Path<?>> getJoins(From<?, ?> from) {
		Map<String, Path<?>> joins = new HashMap<>();
		collectJoins(from, joins);

		if (from instanceof EclipseLinkRoot) {
			((EclipseLinkRoot<?>) from).collectPostponedFetches(joins);
		}

		return joins;
	}

	private static void collectJoins(Path<?> path, Map<String, Path<?>> joins) {
		if (path instanceof From) {
			((From<?, ?>) path).getJoins().forEach(join -> collectJoins(join, joins));
		}
		if (path instanceof FetchParent) {
			try {
				((FetchParent<?, ?>) path).getFetches().stream().filter(fetch -> fetch instanceof Path).forEach(fetch -> collectJoins((Path<?>) fetch, joins));
			}
			catch (NullPointerException openJPAWillThrowThisOnEmptyFetches) {
				// Ignore and continue.
			}
		}
		if (path instanceof Join) {
			joins.put(((Join<?, ?>) path).getAttribute().getName(), path);
		}
		else if (path instanceof Fetch) {
			joins.put(((Fetch<?, ?>) path).getAttribute().getName(), path);
		}
	}

}

