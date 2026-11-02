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

import static jakarta.persistence.criteria.JoinType.INNER;
import static jakarta.persistence.criteria.JoinType.LEFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.persistence.service.Stubs.stub;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostponedFetch}, {@link PostponedFetchRoot} and {@link SingularFetch}. They must postpone fetches by attribute name or attribute, and
 * everything nested in them, except fetches of singular associations. Those must be actual left fetches, unless nested in a postponed fetch.
 * {@link PostponedFetch} must reject everything else with an {@link UnsupportedOperationException}, as there is no actual fetch to delegate to.
 */
public class PostponedFetchTest {

    private static final Set<String> PLURAL_ATTRIBUTES = Set.of("phones", "groups");
    private static final Set<String> EMBEDDED_ATTRIBUTES = Set.of("period");
    private static final Map<String, Set<String>> PLURAL_ATTRIBUTES_OF_TYPE_OF = Map.of("address", Set.of("owner"));

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
    void rootFetchOfPluralAttributeByAnyOverloadIsPostponed() {
        var fetches = new ArrayList<String>();
        var root = postponedFetchRoot(fetches);
        root.fetch("phones");
        root.fetch("groups", INNER);
        root.fetch(plural("phones"));
        root.fetch(plural("groups"), INNER);
        assertEquals(Set.of("phones", "groups"), root.getPostponedFetches());
        assertEquals(List.of(), fetches);
    }

    @Test
    void rootFetchOfEmbeddedAttributeIsPostponed() {
        var fetches = new ArrayList<String>();
        var root = postponedFetchRoot(fetches);
        root.fetch("period");
        assertEquals(Set.of("period"), root.getPostponedFetches());
        assertEquals(List.of(), fetches);
    }

    @Test
    void rootFetchOfSingularAssociationByAnyOverloadIsLeftFetched() {
        var fetches = new ArrayList<String>();
        var root = postponedFetchRoot(fetches);
        assertInstanceOf(SingularFetch.class, root.fetch("address"));
        root.fetch("owner", INNER);
        root.fetch(singular("address"));
        root.fetch(singular("owner"), INNER);
        assertEquals(List.of("address LEFT", "owner LEFT", "address LEFT", "owner LEFT"), fetches);
        assertEquals(Set.of(), root.getPostponedFetches());
    }

    @Test
    void singularAssociationFetchNestedInSingularFetchByAnyOverloadIsLeftFetched() {
        var fetches = new ArrayList<String>();
        var owner = postponedFetchRoot(fetches).<Object, Object>fetch("owner");
        assertInstanceOf(SingularFetch.class, owner.fetch("address"));
        owner.fetch("address", INNER);
        owner.fetch(singular("address"));
        owner.fetch(singular("address"), INNER);
        assertEquals(List.of("owner LEFT", "address LEFT", "address LEFT", "address LEFT", "address LEFT"), fetches);
    }

    @Test
    void pluralFetchNestedInSingularFetchByAnyOverloadIsPostponedAlongWithIt() {
        var fetches = new ArrayList<String>();
        var root = postponedFetchRoot(fetches);
        var owner = root.<Object, Object>fetch("owner");
        owner.fetch(plural("phones"));
        owner.fetch(plural("groups"), INNER);
        assertEquals(List.of("owner LEFT"), fetches);
        assertEquals(Set.of("owner", "owner.phones", "owner.groups"), root.getPostponedFetches());
    }

    @Test
    void fetchNestedInPostponedFetchInSingularFetchIsPostponedWithAllAncestors() {
        var fetches = new ArrayList<String>();
        var root = postponedFetchRoot(fetches);
        root.fetch("owner").fetch("address").fetch("owner").fetch("address");
        assertEquals(List.of("owner LEFT", "address LEFT"), fetches);
        assertEquals(Set.of("owner", "owner.address", "owner.address.owner", "owner.address.owner.address"), root.getPostponedFetches());
    }

    @Test
    void nestedFetchIsDecidedByTypeOfFetchedAttribute() {
        var fetches = new ArrayList<String>();
        var root = postponedFetchRoot(fetches);
        root.fetch("owner");
        root.fetch("address").fetch("owner");
        assertEquals(List.of("owner LEFT", "address LEFT"), fetches);
        assertEquals(Set.of("address", "address.owner"), root.getPostponedFetches());
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

    @SuppressWarnings("unchecked")
    private static PostponedFetchRoot<Object> postponedFetchRoot(List<String> fetches) {
        return new PostponedFetchRoot<>(fetchParent(Root.class, PLURAL_ATTRIBUTES, fetches));
    }

    /**
     * Stubs a fetch parent whose model has given plural associations, the embedded attributes {@link #EMBEDDED_ATTRIBUTES} and singular associations otherwise.
     * A fetch on it returns a fetch parent stub whose model has the plural associations {@link #PLURAL_ATTRIBUTES_OF_TYPE_OF} the fetched attribute, or
     * {@link #PLURAL_ATTRIBUTES} by default. Each fetch on it is recorded in given list as attribute name and join type, and returns a fetch parent stub of the
     * same model.
     */
    private static <T extends FetchParent<?, ?>> T fetchParent(Class<T> type, Set<String> pluralAttributes, List<String> fetches) {
        var model = (EntityType<?>) Proxy.newProxyInstance(
            EntityType.class.getClassLoader(), new Class<?>[] { EntityType.class }, (proxy, method, args) -> {
                if ("getAttribute".equals(method.getName())) {
                    var name = (String) args[0];
                    return stub(
                        Attribute.class,
                        Map.of("getName", name, "isCollection", pluralAttributes.contains(name), "isAssociation", !EMBEDDED_ATTRIBUTES.contains(name))
                    );
                }

                throw new UnsupportedOperationException(method.getName());
            }
        );

        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getModel" :
                            return model;
                        case "getAttribute" :
                            return stub(SingularAttribute.class, Map.of("getType", model));
                        case "fetch" :
                            fetches.add(args[0] + " " + args[1]);
                            return fetchParent(Fetch.class, PLURAL_ATTRIBUTES_OF_TYPE_OF.getOrDefault(args[0], PLURAL_ATTRIBUTES), fetches);
                        default :
                            throw new UnsupportedOperationException(method.getName());
                    }
                }
            )
        );
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
