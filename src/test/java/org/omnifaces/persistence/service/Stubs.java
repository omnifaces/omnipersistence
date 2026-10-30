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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stubs of Jakarta Persistence interfaces for unit tests, built from {@link Proxy} instances.
 */
final class Stubs {

    private Stubs() {
        throw new AssertionError();
    }

    /**
     * Stubs the given interface, returning the given value per method name. Any other method throws {@link UnsupportedOperationException}.
     *
     * @param <T> The generic interface type.
     * @param type The interface to stub.
     * @param returnValues The return values per method name.
     * @param additionalTypes Additional interfaces the stub implements.
     * @return The stub.
     */
    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type, Map<String, Object> returnValues, Class<?>... additionalTypes) {
        var types = new ArrayList<Class<?>>(List.of(type));
        types.addAll(List.of(additionalTypes));
        return (T) Proxy.newProxyInstance(Stubs.class.getClassLoader(), types.toArray(Class<?>[]::new), (proxy, method, args) -> {
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

}
