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
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_MANY;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_MANY;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_ONE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.Provider.OPENJPA;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.EntityManager;
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
import org.omnifaces.persistence.service.EntityMetadata.Associations;

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
                attribute("parent", ONE_TO_ONE, Parent.class), attribute("detail", ONE_TO_ONE, NonBaseEntity.class)
            )
            .entity(Child.class, attribute("root", MANY_TO_ONE, Root.class), attribute("labels", ELEMENT_COLLECTION, String.class))
            .entity(Parent.class, attribute("name", BASIC, String.class))
            .entity(NonBaseEntity.class, attribute("other", MANY_TO_ONE, Root.class));

        private Associations associations(EntityMetadata metadata) {
            return metadata.getAssociations(metamodel.entityManager(), HIBERNATE);
        }

        @Test
        void fieldEndingInElementCollectionIsElementCollection() {
            var associations = associations(new EntityMetadata(Root.class));
            assertTrue(associations.isElementCollection("tags"));
            assertTrue(associations.isElementCollection("children.labels"));
            assertFalse(associations.isElementCollection("children"));
            assertFalse(associations.isElementCollection("parent.name"));
        }

        @Test
        void fieldNestedInOneToManyIsOneToMany() {
            var associations = associations(new EntityMetadata(Root.class));
            assertTrue(associations.isOneToMany("children.labels"));
            assertFalse(associations.isOneToMany("children"));
            assertFalse(associations.isOneToMany("parent.name"));
        }

        @Test
        void unknownFieldNestedInOneToManyIsOneToMany() {
            var associations = associations(new EntityMetadata(Root.class));
            assertTrue(associations.isOneToMany("children.unknown"));
            assertTrue(associations.isOneToMany("children."));
        }

        @Test
        void unknownFieldIsNeitherElementCollectionNorOneToMany() {
            var associations = associations(new EntityMetadata(Root.class));
            assertFalse(associations.isElementCollection("unknown"));
            assertFalse(associations.isOneToMany("unknown.name"));
        }

        @Test
        void explicitJoinFieldIsNeitherElementCollectionNorOneToMany() {
            var associations = associations(new EntityMetadata(Root.class));
            assertFalse(associations.isElementCollection("@tags"));
            assertFalse(associations.isOneToMany("@children.labels"));
        }

        @Test
        void fieldPathBackThroughRootTypeIsResolved() {
            var associations = associations(new EntityMetadata(Root.class));
            assertTrue(associations.isElementCollection("children.root.tags"));
            assertTrue(associations.isOneToMany("children.root.tags"));
        }

        @Test
        void fieldPathThroughNonBaseEntityTypeIsNotFollowed() {
            var associations = associations(new EntityMetadata(Root.class));
            assertFalse(associations.isElementCollection("detail.other"));
            assertFalse(metamodel.lookedUpTypes.contains(NonBaseEntity.class));
        }

        @Test
        void fieldPathsToTypeReachedViaTwoPathsAreBothResolved() {
            var twoPathsMetamodel = new StubMetamodel()
                .entity(Order.class, attribute("billingAddress", MANY_TO_ONE, Address.class), attribute("shippingAddress", MANY_TO_ONE, Address.class))
                .entity(Address.class, attribute("lines", ELEMENT_COLLECTION, String.class), attribute("countries", ONE_TO_MANY, Country.class))
                .entity(Country.class, attribute("name", BASIC, String.class));
            var associations = new EntityMetadata(Order.class).getAssociations(twoPathsMetamodel.entityManager(), HIBERNATE);
            assertTrue(associations.isElementCollection("billingAddress.lines"));
            assertTrue(associations.isElementCollection("shippingAddress.lines"));
            assertTrue(associations.isOneToMany("billingAddress.countries.name"));
            assertTrue(associations.isOneToMany("shippingAddress.countries.name"));
        }

        @Test
        void resolvedFieldPathIsCached() {
            var associations = associations(new EntityMetadata(Root.class));
            associations.isElementCollection("children.labels");
            var lookupsAfterFirst = metamodel.lookedUpTypes.size();
            associations.isElementCollection("children.labels");
            associations.isOneToMany("children.labels");
            assertEquals(lookupsAfterFirst, metamodel.lookedUpTypes.size());
        }

        @Test
        void unknownFieldPathReusesCachedAttributeMappings() {
            var associations = associations(new EntityMetadata(Root.class));
            associations.isElementCollection("children.unknown");
            var lookupsAfterFirst = metamodel.lookedUpTypes.size();
            associations.isElementCollection("children.unknown");
            associations.isElementCollection("unknown");
            assertEquals(lookupsAfterFirst, metamodel.lookedUpTypes.size());
        }

        @Test
        void unresolvableFieldPathIsNotCached() throws ReflectiveOperationException {
            var associations = associations(new EntityMetadata(Root.class));
            associations.isElementCollection("tags");
            associations.isElementCollection("unknown");
            associations.isElementCollection("parent.name.junk");
            associations.isOneToMany("children.labels.junk");
            associations.isElementCollection("tags.");
            associations.isElementCollection("children.labels..");
            assertEquals(Set.of("tags"), cachedPaths(associations));
        }

        @Test
        void fieldPathWithoutAttributeNamesIsNeitherElementCollectionNorOneToMany() {
            var associations = associations(new EntityMetadata(Root.class));
            assertFalse(associations.isElementCollection("."));
            assertFalse(associations.isOneToMany(".."));
        }

        @Test
        void collectsManyOrOneToOnesIncludingNestedOnes() {
            assertEquals(Set.of("parent", "detail", "children.root"), associations(new EntityMetadata(Root.class)).getManyOrOneToOnes());
        }

        @Test
        void manyOrOneToOneCrawlDoesNotGoBackIntoRootType() {
            associations(new EntityMetadata(Root.class)).getManyOrOneToOnes();
            assertEquals(List.of(Root.class, Child.class, Parent.class), metamodel.lookedUpTypes);
        }

        @Test
        void manyOrOneToOneCrawlDoesNotGoIntoNonBaseEntityType() {
            associations(new EntityMetadata(Root.class)).getManyOrOneToOnes();
            assertFalse(metamodel.lookedUpTypes.contains(NonBaseEntity.class));
        }

        @Test
        void manyOrOneToOnesAreComputedOnlyOnce() {
            var metadata = new EntityMetadata(Root.class);
            var first = metadata.getAssociations(metamodel.entityManager(), HIBERNATE).getManyOrOneToOnes();
            var lookedUpTypesAfterFirst = metamodel.lookedUpTypes.size();
            var second = metadata.getAssociations(metamodel.entityManager(), HIBERNATE).getManyOrOneToOnes();
            assertSame(first, second);
            assertEquals(lookedUpTypesAfterFirst, metamodel.lookedUpTypes.size());
        }

        @Test
        void attributeMappingsAreSharedByEntityTypesOfSameEntityManagerFactory() {
            associations(new EntityMetadata(Root.class)).isElementCollection("children.labels");
            var lookupsAfterRoot = metamodel.lookedUpTypes.size();
            assertTrue(associations(new EntityMetadata(Child.class)).isElementCollection("labels"));
            assertEquals(lookupsAfterRoot, metamodel.lookedUpTypes.size());
        }

        @Test
        void elementCollectionReportedAsOneToManyIsCorrectedByProvider() throws NoSuchFieldException {
            var openJpa = new EntityMetadata(OneToManyReportedEntity.class).getAssociations(oneToManyReportedMetamodel().entityManager(), OPENJPA);
            assertTrue(openJpa.isElementCollection("tags"));
            assertFalse(openJpa.isOneToMany("tags.x"));
            assertTrue(openJpa.isOneToMany("children.labels"));

            var hibernate = new EntityMetadata(OneToManyReportedEntity.class).getAssociations(oneToManyReportedMetamodel().entityManager(), HIBERNATE);
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
            var openJpa = new EntityMetadata(Child.class).getAssociations(elementTypeAsBindableJavaTypeMetamodel().entityManager(), OPENJPA);
            assertTrue(openJpa.isElementCollection("root.tags"));

            var hibernate = new EntityMetadata(Child.class).getAssociations(elementTypeAsBindableJavaTypeMetamodel().entityManager(), HIBERNATE);
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
            var associations = metadata.getAssociations(metamodel.entityManager(), HIBERNATE);
            var otherAssociations = metadata.getAssociations(otherMetamodel.entityManager(), HIBERNATE);
            assertTrue(associations.isElementCollection("tags"));
            assertFalse(otherAssociations.isElementCollection("tags"));
            assertEquals(Set.of("parent", "detail", "children.root"), associations.getManyOrOneToOnes());
            assertEquals(Set.of("other"), otherAssociations.getManyOrOneToOnes());
        }

    }

    @Test
    void ofReturnsSameMetadataForSameEntityType() {
        assertSame(EntityMetadata.of(SoftDeletableEntity.class), EntityMetadata.of(SoftDeletableEntity.class));
    }

    private static Set<?> cachedPaths(Associations associations) throws ReflectiveOperationException {
        var mappingsField = Associations.class.getDeclaredField("mappings");
        mappingsField.setAccessible(true);
        var mappings = mappingsField.get(associations);
        var pathsField = mappings.getClass().getDeclaredField("paths");
        pathsField.setAccessible(true);
        return ((Map<?, ?>) pathsField.get(mappings)).keySet();
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

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Object> returnValues, Class<?>... additionalTypes) {
        var types = new ArrayList<Class<?>>(List.of(type));
        types.addAll(List.of(additionalTypes));
        return (T) Proxy.newProxyInstance(EntityMetadataTest.class.getClassLoader(), types.toArray(Class<?>[]::new), (proxy, method, args) -> {
            switch (method.getName()) {
                case "hashCode" :
                    return System.identityHashCode(proxy);
                case "equals" :
                    return proxy == args[0];
                case "toString" :
                    return type.getSimpleName() + returnValues;
                default :
                    if (!returnValues.containsKey(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }

                    return returnValues.get(method.getName());
            }
        });
    }

    private static final class StubMetamodel {

        private final Map<Class<?>, Set<Attribute<?, ?>>> attributes = new HashMap<>();
        private final List<Class<?>> lookedUpTypes = new ArrayList<>();
        private final EntityManagerFactory entityManagerFactory = stub(EntityManagerFactory.class, Map.of());

        StubMetamodel entity(Class<?> type, Attribute<?, ?>... entityAttributes) {
            attributes.put(type, new LinkedHashSet<>(List.of(entityAttributes)));
            return this;
        }

        EntityManager entityManager() {
            Metamodel metamodel = (Metamodel) Proxy.newProxyInstance(
                EntityMetadataTest.class.getClassLoader(), new Class<?>[] { Metamodel.class },
                (proxy, method, args) -> {
                    if (!"entity".equals(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }

                    var type = (Class<?>) args[0];
                    lookedUpTypes.add(type);
                    return stub(EntityType.class, Map.of("getAttributes", attributes.get(type)));
                }
            );
            return stub(EntityManager.class, Map.of("getMetamodel", metamodel, "getEntityManagerFactory", entityManagerFactory));
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

    static class NonBaseEntity {
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
