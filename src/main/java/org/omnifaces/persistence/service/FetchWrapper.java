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
import javax.persistence.criteria.JoinType;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

/**
 * <p>
 * A wrapper for {@link Fetch}, useful in case you intend to decorate it.
 *
 * @author Bauke Scholtz
 * @param <Z> Generic entity type of fetch source.
 * @param <X> Generic entity type of fetch target.
 */
public abstract class FetchWrapper<Z, X> implements Fetch<Z, X> {

	private Fetch<Z, X> wrapped;

	public FetchWrapper(Fetch<Z, X> wrapped) {
		this.wrapped = wrapped;
	}

	public Fetch<Z, X> getWrapped() {
		return wrapped;
	}

	@Override
	public Attribute<? super Z, ?> getAttribute() {
		return getWrapped().getAttribute();
	}

	@Override
	public Set<Fetch<X, ?>> getFetches() {
		return getWrapped().getFetches();
	}

	@Override
	public FetchParent<?, Z> getParent() {
		return getWrapped().getParent();
	}

	@Override
	public JoinType getJoinType() {
		return getWrapped().getJoinType();
	}

	@Override
	public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute) {
		return getWrapped().fetch(attribute);
	}

	@Override
	public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute, JoinType jt) {
		return getWrapped().fetch(attribute, jt);
	}

	@Override
	public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute) {
		return getWrapped().fetch(attribute);
	}

	@Override
	public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute, JoinType jt) {
		return getWrapped().fetch(attribute, jt);
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName) {
		return getWrapped().fetch(attributeName);
	}

	@Override
	@SuppressWarnings("hiding")
	public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
		return getWrapped().fetch(attributeName, jt);
	}

}
