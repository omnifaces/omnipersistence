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
package org.omnifaces.persistence.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.Serializations.serializeAndDeserialize;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.Identifiable;
import org.omnifaces.persistence.test.model.Person;

/**
 * {@link Identifiable} does not require its implementations to be serializable, but {@link BaseEntity} and therefore all its subclasses are.
 */
@SuppressWarnings("removal")
class IdentifiableTest {

    @Test
    void identifiableIsNotSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(Identifiable.class));
    }

    @Test
    void baseEntityIsSerializable() throws Exception {
        assertTrue(Serializable.class.isAssignableFrom(BaseEntity.class));

        var person = new Person();
        person.setId(42L);
        assertEquals(person, serializeAndDeserialize(person));
    }

}
