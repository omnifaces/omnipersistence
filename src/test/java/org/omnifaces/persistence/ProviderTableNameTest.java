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
package org.omnifaces.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.omnifaces.persistence.Provider.HIBERNATE;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.GeneratedIdEntity;

/**
 * Unit tests for {@link Provider#getTableName(Object)} and {@link Provider#getEntityName(Class)}. This class lives outside the integration test package so that
 * its stub entities do not end up in the persistence unit of the integration tests.
 */
class ProviderTableNameTest {

    @Test
    void tableNameIsThatOfTableAnnotation() {
        assertEquals("named_table", HIBERNATE.getTableName(new NamedTableEntity()));
    }

    @Test
    void tableNameDefaultsToUpperCasedSimpleNameWithoutEntityNameAndTableAnnotation() {
        assertEquals("UNANNOTATEDENTITY", HIBERNATE.getTableName(new UnannotatedEntity()));
    }

    @Test
    void tableNameDefaultsToUpperCasedEntityNameWithoutTableAnnotation() {
        assertEquals("MEMO", HIBERNATE.getTableName(new NamedEntity()));
    }

    @Test
    void tableNameDefaultsToUpperCasedEntityNameOfSuperclassWithoutOwnEntityAnnotation() {
        assertEquals("MEMO", HIBERNATE.getTableName(new NamedEntitySubclass()));
    }

    @Test
    void tableNameDefaultsToUpperCasedEntityNameWhenTableAnnotationHasNoName() {
        assertEquals("SCHEMAONLYENTITY", HIBERNATE.getTableName(new SchemaOnlyEntity()));
    }

    @Test
    void entityNameIsThatOfEntityAnnotation() {
        assertEquals("Memo", Provider.getEntityName(NamedEntity.class));
        assertEquals("Memo", Provider.getEntityName(NamedEntitySubclass.class));
    }

    @Test
    void entityNameDefaultsToSimpleNameOfEntityClass() {
        assertEquals("UnannotatedEntity", Provider.getEntityName(UnannotatedEntity.class));
    }

    @Test
    void entityNameDefaultsToSimpleNameOfNonEntityClass() {
        assertEquals("String", Provider.getEntityName(String.class));
    }

    @Entity
    @Table(name = "named_table")
    static class NamedTableEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    @Entity(name = "Memo")
    static class NamedEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class NamedEntitySubclass extends NamedEntity {

        private static final long serialVersionUID = 1L;

    }

    @Entity
    static class UnannotatedEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    @Entity
    @Table(schema = "some_schema")
    static class SchemaOnlyEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

}
