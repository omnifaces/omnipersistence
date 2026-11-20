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
package org.omnifaces.persistence.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.RecordingCDIProvider;

/**
 * Unit tests for {@link BaseEntityListener}, invoking its callbacks the way the persistence provider does.
 */
class BaseEntityListenerTest {

    /**
     * An event is fired as soon as CDI is available, also when it was not available during an earlier callback.
     */
    @Test
    void eventIsFiredOnceCdiBecomesAvailable() {
        var cdi = RecordingCDIProvider.install();

        try {
            var listener = new BaseEntityListener();
            var withoutCdi = new Object();
            var withCdi = new Object();
            listener.onPostPersist(withoutCdi);
            cdi.setAvailable(true);
            listener.onPostPersist(withCdi);
            assertEquals(List.of(withCdi), cdi.getFiredEvents());
        }
        finally {
            cdi.setAvailable(false);
        }
    }

}
