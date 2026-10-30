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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.persistence.test.Serializations.serializeAndDeserialize;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.service.EntityRepository.SerializableSupplier;
import org.omnifaces.persistence.test.model.Text;

/**
 * Unit tests for {@link EntityRepository} and the type argument resolution of {@link EntityRepositoryProducer}. The behavior against a real entity manager is
 * covered by the integration tests.
 */
class EntityRepositoryTest {

    private static final SerializableSupplier<EntityManager> UNUSABLE_ENTITY_MANAGER = () -> {
        throw new IllegalStateException("No entity manager");
    };

    @Nested
    class Construction {

        @Test
        void constructionDoesNotInvokeEntityManagerSupplier() {
            var repository = new EntityRepository<>(UNUSABLE_ENTITY_MANAGER, Long.class, Text.class);
            assertEquals(Long.class, repository.getIdentifierType());
            assertEquals(Text.class, repository.getEntityType());
        }

        @Test
        void entityManagerSupplierIsInvokedOnFirstAction() {
            var repository = new EntityRepository<>(UNUSABLE_ENTITY_MANAGER, Long.class, Text.class);
            assertThrows(IllegalStateException.class, repository::getProvider);
        }

        @Test
        void nullArgumentsAreRejected() {
            assertThrows(NullPointerException.class, () -> new EntityRepository<>(null, Long.class, Text.class));
            assertThrows(NullPointerException.class, () -> new EntityRepository<>(UNUSABLE_ENTITY_MANAGER, null, Text.class));
            assertThrows(NullPointerException.class, () -> new EntityRepository<Long, Text>(UNUSABLE_ENTITY_MANAGER, Long.class, null));
        }

        @Test
        void serializationKeepsTypesWithoutInvokingEntityManagerSupplier() throws IOException, ClassNotFoundException {
            var repository = serializeAndDeserialize(new EntityRepository<>(UNUSABLE_ENTITY_MANAGER, Long.class, Text.class));
            assertEquals(Long.class, repository.getIdentifierType());
            assertEquals(Text.class, repository.getEntityType());
            assertThrows(IllegalStateException.class, repository::getEntityManager);
        }

    }

    @Nested
    class TypeArgumentResolution {

        EntityRepository<Long, Text> concrete;

        @SuppressWarnings("rawtypes")
        EntityRepository raw;

        @Test
        void concreteTypeArgumentsAreResolved() throws NoSuchFieldException {
            assertArrayEquals(new Class<?>[] { Long.class, Text.class }, EntityRepositoryProducer.resolveTypeArguments(typeOf("concrete")));
        }

        @Test
        void rawTypeIsRejected() throws NoSuchFieldException {
            var type = typeOf("raw");
            assertThrows(IllegalArgumentException.class, () -> EntityRepositoryProducer.resolveTypeArguments(type));
        }

        @Test
        void typeVariableIsRejected() throws NoSuchFieldException {
            var type = GenericHolder.class.getDeclaredField("repository").getGenericType();
            assertThrows(IllegalArgumentException.class, () -> EntityRepositoryProducer.resolveTypeArguments(type));
        }

        private Type typeOf(String fieldName) throws NoSuchFieldException {
            return TypeArgumentResolution.class.getDeclaredField(fieldName).getGenericType();
        }

    }

    static class GenericHolder<I extends Comparable<I> & Serializable, E extends BaseEntity<I>> {

        EntityRepository<I, E> repository;

    }

}
