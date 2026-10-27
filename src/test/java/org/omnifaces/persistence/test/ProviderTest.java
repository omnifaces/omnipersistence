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
import static org.omnifaces.persistence.Provider.ECLIPSELINK;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.Provider.OPENJPA;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.Provider;

/**
 * Unit tests for {@link Provider}. Attributes are stubbed with {@link Proxy} instances.
 */
class ProviderTest {

    @Nested
    class GetBindableJavaType {

        @Test
        void singularAttributeReturnsBindableJavaType() {
            var attribute = attribute(SingularAttribute.class, String.class, Integer.class);
            assertEquals(Integer.class, HIBERNATE.getBindableJavaType(attribute));
            assertEquals(Integer.class, ECLIPSELINK.getBindableJavaType(attribute));
        }

        @Test
        void singularAttributeReturnsJavaTypeOnOpenJpa() {
            assertEquals(String.class, OPENJPA.getBindableJavaType(attribute(SingularAttribute.class, String.class, Integer.class)));
        }

        @Test
        void pluralAttributeReturnsBindableJavaType() {
            var attribute = attribute(PluralAttribute.class, List.class, String.class);
            assertEquals(String.class, HIBERNATE.getBindableJavaType(attribute));
            assertEquals(String.class, OPENJPA.getBindableJavaType(attribute));
        }

        @Test
        void nonBindableAttributeReturnsJavaType() {
            assertEquals(String.class, HIBERNATE.getBindableJavaType(attribute(Attribute.class, String.class, Integer.class)));
        }

    }

    private static Attribute<?, ?> attribute(Class<?> type, Class<?> javaType, Class<?> bindableJavaType) {
        var returnValues = Map.of("getJavaType", javaType, "getBindableJavaType", bindableJavaType);
        return (Attribute<?, ?>) Proxy.newProxyInstance(ProviderTest.class.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (!returnValues.containsKey(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }

            return returnValues.get(method.getName());
        });
    }

}
