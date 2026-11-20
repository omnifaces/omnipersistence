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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuditedChangesTest {

    private final AuditedChange first = change("first");
    private final AuditedChange second = change("second");

    @Test
    void changesAreKeptInOrderByEachAccessor() {
        var changes = new AuditedChanges(List.of(first, second));
        var iterated = new ArrayList<AuditedChange>();
        changes.forEach(iterated::add);
        assertEquals(List.of(first, second), changes.getChanges());
        assertEquals(List.of(first, second), changes.stream().toList());
        assertEquals(List.of(first, second), iterated);
    }

    @Test
    void changesAreNotAffectedByLaterChangeOfGivenList() {
        var source = new ArrayList<>(List.of(first));
        var changes = new AuditedChanges(source);
        source.add(second);
        assertEquals(List.of(first), changes.getChanges());
    }

    @Test
    void changesAreUnmodifiable() {
        var changes = new AuditedChanges(List.of(first)).getChanges();
        assertThrows(UnsupportedOperationException.class, () -> changes.add(second));
    }

    private static AuditedChange change(String propertyName) {
        return new AuditedChange(new Object(), 1L, "Entity", propertyName, "old", "new");
    }

}
