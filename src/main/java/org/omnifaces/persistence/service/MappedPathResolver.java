/*
 * Copyright 2018 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.service;

import java.util.Map;
import java.util.Set;

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Root;

/**
 * Helper class of {@link BaseEntityService}.
 */
class MappedPathResolver implements PathResolver {

	private final Root<?> root;
	private final Map<String, Expression<?>> paths;
	private final RootPathResolver rootPathResolver;

	public MappedPathResolver(Root<?> root, Map<String, Expression<?>> paths, Set<String> elementCollections, Set<String> manyOrOneToOnes) {
		this.root = root;
		this.paths = paths;
		this.rootPathResolver = new RootPathResolver(root, elementCollections, manyOrOneToOnes);
	}

	@Override
	public Expression<?> get(String field) {
		if (field == null) {
			return root;
		}

		Expression<?> path = paths.get(field);

		if (path != null) {
			return path;
		}
		else {
			return rootPathResolver.get(field);
		}
	}
}
