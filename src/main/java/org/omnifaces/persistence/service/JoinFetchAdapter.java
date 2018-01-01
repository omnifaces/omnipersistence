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

import java.util.Set;

import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.FetchParent;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

/**
 * This class adapts from {@link Join} to {@link Fetch}.
 * @see SubqueryRoot
 */
class JoinFetchAdapter<X, Y> implements Fetch<X, Y> {

	private Join<X, Y> join;

	public JoinFetchAdapter(Join<X, Y> join) {
		this.join = join;
	}

	@Override
	public Attribute<? super X, ?> getAttribute() {
		return join.getAttribute();
	}

	@Override
	public FetchParent<?, X> getParent() {
		return join.getParent();
	}

	@Override
	public JoinType getJoinType() {
		return join.getJoinType();
	}

	@Override
	public Set<Fetch<Y, ?>> getFetches() {
		return join.getFetches();
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(SingularAttribute<? super Y, Z> attribute) {
		return new JoinFetchAdapter<>(join.join(attribute));
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(SingularAttribute<? super Y, Z> attribute, JoinType jt) {
		return new JoinFetchAdapter<>(join.join(attribute, jt));
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(PluralAttribute<? super Y, ?, Z> attribute) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <Z> Fetch<Y, Z> fetch(PluralAttribute<? super Y, ?, Z> attribute, JoinType jt) {
		throw new UnsupportedOperationException();
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return new JoinFetchAdapter<>(join.join(attributeName));
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
		return new JoinFetchAdapter<>(join.join(attributeName, jt));
	}

}
