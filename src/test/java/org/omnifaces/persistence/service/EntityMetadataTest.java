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

import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.BASIC;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ELEMENT_COLLECTION;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.EMBEDDED;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_MANY;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_MANY;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_ONE;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.Provider.OPENJPA;
import static org.omnifaces.persistence.service.Stubs.stub;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.model.SoftDeletable;
import org.omnifaces.persistence.service.EntityMetadata.Mappings;

/**
 * Unit tests for {@link EntityMetadata}. The association mappings are resolved against a stub metamodel built from {@link Proxy} instances, see
 * {@link AssociationMappings#metamodel}.
 */
class EntityMetadataTest {

    @Nested
    class GeneratedId {

        @Test
        void generatedIdEntitySubclassHasGeneratedId() {
            assertTrue(new EntityMetadata(Root.class).isGeneratedId());
        }

        @Test
        void idFieldWithGeneratedValueHasGeneratedId() {
            assertTrue(new EntityMetadata(GeneratedValueEntity.class).isGeneratedId());
        }

        @Test
        void idFieldWithoutGeneratedValueHasNoGeneratedId() {
            assertFalse(new EntityMetadata(AssignedIdEntity.class).isGeneratedId());
        }

    }

    @Nested
    class SoftDelete {

        @Test
        void entityWithSoftDeletableFieldIsSoftDeletable() {
            var softDeleteData = new EntityMetadata(SoftDeletableEntity.class).getSoftDeleteData();
            assertDoesNotThrow(softDeleteData::checkSoftDeletable);
        }

        @Test
        void entityWithoutSoftDeletableFieldIsNotSoftDeletable() {
            var softDeleteData = new EntityMetadata(AssignedIdEntity.class).getSoftDeleteData();
            assertThrows(NonSoftDeletableEntityException.class, softDeleteData::checkSoftDeletable);
        }

    }

    @Nested
    class AssociationMappings {

        private final StubMetamodel metamodel = new StubMetamodel()
            .entity(
                Root.class, attribute("children", ONE_TO_MANY, Child.class), attribute("tags", ELEMENT_COLLECTION, String.class),
                attribute("parent", ONE_TO_ONE, Parent.class), attribute("detail", ONE_TO_ONE, PlainEntity.class),
                attribute("location", EMBEDDED, Location.class)
            )
            .entity(Child.class, attribute("root", MANY_TO_ONE, Root.class), attribute("labels", ELEMENT_COLLECTION, String.class))
            .entity(Parent.class, attribute("name", BASIC, String.class))
            .entity(PlainEntity.class, attribute("other", MANY_TO_ONE, Root.class));

        private Mappings mappings(EntityMetadata metadata) {
            return metadata.getMappings(metamodel.entityManagerFactory(), HIBERNATE);
        }

        @Test
        void fieldEndingInElementCollectionIsElementCollection() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertTrue(mappings.isElementCollection("tags"));
            assertTrue(mappings.isElementCollection("children.labels"));
            assertFalse(mappings.isElementCollection("children"));
            assertFalse(mappings.isElementCollection("parent.name"));
        }

        @Test
        void fieldNestedInOneToManyIsOneToMany() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertTrue(mappings.isOneToMany("children.labels"));
            assertFalse(mappings.isOneToMany("children"));
            assertFalse(mappings.isOneToMany("parent.name"));
        }

        @Test
        void unknownFieldNestedInOneToManyIsOneToMany() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertTrue(mappings.isOneToMany("children.unknown"));
            assertTrue(mappings.isOneToMany("children.labels.junk"));
            assertTrue(mappings.isOneToMany("children."));
        }

        @Test
        void unknownFieldIsNeitherElementCollectionNorOneToMany() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertFalse(mappings.isElementCollection("unknown"));
            assertFalse(mappings.isOneToMany("unknown.name"));
        }

        @Test
        void explicitJoinFieldIsNeitherElementCollectionNorOneToMany() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertFalse(mappings.isElementCollection("@tags"));
            assertFalse(mappings.isOneToMany("@children.labels"));
        }

        @Test
        void fieldPathBackThroughRootTypeIsResolved() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertTrue(mappings.isElementCollection("children.root.tags"));
            assertTrue(mappings.isOneToMany("children.root.tags"));
        }

        @Test
        void fieldPathThroughEntityNotExtendingBaseEntityIsFollowed() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertFalse(mappings.isElementCollection("detail.other"));
            assertTrue(metamodel.lookedUpTypes.contains(PlainEntity.class));
        }

        @Test
        void fieldPathThroughNonEntityTypeIsNotFollowed() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertFalse(mappings.isElementCollection("location.lines"));
            assertFalse(metamodel.lookedUpTypes.contains(Location.class));
        }

        @Test
        void fieldPathsToTypeReachedViaTwoPathsAreBothResolved() {
            var twoPathsMetamodel = new StubMetamodel()
                .entity(Order.class, attribute("billingAddress", MANY_TO_ONE, Address.class), attribute("shippingAddress", MANY_TO_ONE, Address.class))
                .entity(Address.class, attribute("lines", ELEMENT_COLLECTION, String.class), attribute("countries", ONE_TO_MANY, Country.class))
                .entity(Country.class, attribute("name", BASIC, String.class));
            var mappings = new EntityMetadata(Order.class).getMappings(twoPathsMetamodel.entityManagerFactory(), HIBERNATE);
            assertTrue(mappings.isElementCollection("billingAddress.lines"));
            assertTrue(mappings.isElementCollection("shippingAddress.lines"));
            assertTrue(mappings.isOneToMany("billingAddress.countries.name"));
            assertTrue(mappings.isOneToMany("shippingAddress.countries.name"));
        }

        @Test
        void resolvedFieldPathIsCached() {
            var mappings = mappings(new EntityMetadata(Root.class));
            mappings.isElementCollection("children.labels");
            var lookupsAfterFirst = metamodel.lookedUpTypes.size();
            mappings.isElementCollection("children.labels");
            mappings.isOneToMany("children.labels");
            assertEquals(lookupsAfterFirst, metamodel.lookedUpTypes.size());
        }

        @Test
        void unknownFieldPathReusesCachedAttributeMappings() {
            var mappings = mappings(new EntityMetadata(Root.class));
            mappings.isElementCollection("children.unknown");
            var lookupsAfterFirst = metamodel.lookedUpTypes.size();
            mappings.isElementCollection("children.unknown");
            mappings.isElementCollection("unknown");
            assertEquals(lookupsAfterFirst, metamodel.lookedUpTypes.size());
        }

        @Test
        void unresolvableFieldPathIsNotCached() throws ReflectiveOperationException {
            var mappings = mappings(new EntityMetadata(Root.class));
            mappings.isElementCollection("tags");
            mappings.isElementCollection("unknown");
            mappings.isElementCollection("parent.name.junk");
            mappings.isOneToMany("children.labels.junk");
            mappings.isElementCollection("tags.");
            mappings.isElementCollection("children.labels..");
            assertEquals(Set.of("tags"), cachedPaths(mappings));
        }

        @Test
        void fieldPathWithoutAttributeNamesIsNeitherElementCollectionNorOneToMany() {
            var mappings = mappings(new EntityMetadata(Root.class));
            assertFalse(mappings.isElementCollection("."));
            assertFalse(mappings.isOneToMany(".."));
        }

        @Test
        void collectsManyOrOneToOnesIncludingNestedOnes() {
            assertEquals(Set.of("parent", "detail", "detail.other", "children.root"), mappings(new EntityMetadata(Root.class)).getManyOrOneToOnes());
        }

        @Test
        void manyOrOneToOneCrawlDoesNotGoBackIntoRootType() {
            mappings(new EntityMetadata(Root.class)).getManyOrOneToOnes();
            assertEquals(List.of(Root.class, Child.class, Parent.class, PlainEntity.class), metamodel.lookedUpTypes);
        }

        @Test
        void manyOrOneToOneCrawlDoesNotGoIntoNonEntityType() {
            mappings(new EntityMetadata(Root.class)).getManyOrOneToOnes();
            assertFalse(metamodel.lookedUpTypes.contains(Location.class));
        }

        @Test
        void manyOrOneToOnesAreComputedOnlyOnce() {
            var metadata = new EntityMetadata(Root.class);
            var first = metadata.getMappings(metamodel.entityManagerFactory(), HIBERNATE).getManyOrOneToOnes();
            var lookedUpTypesAfterFirst = metamodel.lookedUpTypes.size();
            var second = metadata.getMappings(metamodel.entityManagerFactory(), HIBERNATE).getManyOrOneToOnes();
            assertSame(first, second);
            assertEquals(lookedUpTypesAfterFirst, metamodel.lookedUpTypes.size());
        }

        @Test
        void attributeMappingsAreSharedByEntityTypesOfSameEntityManagerFactory() {
            mappings(new EntityMetadata(Root.class)).isElementCollection("children.labels");
            var lookupsAfterRoot = metamodel.lookedUpTypes.size();
            assertTrue(mappings(new EntityMetadata(Child.class)).isElementCollection("labels"));
            assertEquals(lookupsAfterRoot, metamodel.lookedUpTypes.size());
        }

        @Test
        void elementCollectionReportedAsOneToManyIsCorrectedByProvider() throws NoSuchFieldException {
            var openJpa = new EntityMetadata(OneToManyReportedEntity.class).getMappings(oneToManyReportedMetamodel().entityManagerFactory(), OPENJPA);
            assertTrue(openJpa.isElementCollection("tags"));
            assertFalse(openJpa.isOneToMany("tags.x"));
            assertTrue(openJpa.isOneToMany("children.labels"));

            var hibernate = new EntityMetadata(OneToManyReportedEntity.class).getMappings(oneToManyReportedMetamodel().entityManagerFactory(), HIBERNATE);
            assertFalse(hibernate.isElementCollection("tags"));
            assertTrue(hibernate.isOneToMany("tags.x"));
        }

        /**
         * Describes <code>tags</code> the way OpenJPA does: as <code>ONE_TO_MANY</code>, although the field is an <code>@ElementCollection</code>.
         */
        private StubMetamodel oneToManyReportedMetamodel() throws NoSuchFieldException {
            return new StubMetamodel()
                .entity(
                    OneToManyReportedEntity.class,
                    attribute("tags", ONE_TO_MANY, String.class, OneToManyReportedEntity.class.getDeclaredField("tags")),
                    attribute("children", ONE_TO_MANY, Child.class, OneToManyReportedEntity.class.getDeclaredField("children"))
                )
                .entity(Child.class, attribute("labels", BASIC, String.class, OneToManyReportedEntity.class.getDeclaredField("name")));
        }

        @Test
        void singularAttributeWithElementTypeAsBindableJavaTypeIsCorrectedByProvider() throws NoSuchFieldException {
            var openJpa = new EntityMetadata(Child.class).getMappings(elementTypeAsBindableJavaTypeMetamodel().entityManagerFactory(), OPENJPA);
            assertTrue(openJpa.isElementCollection("root.tags"));

            var hibernate = new EntityMetadata(Child.class).getMappings(elementTypeAsBindableJavaTypeMetamodel().entityManagerFactory(), HIBERNATE);
            assertFalse(hibernate.isElementCollection("root.tags"));
        }

        /**
         * Describes <code>root</code> the way OpenJPA does: with the element type of the field as bindable Java type, instead of the attribute type.
         */
        private StubMetamodel elementTypeAsBindableJavaTypeMetamodel() throws NoSuchFieldException {
            var name = OneToManyReportedEntity.class.getDeclaredField("name");
            return new StubMetamodel()
                .entity(Child.class, attribute("root", MANY_TO_ONE, Root.class, Map.of("getBindableJavaType", Object.class, "getJavaMember", name)))
                .entity(Root.class, attribute("tags", ELEMENT_COLLECTION, String.class, OneToManyReportedEntity.class.getDeclaredField("tags")));
        }

        @Test
        void mappingIsResolvedPerEntityManagerFactory() {
            var metadata = new EntityMetadata(Root.class);
            var otherMetamodel = new StubMetamodel().entity(Root.class, attribute("tags", BASIC, String.class), attribute("other", ONE_TO_ONE, Parent.class))
                .entity(Parent.class);
            var mappings = metadata.getMappings(metamodel.entityManagerFactory(), HIBERNATE);
            var otherMappings = metadata.getMappings(otherMetamodel.entityManagerFactory(), HIBERNATE);
            assertTrue(mappings.isElementCollection("tags"));
            assertFalse(otherMappings.isElementCollection("tags"));
            assertEquals(Set.of("parent", "detail", "detail.other", "children.root"), mappings.getManyOrOneToOnes());
            assertEquals(Set.of("other"), otherMappings.getManyOrOneToOnes());
        }

    }

    @Test
    void ofReturnsSameMetadataForSameEntityType() {
        assertSame(EntityMetadata.of(SoftDeletableEntity.class), EntityMetadata.of(SoftDeletableEntity.class));
    }

    private static Set<?> cachedPaths(Mappings mappings) throws ReflectiveOperationException {
        var factoryMappingsField = Mappings.class.getDeclaredField("factoryMappings");
        factoryMappingsField.setAccessible(true);
        var factoryMappings = factoryMappingsField.get(mappings);
        var pathsField = factoryMappings.getClass().getDeclaredField("paths");
        pathsField.setAccessible(true);
        return ((Map<?, ?>) pathsField.get(factoryMappings)).keySet();
    }

    // Stub metamodel -------------------------------------------------------------------------------------------------

    private static final Set<PersistentAttributeType> PLURAL_ATTRIBUTE_TYPES = Set.of(ONE_TO_MANY, MANY_TO_MANY, ELEMENT_COLLECTION);

    private static Attribute<?, ?> attribute(String name, PersistentAttributeType type, Class<?> javaType) {
        return attribute(name, type, javaType, Map.of());
    }

    private static Attribute<?, ?> attribute(String name, PersistentAttributeType type, Class<?> javaType, Field javaMember) {
        return attribute(name, type, javaType, Map.of("getJavaMember", javaMember));
    }

    /**
     * Stubs a plural attribute for collection types, else a singular attribute, both with the given type as Java type and as bindable Java type.
     */
    private static Attribute<?, ?> attribute(String name, PersistentAttributeType type, Class<?> javaType, Map<String, Object> additionalReturnValues) {
        var returnValues = new HashMap<String, Object>(Map.of("getName", name, "getPersistentAttributeType", type, "getBindableJavaType", javaType));
        returnValues.put("getJavaType", javaType);
        returnValues.putAll(additionalReturnValues);
        Class<?> attributeType = PLURAL_ATTRIBUTE_TYPES.contains(type) ? PluralAttribute.class : SingularAttribute.class;
        return (Attribute<?, ?>) stub(attributeType, returnValues);
    }

    private static final class StubMetamodel {

        private final Map<Class<?>, Set<Attribute<?, ?>>> attributes = new HashMap<>();
        private final List<Class<?>> lookedUpTypes = new ArrayList<>();
        private final EntityManagerFactory entityManagerFactory = stub(EntityManagerFactory.class, Map.of("getMetamodel", metamodel()));

        StubMetamodel entity(Class<?> type, Attribute<?, ?>... entityAttributes) {
            attributes.put(type, new LinkedHashSet<>(List.of(entityAttributes)));
            return this;
        }

        EntityManagerFactory entityManagerFactory() {
            return entityManagerFactory;
        }

        private Metamodel metamodel() {
            return (Metamodel) Proxy.newProxyInstance(
                EntityMetadataTest.class.getClassLoader(), new Class<?>[] { Metamodel.class },
                (proxy, method, args) -> {
                    if ("getEntities".equals(method.getName())) {
                        return attributes.keySet().stream().map(type -> stub(EntityType.class, Map.of("getJavaType", type))).collect(toSet());
                    }

                    if (!"entity".equals(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }

                    var type = (Class<?>) args[0];
                    lookedUpTypes.add(type);
                    return stub(EntityType.class, Map.of("getAttributes", attributes.get(type)));
                }
            );
        }

    }

    // Stub entities --------------------------------------------------------------------------------------------------

    static class Root extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class Child extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class Parent extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class Order extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class Address extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class Country extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    @SuppressWarnings("unused")
    static class OneToManyReportedEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

        @ElementCollection
        private List<String> tags;

        @OneToMany
        private List<Child> children;

        private String name;

    }

    static class PlainEntity {
    }

    static class Location {
    }

    static class GeneratedValueEntity extends BaseEntity<Long> {

        private static final long serialVersionUID = 1L;

        @Id
        @GeneratedValue
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

    }

    static class AssignedIdEntity extends BaseEntity<Long> {

        private static final long serialVersionUID = 1L;

        @Id
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

    }

    static class SoftDeletableEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

        @SoftDeletable
        private boolean deleted;

        public boolean isDeleted() {
            return deleted;
        }

    }

}
