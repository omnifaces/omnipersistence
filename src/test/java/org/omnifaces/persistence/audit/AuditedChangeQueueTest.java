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

import static jakarta.transaction.Status.STATUS_COMMITTED;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuditedChangeQueueTest {

    private final List<List<String>> fired = new ArrayList<>();

    @Test
    void changesAreFiredInOrderOnceAfterCommit() {
        var queue = new AuditedChangeQueue();
        queue.addAll(List.of(change("first")), this::fire);
        queue.addAll(List.of(change("second"), change("third")), this::fire);
        queue.beforeCompletion();
        assertEquals(List.of(), fired, "Nothing is fired before the completion");
        queue.afterCompletion(STATUS_COMMITTED);
        assertEquals(List.of(List.of("first", "second", "third")), fired);
    }

    @Test
    void changesOfEachAdditionAreFiredByLastGivenFirer() {
        var firedByFirst = new ArrayList<List<String>>();
        var queue = new AuditedChangeQueue();
        queue.addAll(List.of(change("first")), changes -> firedByFirst.add(List.of()));
        queue.addAll(List.of(change("second")), this::fire);
        queue.afterCompletion(STATUS_COMMITTED);
        assertEquals(List.of(), firedByFirst);
        assertEquals(List.of(List.of("first", "second")), fired);
    }

    @Test
    void changesAreDroppedAfterRollback() {
        var queue = new AuditedChangeQueue();
        queue.addAll(List.of(change("first")), this::fire);
        queue.afterCompletion(STATUS_ROLLEDBACK);
        assertEquals(List.of(), fired);
    }

    @Test
    void nothingIsFiredAfterCommitWithoutChanges() {
        new AuditedChangeQueue().afterCompletion(STATUS_COMMITTED);
        assertEquals(List.of(), fired);
    }

    private void fire(List<AuditedChange> changes) {
        fired.add(changes.stream().map(AuditedChange::getPropertyName).toList());
    }

    private static AuditedChange change(String propertyName) {
        return new AuditedChange(new Object(), 1L, "Entity", propertyName, "old", "new");
    }

}
