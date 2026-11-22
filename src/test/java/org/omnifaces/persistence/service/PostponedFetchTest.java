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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.persistence.service.Stubs.stub;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.BasicType;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.query.Page;
import org.omnifaces.persistence.service.PostponedFetchRoot.FetchedCollection;
import org.omnifaces.persistence.service.PostponedFetchRoot.MappedOrder;

/**
 * Unit tests for {@link PostponedFetch}, {@link PostponedFetchRoot} and {@link SingularFetch}. They must postpone fetches by attribute name or attribute, and
 * everything nested in them, except fetches of singular associations. Those must be actual left fetches, unless nested in a postponed fetch.
 * {@link PostponedFetch} must reject everything else with an {@link UnsupportedOperationException}, as there is no actual fetch to delegate to.
 */
public class PostponedFetchTest {

    private static final Set<String> PLURAL_ATTRIBUTES = Set.of("phones", "groups");
    private static final Set<String> EMBEDDED_ATTRIBUTES = Set.of("period");
    private static final Map<String, Set<String>> PLURAL_ATTRIBUTES_OF_TYPE_OF = Map.of("address", Set.of("owner"));
    private static final BasicType<?> BASIC = stub(BasicType.class, Map.of());
    private static final EntityType<?> TRACK = managedType(
        EntityType.class,
        Map.of(
            "id", BASIC, "title", BASIC, "album", stub(EntityType.class, Map.of()),
            "tags", stub(PluralAttribute.class, Map.of("getName", "tags", "isCollection", true, "getElementType", BASIC))
        ),
        Map.of(
            "hasSingleIdAttribute", true, "getSingularAttributes",
            Set.of(stub(SingularAttribute.class, Map.of("isId", true, "getName", "id", "getType", BASIC)))
        )
    );
    private static final EntityType<?> MEMBERSHIP = managedType(
        EntityType.class, Map.of(),
        Map.of(
            "hasSingleIdAttribute", true,
            "getSingularAttributes",
            Set.of(stub(SingularAttribute.class, Map.of("isId", true, "getName", "id", "getType", stub(EmbeddableType.class, Map.of()))))
        )
    );
    private static final EmbeddableType<?> CREDIT = managedType(
        EmbeddableType.class, Map.of("name", BASIC, "address", managedType(EmbeddableType.class, Map.of("city", BASIC), Map.of())), Map.of()
    );

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

    @Test
    void orderByWithoutFieldOrdersEntityElementsByIdAndBasicElementsByValue() {
        assertEquals(Map.of("tracks.id", true), PostponedFetchRoot.parseOrderBy("", "tracks", TRACK), "Entity, empty");
        assertEquals(Map.of("tracks.id", false), PostponedFetchRoot.parseOrderBy(" DESC ", "tracks", TRACK), "Entity, DESC");
        assertEquals(Map.of("tags", true), PostponedFetchRoot.parseOrderBy("", "tags", BASIC), "Basic, empty");
        assertEquals(Map.of("tags", false), PostponedFetchRoot.parseOrderBy("value DESC", "tags", BASIC), "Basic, field is not used");
    }

    @Test
    void orderByWithFieldsOrdersByEachFieldInGivenDirection() {
        assertEquals(
            List.of(Map.entry("tracks.title", false), Map.entry("tracks.id", true)),
            List.copyOf(PostponedFetchRoot.parseOrderBy("title desc, id", "tracks", TRACK).entrySet()), "Entity, two items"
        );
        assertEquals(Map.of("credits.address.city", true), PostponedFetchRoot.parseOrderBy("address.city ASC", "credits", CREDIT), "Embeddable, nested");
    }

    @Test
    void unsupportedOrderByIsIgnored() {
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("title DESC NULLS LAST", "tracks", TRACK), "NULLS LAST");
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("lower(title)", "tracks", TRACK), "Function");
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("title, unknown", "tracks", TRACK), "Unknown field");
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("", "credits", CREDIT), "Embeddable without field");
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("tags", "tracks", TRACK), "Collection field");
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("album", "tracks", TRACK), "Association field");
        assertEquals(Map.of(), PostponedFetchRoot.parseOrderBy("", "memberships", MEMBERSHIP), "Embedded ID");
    }

    @Test
    void orderByDoesNotApplyAlongWithOrderColumn() {
        var both = PostponedFetchRoot.getMappedOrder(MappedOrders.class, "both");
        assertEquals(new MappedOrder("title", true), both, "Both annotations");
        assertNull(both.effectiveOrderBy(), "Effective @OrderBy along with @OrderColumn");
        assertEquals("", PostponedFetchRoot.getMappedOrder(MappedOrders.class, "plain").effectiveOrderBy(), "Plain @OrderBy");
        assertEquals("title DESC", PostponedFetchRoot.getMappedOrder(MappedOrders.class, "viaGetter").effectiveOrderBy(), "@OrderBy on getter");
        assertFalse(PostponedFetchRoot.getMappedOrder(MappedOrders.class, "none").isPresent(), "No annotation");
    }

    @Test
    void criteriaOnEntityWithUnsupportedIdIsKeptAndOrderingOnItIsSkipped() {
        var unsupported = stub(EntityType.class, Map.of("hasSingleIdAttribute", false));
        var owner = stub(SingularAttribute.class, Map.of("getName", "owner", "isCollection", false, "getType", unsupported));
        var element = managedType(EmbeddableType.class, Map.of("owner", owner), Map.of());
        var declaringType = stub(ManagedType.class, Map.of("getJavaType", MappedOrders.class));
        var credits = stub(
            PluralAttribute.class, Map.of("getName", "credits", "isCollection", true, "getElementType", element, "getDeclaringType", declaringType)
        );
        var root = managedType(EntityType.class, Map.of("credits", credits), Map.of());
        var byCriteria = FetchedCollection.of(root, "credits", Page.with().allMatch(Map.of("credits.owner", 1L)).build(), id -> id, value -> false);
        var byOrdering = FetchedCollection.of(root, "credits", Page.with().orderBy("credits.owner", true).build(), id -> id, value -> false);
        assertEquals(List.of("owner"), byCriteria.getElementFields(), "Criteria");
        assertEquals(List.of(), byOrdering.getElementFields(), "Ordering");
    }

    static class MappedOrders {

        @OrderBy("title")
        @OrderColumn
        List<String> both;

        @OrderBy
        List<String> plain;

        List<String> none;

        List<String> viaGetter;

        @OrderBy("title DESC")
        public List<String> getViaGetter() {
            return viaGetter;
        }

    }

    /**
     * Stubs a managed type of given type with given attributes. Each is given attribute, or a singular attribute of given type. An unknown attribute throws
     * {@link IllegalArgumentException}, as specified by {@link ManagedType#getAttribute(String)}.
     */
    private static <T extends ManagedType<?>> T managedType(Class<T> type, Map<String, ?> attributes, Map<String, Object> returnValues) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) {
                        var attributeType = attributes.get((String) args[0]);

                        if (attributeType == null) {
                            throw new IllegalArgumentException((String) args[0]);
                        }
                        else if (attributeType instanceof Attribute) {
                            return attributeType;
                        }

                        return stub(SingularAttribute.class, Map.of("getName", args[0], "isCollection", false, "getType", attributeType));
                    }
                    else if (returnValues.containsKey(method.getName())) {
                        return returnValues.get(method.getName());
                    }

                    throw new UnsupportedOperationException(method.getName());
                }
            )
        );
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
        return new PostponedFetchRoot<>(fetchParent(Root.class, PLURAL_ATTRIBUTES, fetches), Provider.UNKNOWN);
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
