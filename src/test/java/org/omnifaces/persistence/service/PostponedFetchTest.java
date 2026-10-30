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

import static jakarta.persistence.criteria.JoinType.LEFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostponedFetch} and {@link PostponedFetchRoot}. They must postpone fetches by attribute name or attribute, and {@link PostponedFetch}
 * must reject everything else with an {@link UnsupportedOperationException}, as there is no actual fetch to delegate to.
 */
public class PostponedFetchTest {

    @Test
    void fetchByNameIsPostponed() {
        var postponedFetches = new HashSet<String>();
        new PostponedFetch<>(postponedFetches, "phones").fetch("owner");
        assertEquals(Set.of("phones", "phones.owner"), postponedFetches);
    }

    @Test
    void fetchByNameWithJoinTypeIsPostponed() {
        var postponedFetches = new HashSet<String>();
        new PostponedFetch<>(postponedFetches, "phones").fetch("owner", LEFT);
        assertEquals(Set.of("phones", "phones.owner"), postponedFetches);
    }

    @Test
    void fetchBySingularAttributeIsPostponed() {
        var postponedFetches = new HashSet<String>();
        var fetch = new PostponedFetch<Object, Object>(postponedFetches, "phones");
        fetch.fetch(singular("owner"));
        fetch.fetch(singular("type"), LEFT);
        assertEquals(Set.of("phones", "phones.owner", "phones.type"), postponedFetches);
    }

    @Test
    void fetchByPluralAttributeIsPostponed() {
        var postponedFetches = new HashSet<String>();
        var fetch = new PostponedFetch<Object, Object>(postponedFetches, "owner");
        fetch.fetch(plural("phones"));
        fetch.fetch(plural("groups"), LEFT);
        assertEquals(Set.of("owner", "owner.phones", "owner.groups"), postponedFetches);
    }

    @Test
    void rootFetchByAnyOverloadIsPostponed() {
        var root = new PostponedFetchRoot<Object>(null);
        root.fetch("address");
        root.fetch("phones", LEFT);
        root.fetch(singular("owner"));
        root.fetch(singular("type"), LEFT);
        root.fetch(plural("groups"));
        root.fetch(plural("tags"), LEFT);
        assertEquals(Set.of("address", "phones", "owner", "type", "groups", "tags"), root.getPostponedFetches());
    }

    @Test
    void otherMethodsThrowUnsupportedOperationException() {
        var fetch = new PostponedFetch<>(new HashSet<>(), "phones");
        assertThrows(UnsupportedOperationException.class, fetch::getAttribute);
        assertThrows(UnsupportedOperationException.class, fetch::getFetches);
        assertThrows(UnsupportedOperationException.class, fetch::getParent);
        assertThrows(UnsupportedOperationException.class, fetch::getJoinType);
    }

    @SuppressWarnings("unchecked")
    private static SingularAttribute<Object, Object> singular(String name) {
        return attribute(SingularAttribute.class, name);
    }

    @SuppressWarnings("unchecked")
    private static PluralAttribute<Object, ?, Object> plural(String name) {
        return attribute(PluralAttribute.class, name);
    }

    private static <A extends Attribute<?, ?>> A attribute(Class<A> type, String name) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return name;
                    }

                    throw new UnsupportedOperationException(method.getName());
                }
            )
        );
    }

}
