/*
 * Copyright OmniFaces
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

import static org.omnifaces.persistence.service.PostponedFetchRoot.fetchOrPostpone;

import java.util.Set;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * This class wraps an actual fetch of a singular association issued on a {@link PostponedFetchRoot}. Its {@link Fetch#fetch(String)} calls and their overloads
 * are actual fetches as well when they are singular associations, and postponed otherwise. The join type is ignored. An actual fetch always uses a left fetch
 * join.
 *
 * @author Bauke Scholtz
 * @since 2.0
 * @see PostponedFetchRoot
 */
class SingularFetch<Z, X> extends FetchWrapper<Z, X> {

    private final Set<String> postponedFetches;
    private final String path;

    public SingularFetch(Fetch<Z, X> wrapped, Set<String> postponedFetches, String path) {
        super(wrapped);
        this.postponedFetches = postponedFetches;
        this.path = path;
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        var type = (ManagedType<?>) ((SingularAttribute<?, ?>) getAttribute()).getType();
        return fetchOrPostpone(getWrapped(), type, postponedFetches, path, attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType joinType) {
        return fetch(attributeName);
    }

    @Override
    public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute) {
        return fetch(attribute.getName());
    }

    @Override
    public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute, JoinType joinType) {
        return fetch(attribute.getName());
    }

    @Override
    public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute) {
        return fetch(attribute.getName());
    }

    @Override
    public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute, JoinType joinType) {
        return fetch(attribute.getName());
    }

}
