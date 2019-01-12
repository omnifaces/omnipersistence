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
