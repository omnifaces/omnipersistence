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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WeakIdentityMap}.
 */
class WeakIdentityMapTest {

    private static final long RELEASE_TIMEOUT_MILLIS = 10_000;

    @Test
    void equalKeysHaveEntriesOfTheirOwn() {
        var map = new WeakIdentityMap<String, String>();
        var first = new String("key");
        var second = new String("key");
        map.put(first, "first");
        map.put(second, "second");
        assertEquals("first", map.get(first));
        assertEquals("second", map.get(second));
        assertNull(map.get(new String("key")));
        assertEquals(2, map.size());
    }

    @Test
    void putReplacesValueOfSameKey() {
        var map = new WeakIdentityMap<Object, String>();
        var key = new Object();
        map.put(key, "first");
        map.put(key, "second");
        assertEquals("second", map.get(key));
        assertEquals(1, map.size());
    }

    @Test
    void entryIsRemovedOnceKeyIsUnreachable() throws InterruptedException {
        assumeFalse(AuditListenerTest.isExplicitGcDisabled(), "System.gc() is disabled");
        var map = new WeakIdentityMap<Object, String>();
        map.put(new Object(), "released");
        var deadline = System.currentTimeMillis() + RELEASE_TIMEOUT_MILLIS;

        while (map.size() > 0 && System.currentTimeMillis() < deadline) {
            System.gc();
            Thread.sleep(10);
        }

        assertEquals(0, map.size());
    }

}
