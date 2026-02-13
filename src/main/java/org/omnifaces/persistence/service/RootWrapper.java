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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.CollectionJoin;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * <p>
 * A wrapper for {@link Root}, useful in case you intend to decorate it.
 *
 * @author Bauke Scholtz
 * @param <X> Generic entity type referenced by root.
 */
public abstract class RootWrapper<X> implements Root<X> {

    private Root<X> wrapped;

    public RootWrapper(Root<X> wrapped) {
        this.wrapped = wrapped;
    }

    public Root<X> getWrapped() {
        return wrapped;
    }

    @Override
    public Predicate isNull() {
        return getWrapped().isNull();
    }

    @Override
    public Class<? extends X> getJavaType() {
        return getWrapped().getJavaType();
    }

    @Override
    public EntityType<X> getModel() {
        return getWrapped().getModel();
    }

    @Override
    public Selection<X> alias(String name) {
        return getWrapped().alias(name);
    }

    @Override
    public Set<Fetch<X, ?>> getFetches() {
        return getWrapped().getFetches();
    }

    @Override
    public Predicate isNotNull() {
        return getWrapped().isNotNull();
    }

    @Override
    public String getAlias() {
        return getWrapped().getAlias();
    }

    @Override
    public Predicate in(Object... values) {
        return getWrapped().in(values);
    }

    @Override
    public boolean isCompoundSelection() {
        return getWrapped().isCompoundSelection();
    }

    @Override
    public Path<?> getParentPath() {
        return getWrapped().getParentPath();
    }

    @Override
    public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute) {
        return getWrapped().fetch(attribute);
    }

    @Override
    public List<Selection<?>> getCompoundSelectionItems() {
        return getWrapped().getCompoundSelectionItems();
    }

    @Override
    public <Y> Path<Y> get(SingularAttribute<? super X, Y> attribute) {
        return getWrapped().get(attribute);
    }

    @Override
    public Set<Join<X, ?>> getJoins() {
        return getWrapped().getJoins();
    }

    @Override
    public Predicate in(Expression<?>... values) {
        return getWrapped().in(values);
    }

    @Override
    public <Y> Fetch<X, Y> fetch(SingularAttribute<? super X, Y> attribute, JoinType jt) {
        return getWrapped().fetch(attribute, jt);
    }

    @Override
    public <E, C extends Collection<E>> Expression<C> get(PluralAttribute<X, C, E> collection) {
        return getWrapped().get(collection);
    }

    @Override
    public Predicate in(Collection<?> values) {
        return getWrapped().in(values);
    }

    @Override
    public boolean isCorrelated() {
        return getWrapped().isCorrelated();
    }

    @Override
    public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute) {
        return getWrapped().fetch(attribute);
    }

    @Override
    public Predicate in(Expression<Collection<?>> values) {
        return getWrapped().in(values);
    }

    @Override
    public <K, V, M extends Map<K, V>> Expression<M> get(MapAttribute<X, K, V> map) {
        return getWrapped().get(map);
    }

    @Override
    public From<X, X> getCorrelationParent() {
        return getWrapped().getCorrelationParent();
    }

    @Override
    public <Y> Fetch<X, Y> fetch(PluralAttribute<? super X, ?, Y> attribute, JoinType jt) {
        return getWrapped().fetch(attribute, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X> Expression<X> as(Class<X> type) {
        return getWrapped().as(type);
    }

    @Override
    public Expression<Class<? extends X>> type() {
        return getWrapped().type();
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        return getWrapped().fetch(attributeName);
    }

    @Override
    public <Y> Join<X, Y> join(SingularAttribute<? super X, Y> attribute) {
        return getWrapped().join(attribute);
    }

    @Override
    public <Y> Path<Y> get(String attributeName) {
        return getWrapped().get(attributeName);
    }

    @Override
    public <Y> Join<X, Y> join(SingularAttribute<? super X, Y> attribute, JoinType jt) {
        return getWrapped().join(attribute, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName, JoinType jt) {
        return getWrapped().fetch(attributeName, jt);
    }

    @Override
    public <Y> CollectionJoin<X, Y> join(CollectionAttribute<? super X, Y> collection) {
        return getWrapped().join(collection);
    }

    @Override
    public <Y> SetJoin<X, Y> join(SetAttribute<? super X, Y> set) {
        return getWrapped().join(set);
    }

    @Override
    public <Y> ListJoin<X, Y> join(ListAttribute<? super X, Y> list) {
        return getWrapped().join(list);
    }

    @Override
    public <K, V> MapJoin<X, K, V> join(MapAttribute<? super X, K, V> map) {
        return getWrapped().join(map);
    }

    @Override
    public <Y> CollectionJoin<X, Y> join(CollectionAttribute<? super X, Y> collection, JoinType jt) {
        return getWrapped().join(collection, jt);
    }

    @Override
    public <Y> SetJoin<X, Y> join(SetAttribute<? super X, Y> set, JoinType jt) {
        return getWrapped().join(set, jt);
    }

    @Override
    public <Y> ListJoin<X, Y> join(ListAttribute<? super X, Y> list, JoinType jt) {
        return getWrapped().join(list, jt);
    }

    @Override
    public <K, V> MapJoin<X, K, V> join(MapAttribute<? super X, K, V> map, JoinType jt) {
        return getWrapped().join(map, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Join<X, Y> join(String attributeName) {
        return getWrapped().join(attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> CollectionJoin<X, Y> joinCollection(String attributeName) {
        return getWrapped().joinCollection(attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> SetJoin<X, Y> joinSet(String attributeName) {
        return getWrapped().joinSet(attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> ListJoin<X, Y> joinList(String attributeName) {
        return getWrapped().joinList(attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, K, V> MapJoin<X, K, V> joinMap(String attributeName) {
        return getWrapped().joinMap(attributeName);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Join<X, Y> join(String attributeName, JoinType jt) {
        return getWrapped().join(attributeName, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> CollectionJoin<X, Y> joinCollection(String attributeName, JoinType jt) {
        return getWrapped().joinCollection(attributeName, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> SetJoin<X, Y> joinSet(String attributeName, JoinType jt) {
        return getWrapped().joinSet(attributeName, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> ListJoin<X, Y> joinList(String attributeName, JoinType jt) {
        return getWrapped().joinList(attributeName, jt);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, K, V> MapJoin<X, K, V> joinMap(String attributeName, JoinType jt) {
        return getWrapped().joinMap(attributeName, jt);
    }

}
