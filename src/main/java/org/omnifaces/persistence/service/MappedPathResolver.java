/*
 * Copyright 2019 OmniFaces
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
class MappedPathResolver extends RootPathResolver {

	private final Map<String, Expression<?>> paths;

	public MappedPathResolver(Root<?> root, Map<String, Expression<?>> paths, Set<String> elementCollections, Set<String> manyOrOneToOnes) {
		super(root, elementCollections, manyOrOneToOnes);
		this.paths = paths;
	}

	@Override
	public Expression<?> get(String field) {
		if (field != null) {
			Expression<?> path = paths.get(field);

			if (path != null) {
				return path;
			}
		}

		return super.get(field);
	}
}
