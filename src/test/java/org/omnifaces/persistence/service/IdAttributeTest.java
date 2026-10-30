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

import static jakarta.persistence.metamodel.Type.PersistenceType.BASIC;
import static jakarta.persistence.metamodel.Type.PersistenceType.EMBEDDABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.service.Stubs.stub;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import jakarta.persistence.metamodel.Type.PersistenceType;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.GeneratedIdEntity;

/**
 * Unit tests for {@link IdAttribute}. The metamodel is stubbed, see {@link Stubs}.
 */
class IdAttributeTest {

    @Test
    void nameIsThatOfIdAttribute() throws ReflectiveOperationException {
        assertEquals("code", idAttribute(FieldAccessEntity.class.getDeclaredField("code")).getName());
    }

    @Test
    void idIsReadAndSetViaFieldOnFieldAccess() throws ReflectiveOperationException {
        var entity = new FieldAccessEntity();
        entity.code = 42L;
        var idAttribute = idAttribute(FieldAccessEntity.class.getDeclaredField("code"));
        assertEquals(42L, idAttribute.<Long>get(entity));
        idAttribute.set(entity, null);
        assertNull(entity.code);
    }

    @Test
    void idIsReadAndSetViaGetterAndSetterOnPropertyAccess() throws ReflectiveOperationException {
        var entity = new PropertyAccessEntity();
        entity.setCode(42L);
        var idAttribute = idAttribute(PropertyAccessEntity.class.getDeclaredMethod("getCode"));
        assertEquals(42L, idAttribute.<Long>get(entity));
        idAttribute.set(entity, null);
        assertNull(entity.getCode());
    }

    @Test
    void idIsSetViaIdentifiableMethodsWhenImplemented() throws ReflectiveOperationException {
        var entity = new IdentifiableEntity();
        entity.setId(42L);
        idAttribute(IdentifiableEntity.class.getDeclaredField("unrelated")).set(entity, null);
        assertNull(entity.getId());
    }

    @Test
    void idClassIsRejected() {
        var entityType = stub(EntityType.class, Map.of("hasSingleIdAttribute", false, "getJavaType", Object.class));
        assertThrows(IllegalArgumentException.class, () -> IdAttribute.of(entityType));
    }

    @Test
    void embeddedIdIsAccepted() throws ReflectiveOperationException {
        var entityType = entityType(FieldAccessEntity.class.getDeclaredField("code"), EMBEDDABLE, Object.class);
        assertEquals("code", IdAttribute.of(entityType).getName());
    }

    @Test
    void accessIsThatOfIdMember() throws ReflectiveOperationException {
        assertFalse(idAttribute(FieldAccessEntity.class.getDeclaredField("code")).isPropertyAccess());
        assertTrue(idAttribute(PropertyAccessEntity.class.getDeclaredMethod("getCode")).isPropertyAccess());
    }

    @Test
    void primitiveIdIsRejected() throws ReflectiveOperationException {
        var entityType = entityType(PrimitiveIdEntity.class.getDeclaredField("code"), BASIC, long.class);
        assertThrows(IllegalArgumentException.class, () -> IdAttribute.of(entityType));
    }

    private static IdAttribute idAttribute(Member javaMember) {
        return IdAttribute.of(entityType(javaMember, BASIC, Long.class));
    }

    private static EntityType<?> entityType(Member javaMember, PersistenceType persistenceType, Class<?> javaType) {
        var nonId = stub(SingularAttribute.class, Map.of("isId", false));
        var type = stub(Type.class, Map.of("getPersistenceType", persistenceType));
        var id = stub(
            SingularAttribute.class, Map.of("isId", true, "getName", "code", "getJavaMember", javaMember, "getType", type, "getJavaType", javaType)
        );
        return stub(EntityType.class, Map.of("hasSingleIdAttribute", true, "getSingularAttributes", Set.of(nonId, id), "getJavaType", Object.class));
    }

    static class FieldAccessEntity {

        Long code;

    }

    static class PrimitiveIdEntity {

        long code;

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
