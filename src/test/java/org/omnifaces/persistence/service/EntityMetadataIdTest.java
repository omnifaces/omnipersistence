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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.service.Stubs.stub;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.service.EntityMetadata.Mappings;

/**
 * Unit tests for reading and unsetting the ID via {@link Mappings}. The metamodel is stubbed with a single entity type whose ID attribute is the given member,
 * and a Hibernate proxy is stubbed via its {@link HibernateProxy} and {@link LazyInitializer} interfaces, see {@link Stubs}.
 */
class EntityMetadataIdTest {

    private static final Long ID = 42L;

    @Test
    void idOfEntityIsReadViaIdAttribute() throws ReflectiveOperationException {
        var entity = new PlainEntity();
        entity.code = ID;
        assertEquals(ID, mappings(PlainEntity.class, PlainEntity.class.getDeclaredField("code")).getId(entity));
    }

    @Test
    void idOfIdentifiableEntityIsReadViaGetId() throws ReflectiveOperationException {
        var entity = new IdentifiableEntity();
        entity.setId(ID);
        assertEquals(ID, mappings(IdentifiableEntity.class, IdentifiableEntity.class.getDeclaredField("unrelated")).getId(entity));
    }

    @Test
    void idOfEntityWithPropertyAccessIsUnsetViaSetter() throws ReflectiveOperationException {
        var entity = new PropertyAccessEntity();
        entity.setCode(ID);
        mappings(PropertyAccessEntity.class, PropertyAccessEntity.class.getDeclaredMethod("getCode")).unsetId(entity);
        assertNull(entity.getCode());
    }

    @Test
    void idOfIdentifiableEntityIsUnsetViaSetId() throws ReflectiveOperationException {
        var entity = new IdentifiableEntity();
        entity.setId(ID);
        mappings(IdentifiableEntity.class, IdentifiableEntity.class.getDeclaredField("unrelated")).unsetId(entity);
        assertNull(entity.getId());
    }

    private static Mappings mappings(Class<?> entityClass, Member idMember) {
        var type = stub(Type.class, Map.of("getPersistenceType", Type.PersistenceType.BASIC));
        var id = stub(SingularAttribute.class, Map.of("isId", true, "getName", "code", "getJavaMember", idMember, "getType", type, "getJavaType", Long.class));
        var entityType = stub(
            EntityType.class, Map.of("hasSingleIdAttribute", true, "getSingularAttributes", Set.of(id), "getJavaType", entityClass, "getAttributes", Set.of())
        );
        var metamodel = stub(Metamodel.class, Map.of("getEntities", Set.of(entityType), "entity", entityType));
        return new EntityMetadata(entityClass).getMappings(stub(EntityManagerFactory.class, Map.of("getMetamodel", metamodel)), HIBERNATE);
    }

    static class PlainEntity {

        Long code;

    }

    static class PropertyAccessEntity {

        private Long value;

        public Long getCode() {
            return value;
        }

        public void setCode(Long code) {
            value = code;
        }

    }

    static class IdentifiableEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

        Long unrelated;

    }

}
