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

import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.List;

import jakarta.persistence.Entity;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.GeneratedIdEntity;

/**
 * Unit tests for {@link AuditListener}, invoking its callbacks the way the persistence provider does, outside any CDI or service context. This class lives
 * outside the integration test package so that its stub entities do not end up in the persistence unit of the integration tests.
 */
class AuditListenerTest {

    private static final int RELEASED_ENTITIES = 1000;
    private static final long RELEASE_TIMEOUT_MILLIS = 10_000;

    private final AuditListener listener = new AuditListener();

    @Test
    void callbacksOutsideServiceDoNotThrow() {
        var entity = loaded(new AuditedEntity(), "old");
        entity.setValue("new");
        assertDoesNotThrow(() -> listener.afterUpdate(entity));
    }

    @Test
    void changeOfAuditedFieldIsDetected() {
        var entity = loaded(new AuditedEntity(), "old");
        entity.setValue("new");
        assertEquals(List.of("value: old -> new"), changes(entity));
    }

    @Test
    void unchangedAuditedFieldIsNotDetected() {
        var entity = loaded(new AuditedEntity(), "same");
        entity.setValue("same");
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void changeOfNonAuditedFieldIsNotDetected() {
        var entity = loaded(new AuditedEntity(), "same");
        entity.setName("changed");
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void changeOfAuditedFieldInSuperclassIsDetected() {
        var entity = loaded(new AuditedSubclassEntity(), "old");
        entity.setValue("new");
        assertEquals(List.of("value: old -> new"), changes(entity));
    }

    @Test
    void entityWithoutSnapshotHasNoChanges() {
        var entity = new AuditedEntity();
        entity.setValue("new");
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void instancesOfSameEntityAreDetectedAgainstTheirOwnSnapshot() {
        var first = loaded(new AuditedEntity(), "original");
        var second = loaded(new AuditedEntity(), "original");
        second.setValue("second");
        assertEquals(List.of("value: original -> second"), changes(second));
        first.setValue("first");
        assertEquals(List.of("value: original -> first"), changes(first));
    }

    @Test
    void eachChangeIsDetectedAgainstThePreviousUpdate() {
        var entity = loaded(new AuditedEntity(), "original");
        entity.setValue("first");
        assertEquals(List.of("value: original -> first"), changes(entity));
        entity.setValue("second");
        assertEquals(List.of("value: first -> second"), changes(entity));
    }

    @Test
    void snapshotIsReleasedWithItsEntity() throws InterruptedException {
        assumeFalse(isExplicitGcDisabled(), "System.gc() is disabled");
        var snapshotsBefore = AuditListener.SNAPSHOTS.size();
        range(0, RELEASED_ENTITIES).forEach(i -> loaded(new AuditedEntity(), "released"));
        var deadline = System.currentTimeMillis() + RELEASE_TIMEOUT_MILLIS;

        while (AuditListener.SNAPSHOTS.size() > snapshotsBefore && System.currentTimeMillis() < deadline) {
            System.gc();
            Thread.sleep(10);
        }

        assertTrue(AuditListener.SNAPSHOTS.size() <= snapshotsBefore, "Snapshots of unreachable entities are released");
    }

    static boolean isExplicitGcDisabled() {
        return getRuntimeMXBean().getInputArguments().contains("-XX:+DisableExplicitGC");
    }

    private <E extends AuditedEntity> E loaded(E entity, String value) {
        entity.setId(1L);
        entity.setValue(value);
        listener.beforeUpdate(entity);
        return entity;
    }

    private static List<String> changes(AuditedEntity entity) {
        return AuditListener.detectChanges(entity).stream()
            .map(change -> change.getPropertyName() + ": " + change.getOldValue() + " -> " + change.getNewValue())
            .toList();
    }

    @Entity
    public static class AuditedEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

        @Audit
        private String value;

        private String name;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    @Entity
    public static class AuditedSubclassEntity extends AuditedEntity {

        private static final long serialVersionUID = 1L;

    }

}
