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
package org.omnifaces.persistence.audit;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class of {@link AuditListener}. A thread safe map whose keys are weakly referenced, so that an entry is removed once its key is no longer reachable.
 * Keys are compared by identity, because two instances of the same entity are equal by {@link Object#equals(Object)} while each needs its own entry. A value
 * may not strongly reference its key, else the entry is never removed.
 *
 * @param <K> The generic key type.
 * @param <V> The generic value type.
 */
final class WeakIdentityMap<K, V> {

    private final Map<IdentityKey<K>, V> entries = new ConcurrentHashMap<>();
    private final ReferenceQueue<K> releasedKeys = new ReferenceQueue<>();

    V get(K key) {
        removeReleasedKeys();
        return entries.get(new IdentityKey<>(key, null));
    }

    void put(K key, V value) {
        removeReleasedKeys();
        entries.put(new IdentityKey<>(key, releasedKeys), value);
    }

    int size() {
        removeReleasedKeys();
        return entries.size();
    }

    private void removeReleasedKeys() {
        for (var releasedKey = releasedKeys.poll(); releasedKey != null; releasedKey = releasedKeys.poll()) {
            entries.remove(releasedKey);
        }
    }

    private static final class IdentityKey<K> extends WeakReference<K> {

        private final int hashCode;

        private IdentityKey(K key, ReferenceQueue<K> releasedKeys) {
            super(key, releasedKeys);
            hashCode = System.identityHashCode(key);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            var key = get();
            return key != null && other instanceof IdentityKey<?> otherKey && key == otherKey.get();
        }

    }

}
