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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.model.SoftDeletable;

/**
 * Unit tests for {@link EntityMetadata}. The association mappings are resolved against a stub metamodel built from {@link Proxy} instances, describing
 * <code>Root</code> with a <code>@OneToMany</code> to <code>Child</code>, which has a <code>@ManyToOne</code> back to <code>Root</code>.
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
    class Associations {

        private final StubMetamodel metamodel = new StubMetamodel()
            .entity(
                Root.class, attribute("children", ONE_TO_MANY, Child.class), attribute("tags", ELEMENT_COLLECTION, String.class),
                attribute("parent", ONE_TO_ONE, Parent.class), attribute("detail", ONE_TO_ONE, NonBaseEntity.class)
            )
            .entity(Child.class, attribute("root", MANY_TO_ONE, Root.class), attribute("labels", ELEMENT_COLLECTION, String.class))
            .entity(Parent.class, attribute("name", BASIC, String.class))
            .entity(NonBaseEntity.class, attribute("other", MANY_TO_ONE, Root.class));

        @Test
        void collectsElementCollectionsIncludingNestedOnes() {
            assertEquals(Set.of("tags", "children.labels"), new EntityMetadata(Root.class).getElementCollections(metamodel.entityManager(), HIBERNATE));
        }

        @Test
        void collectsManyOrOneToOnesIncludingNestedOnes() {
            assertEquals(Set.of("parent", "detail", "children.root"), new EntityMetadata(Root.class).getManyOrOneToOnes(metamodel.entityManager(), HIBERNATE));
        }

        @Test
        void fieldNestedInOneToManyIsOneToMany() {
            var metadata = new EntityMetadata(Root.class);
            assertTrue(metadata.isOneToMany("children.labels", metamodel.entityManager(), HIBERNATE));
            assertFalse(metadata.isOneToMany("children", metamodel.entityManager(), HIBERNATE));
            assertFalse(metadata.isOneToMany("parent.name", metamodel.entityManager(), HIBERNATE));
        }

        @Test
        void associationBackToRootTypeIsNotCrawled() {
            new EntityMetadata(Root.class).getManyOrOneToOnes(metamodel.entityManager(), HIBERNATE);
            assertEquals(List.of(Root.class, Child.class, Parent.class), metamodel.crawledTypes);
        }

        @Test
        void associationToNonBaseEntityTypeIsNotCrawled() {
            new EntityMetadata(Root.class).getManyOrOneToOnes(metamodel.entityManager(), HIBERNATE);
            assertFalse(metamodel.crawledTypes.contains(NonBaseEntity.class));
        }

        @Test
        void mappingIsComputedOnlyOnce() {
            var metadata = new EntityMetadata(Root.class);
            var first = metadata.getElementCollections(metamodel.entityManager(), HIBERNATE);
            var crawledTypesAfterFirst = metamodel.crawledTypes.size();
            var second = metadata.getElementCollections(metamodel.entityManager(), HIBERNATE);
            assertSame(first, second);
            assertEquals(crawledTypesAfterFirst, metamodel.crawledTypes.size());
        }

    }

    @Test
    void ofReturnsSameMetadataForSameEntityType() {
        assertSame(EntityMetadata.of(SoftDeletableEntity.class), EntityMetadata.of(SoftDeletableEntity.class));
    }

    // Stub metamodel -------------------------------------------------------------------------------------------------

    private static Attribute<?, ?> attribute(String name, PersistentAttributeType type, Class<?> javaType) {
        return stub(Attribute.class, Map.of("getName", name, "getPersistentAttributeType", type, "getBindableJavaType", javaType), Bindable.class);
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
        private final List<Class<?>> crawledTypes = new ArrayList<>();

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
                    crawledTypes.add(type);
                    return stub(EntityType.class, Map.of("getAttributes", attributes.get(type)));
                }
            );
            return stub(EntityManager.class, Map.of("getMetamodel", metamodel));
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
