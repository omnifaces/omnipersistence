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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Test helper for asserting that objects which end up in a passivated or replicated HTTP session can be serialized.
 */
final class Serializations {

    private Serializations() {
        throw new AssertionError();
    }

    /**
     * Serializes and immediately deserializes the given object.
     *
     * @param <T> The generic object type.
     * @param object The object to be serialized and deserialized.
     * @return The deserialized object.
     * @throws IOException When the object is not serializable.
     * @throws ClassNotFoundException When the deserialized class cannot be found.
     */
    @SuppressWarnings("unchecked")
    static <T> T serializeAndDeserialize(T object) throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();

        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }

        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

}
