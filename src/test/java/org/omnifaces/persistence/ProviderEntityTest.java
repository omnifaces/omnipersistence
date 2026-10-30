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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.omnifaces.persistence.Provider.ECLIPSELINK;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.Provider.OPENJPA;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.Identifiable;

/**
 * Unit tests for the entity methods of {@link Provider} on entities which do not implement {@link Identifiable}. This class lives outside the integration test
 * package so that its stub entity does not end up in the persistence unit of the integration tests.
 */
class ProviderEntityTest {

    @Test
    void identifierOfNullIsNull() {
        assertNull(HIBERNATE.getIdentifier(null));
    }

    @Test
    void identifierOfNonProxyEntityNotImplementingIdentifiableIsRejected() {
        var entity = new PlainEntity();
        assertThrows(IllegalArgumentException.class, () -> HIBERNATE.getIdentifier(entity));
        assertThrows(IllegalArgumentException.class, () -> ECLIPSELINK.getIdentifier(entity));
        assertThrows(IllegalArgumentException.class, () -> OPENJPA.getIdentifier(entity));
    }

    @Test
    void entityTypeOfSubclassIsEntityAnnotatedSuperclass() {
        assertEquals(PlainEntity.class, HIBERNATE.getEntityType(new PlainEntitySubclass()));
    }

    @Test
    void entityTypeOfNonEntityIsItsOwnClass() {
        assertEquals(String.class, HIBERNATE.getEntityType("nonEntity"));
    }

    @Entity
    static class PlainEntity {

        @Id
        Long code;

    }

    static class PlainEntitySubclass extends PlainEntity {
    }

}
