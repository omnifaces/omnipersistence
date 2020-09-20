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

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Fetch joins are not supported in subqueries, so delegate to normal joins.
 *
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

	@Override
	@SuppressWarnings({ "hiding" })
	public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType joinType) {
		return new JoinFetchAdapter<>(join(attributeName, joinType));
	}

	@Override
	public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute) {
		return new JoinFetchAdapter<>(join(attribute));
	}

	@Override
	public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute, JoinType joinType) {
		return new JoinFetchAdapter<>(join(attribute, joinType));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute) {
		Join<X, Y> join;

		if (attribute instanceof ListAttribute) {
			join = join((ListAttribute<X, Y>) attribute);
		}
		else if (attribute instanceof SetAttribute) {
			join = join((SetAttribute<X, Y>) attribute);
		}
		else if (attribute instanceof MapAttribute) {
			join = join((MapAttribute<X, ?, Y>) attribute);
		}
		else if (attribute instanceof CollectionAttribute) {
			join = join((CollectionAttribute<X, Y>) attribute);
		}
		else {
			throw new UnsupportedOperationException();
		}

		return new JoinFetchAdapter<>(join);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute, JoinType joinType) {
		Join<X, Y> join;

		if (attribute instanceof ListAttribute) {
			join = join((ListAttribute<X, Y>) attribute, joinType);
		}
		else if (attribute instanceof SetAttribute) {
			join = join((SetAttribute<X, Y>) attribute, joinType);
		}
		else if (attribute instanceof MapAttribute) {
			join = join((MapAttribute<X, ?, Y>) attribute, joinType);
		}
		else if (attribute instanceof CollectionAttribute) {
			join = join((CollectionAttribute<X, Y>) attribute, joinType);
		}
		else {
			throw new UnsupportedOperationException();
		}

		return new JoinFetchAdapter<>(join);
	}
}
