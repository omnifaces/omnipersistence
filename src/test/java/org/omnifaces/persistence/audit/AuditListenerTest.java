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

import static jakarta.transaction.Status.STATUS_ACTIVE;
import static jakarta.transaction.Status.STATUS_COMMITTED;
import static jakarta.transaction.Status.STATUS_MARKED_ROLLBACK;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static jakarta.transaction.Status.STATUS_ROLLING_BACK;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.omnifaces.persistence.RecordingCDIProvider;
import org.omnifaces.persistence.model.GeneratedIdEntity;

/**
 * Unit tests for {@link AuditListener}, invoking its callbacks the way the persistence provider does, outside any CDI or service context. This class lives
 * outside the integration test package so that its stub entities do not end up in the persistence unit of the integration tests.
 */
class AuditListenerTest {

    private static final int RELEASED_ENTITIES = 1000;
    private static final long RELEASE_TIMEOUT_MILLIS = 10_000;
    private static final long INSTANT = 1_700_000_000_000L;

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
    void changeOfAuditedFieldOfEntityWithoutBaseEntityIsDetected() {
        var entity = new PlainAuditedEntity();
        entity.setValue("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        assertEquals(List.of("value: old -> new"), changes(entity));
    }

    @Test
    void entityNameIsTheJakartaPersistenceEntityName() {
        var named = new NamedAuditedEntity();
        named.setValue("old");
        listener.beforeUpdate(named);
        named.setValue("new");
        assertEquals(List.of("Memo"), entityNames(named), "Name of @Entity");

        var plain = new PlainAuditedEntity();
        plain.setValue("old");
        listener.beforeUpdate(plain);
        plain.setValue("new");
        assertEquals(List.of("PlainAuditedEntity"), entityNames(plain), "Simple class name when @Entity has no name");

        var subclass = new NamedAuditedSubclass();
        subclass.setValue("old");
        listener.beforeUpdate(subclass);
        subclass.setValue("new");
        assertEquals(List.of("Memo"), entityNames(subclass), "Name of @Entity of superclass when subclass has no @Entity");
    }

    @Test
    void entityIdOfBaseEntityIsItsId() {
        var entity = loaded(new AuditedEntity(), "old");
        entity.setValue("new");
        assertEquals(List.of(1L), entityIds(entity));
    }

    @Test
    void entityIdOfEntityWithoutBaseEntityIsItsIdField() {
        var entity = new PlainAuditedEntity();
        entity.setCode(42L);
        entity.setValue("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        assertEquals(List.of(42L), entityIds(entity));
    }

    @Test
    void entityIdOfEntityWithIdOnGetterIsItsIdGetter() {
        var entity = new PropertyAccessAuditedEntity();
        entity.setKey("k");
        entity.setValue("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        assertEquals(List.of("k"), entityIds(entity));
    }

    @Test
    void entityIdOfEntityWithEmbeddedIdIsItsEmbeddedId() {
        var entity = new EmbeddedIdAuditedEntity();
        var id = new CompositeId(1L, 2L);
        entity.setId(id);
        entity.setValue("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        assertEquals(List.of(id), entityIds(entity));
    }

    @Test
    void entityIdOfEntityWithIdInMappedSuperclassIsItsIdField() {
        var entity = new MappedSuperclassAuditedEntity();
        entity.setCode(42L);
        entity.setValue("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        assertEquals(List.of(42L), entityIds(entity));
    }

    @Test
    void entityIdIsSameForEachChangeOfOneUpdate() {
        var entity = new TwoAuditedFieldsEntity();
        entity.setCode(42L);
        entity.setValue("old");
        entity.setOther("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        entity.setOther("new");
        assertEquals(List.of(42L, 42L), entityIds(entity));
    }

    @Test
    void entityIdOfEntityWithIdClassIsNull() {
        var entity = new CompositeIdAuditedEntity();
        entity.setValue("old");
        listener.beforeUpdate(entity);
        entity.setValue("new");
        assertEquals(singletonList(null), entityIds(entity));
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
    void mergedEntityWithoutSnapshotIsDetectedAgainstSnapshotOfDetachedEntity() {
        var detached = loaded(new AuditedEntity(), "original");
        var managed = new AuditedEntity();
        managed.setValue("modified");
        AuditListener.copySnapshot(detached, managed);
        assertEquals(List.of("value: original -> modified"), changes(managed));
    }

    @Test
    void mergedEntityWithSnapshotKeepsItsOwnSnapshot() {
        var detached = loaded(new AuditedEntity(), "stale");
        var managed = loaded(new AuditedEntity(), "current");
        managed.setValue("modified");
        AuditListener.copySnapshot(detached, managed);
        assertEquals(List.of("value: current -> modified"), changes(managed));
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

    @Test
    void timestampLoadedForDateIsNotAChangeWhenSetToDateOfSameInstant() {
        var entity = new ValueTypesAuditedEntity();
        entity.setDate(new Timestamp(INSTANT));
        listener.beforeUpdate(entity);
        entity.setDate(new Date(INSTANT));
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void dateMutatedInPlaceIsAChange() {
        var entity = new ValueTypesAuditedEntity();
        entity.setDate(new Date(INSTANT));
        listener.beforeUpdate(entity);
        entity.getDate().setTime(INSTANT + 1);
        assertEquals(List.of("date"), changedProperties(entity));
    }

    @Test
    void byteArrayWithSameContentIsNotAChange() {
        var entity = new ValueTypesAuditedEntity();
        entity.setBytes(new byte[] { 1, 2 });
        listener.beforeUpdate(entity);
        entity.setBytes(new byte[] { 1, 2 });
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void byteArrayMutatedInPlaceIsAChange() {
        var entity = new ValueTypesAuditedEntity();
        entity.setBytes(new byte[] { 1, 2 });
        listener.beforeUpdate(entity);
        entity.getBytes()[0] = 3;
        assertEquals(List.of("bytes"), changedProperties(entity));
    }

    @Test
    void mutatingNewValueOfAuditedChangeDoesNotAlterSnapshot() {
        var entity = new ValueTypesAuditedEntity();
        entity.setBytes(new byte[] { 1, 2 });
        listener.beforeUpdate(entity);
        entity.setBytes(new byte[] { 3, 4 });
        var change = AuditListener.detectChanges(entity).get(0);
        ((byte[]) change.getNewValue())[0] = 5;
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void bigDecimalWithOtherScaleIsNotAChange() {
        var entity = new ValueTypesAuditedEntity();
        entity.setAmount(new BigDecimal("5.0"));
        listener.beforeUpdate(entity);
        entity.setAmount(new BigDecimal("5.00"));
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void bigDecimalWithOtherValueIsAChange() {
        var entity = new ValueTypesAuditedEntity();
        entity.setAmount(new BigDecimal("5.0"));
        listener.beforeUpdate(entity);
        entity.setAmount(new BigDecimal("5.01"));
        assertEquals(List.of("amount: 5.0 -> 5.01"), changes(entity));
    }

    @Test
    void nullToValueIsAChange() {
        var entity = loaded(new AuditedEntity(), null);
        entity.setValue("new");
        assertEquals(List.of("value: null -> new"), changes(entity));
    }

    @Test
    void valueToNullIsAChange() {
        var entity = loaded(new AuditedEntity(), "old");
        entity.setValue(null);
        assertEquals(List.of("value: old -> null"), changes(entity));
    }

    @Test
    void nullToNullIsNotAChange() {
        var entity = loaded(new AuditedEntity(), null);
        entity.setValue(null);
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void booleanFlipIsAChange() {
        var entity = new TypedAuditedEntity();
        listener.beforeUpdate(entity);
        entity.setActive(true);
        assertEquals(List.of("active: false -> true"), changes(entity));
    }

    @Test
    void booleanUnchangedIsNotAChange() {
        var entity = new TypedAuditedEntity();
        listener.beforeUpdate(entity);
        entity.setActive(false);
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void enumChangeIsAChange() {
        var entity = new TypedAuditedEntity();
        entity.setStatus(Status.NEW);
        listener.beforeUpdate(entity);
        entity.setStatus(Status.DONE);
        assertEquals(List.of("status: NEW -> DONE"), changes(entity));
    }

    @Test
    void enumToNullAndBackAreChanges() {
        var entity = new TypedAuditedEntity();
        entity.setStatus(Status.NEW);
        listener.beforeUpdate(entity);
        entity.setStatus(null);
        assertEquals(List.of("status: NEW -> null"), changes(entity));
        entity.setStatus(Status.NEW);
        assertEquals(List.of("status: null -> NEW"), changes(entity));
    }

    @Test
    void enumUnchangedIsNotAChange() {
        var entity = new TypedAuditedEntity();
        entity.setStatus(Status.NEW);
        listener.beforeUpdate(entity);
        entity.setStatus(Status.NEW);
        assertEquals(List.of(), changes(entity));
    }

    @Test
    void auditedFieldWithoutGetterIsNotAudited() {
        var entity = new TypedAuditedEntity();
        listener.beforeUpdate(entity);
        entity.hide("changed");
        assertEquals(List.of(), changes(entity));
    }

    /**
     * An {@link AuditedChange} is fired as soon as CDI is available, also when it was not available during an earlier update.
     */
    @Test
    void auditedChangeIsFiredOnceCdiBecomesAvailable() {
        var cdi = RecordingCDIProvider.install();

        try {
            var entity = loaded(new AuditedEntity(), "old");
            entity.setValue("first");
            listener.afterUpdate(entity);
            cdi.setAvailable(true);
            entity.setValue("second");
            listener.afterUpdate(entity);
            assertEquals(List.of("value: first -> second", "[value: first -> second]"), describeEvents(cdi.getFiredEvents()));
        }
        finally {
            cdi.setAvailable(false);
        }
    }

    /**
     * The {@link AuditedChange} events are queued per JTA transaction and fired after its commit, followed by one {@link AuditedChanges} event with all of
     * them. When they cannot be queued, they are fired immediately, during the flush, followed by one {@link AuditedChanges} event with the changes of the
     * flushed entity, with a warning which is logged once per entity class and reason, not on every change.
     */
    @Nested
    class Queueing {

        private final List<String> warnings = new CopyOnWriteArrayList<>();
        private final WarningRecorder warningRecorder = new WarningRecorder(warnings);
        private final Logger logger = Logger.getLogger(AuditListener.class.getName());
        private final StubRegistry registry = new StubRegistry();
        private RecordingCDIProvider cdi;

        @BeforeEach
        void setUp() {
            cdi = RecordingCDIProvider.install();
            cdi.setAvailable(true);
            AuditListener.WARNED_FALLBACKS.clear();
            AuditListener.useRegistryLookup(() -> registry);
            logger.addHandler(warningRecorder);
        }

        @AfterEach
        void tearDown() {
            logger.removeHandler(warningRecorder);
            AuditListener.useRegistryLookup(AuditListener::lookupRegistry);
            cdi.setAvailable(false);
        }

        @Test
        void queueIsRegisteredWhenAuditedEntityIsLoadedInActiveTransaction() {
            loaded(new AuditedEntity(), "old");
            assertEquals(1, registry.synchronizations.size());
        }

        @Test
        void auditedChangeInActiveTransactionIsFiredOnlyAfterCommit() {
            updateTwice(loaded(new AuditedEntity(), "old"));
            assertEquals(List.of(), firedEvents(), "Nothing is fired during the flush");
            registry.complete(STATUS_COMMITTED);
            assertEquals(
                List.of("value: old -> first", "value: first -> second", "[value: old -> first, value: first -> second]"), firedEvents(),
                "Each change is fired after the commit, followed by all changes"
            );
            assertEquals(List.of(), warnings);
        }

        @Test
        void auditedChangesHoldsChangesOfAllEntitiesOfTransaction() {
            var entity = loaded(new AuditedEntity(), "old");
            var otherEntity = loaded(new AuditedEntity(), "other");
            entity.setValue("new");
            listener.afterUpdate(entity);
            otherEntity.setValue("new");
            listener.afterUpdate(otherEntity);
            registry.complete(STATUS_COMMITTED);
            assertEquals(
                List.of("value: old -> new", "value: other -> new", "[value: old -> new, value: other -> new]"), firedEvents(),
                "One AuditedChanges is fired for all entities of the transaction"
            );
        }

        @Test
        void auditedChangesIsNotFiredAfterCommitWithoutChanges() {
            loaded(new AuditedEntity(), "old");
            registry.complete(STATUS_COMMITTED);
            assertEquals(List.of(), firedEvents());
        }

        @Test
        void auditedChangeInActiveTransactionIsNotFiredAfterRollback() {
            updateTwice(loaded(new AuditedEntity(), "old"));
            registry.complete(STATUS_ROLLEDBACK);
            assertEquals(List.of(), firedEvents());
        }

        @ParameterizedTest
        @ValueSource(ints = { STATUS_MARKED_ROLLBACK, STATUS_ROLLING_BACK, STATUS_ROLLEDBACK })
        void auditedChangeInTransactionWhichWillNotCommitIsDropped(int status) {
            var entity = loaded(new AuditedEntity(), "old");
            registry.status = status;
            updateTwice(entity);
            registry.complete(STATUS_COMMITTED);
            assertEquals(List.of(), firedEvents());
            assertEquals(List.of(), warnings);
        }

        @Test
        void auditedChangeIsFiredDuringFlushWithOneWarningWhenNoTransactionSynchronizationRegistry() {
            AuditListener.useRegistryLookup(() -> null);
            assertFiredDuringFlushWithOneWarning(new NoRegistryAuditedEntity(), "no TransactionSynchronizationRegistry");
        }

        @Test
        void auditedChangeIsFiredDuringFlushWithOneWarningWhenNoTransaction() {
            registry.status = STATUS_NO_TRANSACTION;
            assertFiredDuringFlushWithOneWarning(new NoTransactionAuditedEntity(), "no active JTA transaction");
        }

        @Test
        void auditedChangeIsFiredDuringFlushWithOneWarningWhenRegistrationIsRejected() {
            registry.rejecting = true;
            assertFiredDuringFlushWithOneWarning(new RejectedAuditedEntity(), "rejected the registration");
        }

        @Test
        void failedRegistryLookupIsRetriedOnFlushOnly() {
            var lookups = new AtomicInteger();
            var found = new AtomicReference<TransactionSynchronizationRegistry>();
            AuditListener.useRegistryLookup(() -> {
                lookups.incrementAndGet();
                return found.get();
            });
            loaded(new AuditedEntity(), "other");
            var entity = loaded(new AuditedEntity(), "old");
            assertEquals(1, lookups.get(), "Failed lookup is not retried on load");
            found.set(registry);
            entity.setValue("new");
            listener.afterUpdate(entity);
            assertEquals(2, lookups.get(), "Failed lookup is retried on flush");
            registry.complete(STATUS_COMMITTED);
            assertEquals(List.of("value: old -> new", "[value: old -> new]"), firedEvents(), "Change is fired after the commit");
        }

        @Test
        void failingObserverAfterCommitDoesNotSuppressOtherAuditedChanges() {
            var entity = new TwoAuditedFieldsEntity();
            entity.setValue("old");
            entity.setOther("old");
            listener.beforeUpdate(entity);
            entity.setValue("new");
            entity.setOther("new");
            listener.afterUpdate(entity);
            cdi.setFailing(event -> event instanceof AuditedChange change && "other".equals(change.getPropertyName()));
            registry.complete(STATUS_COMMITTED);
            assertEquals(
                List.of("value: old -> new", "[other: old -> new, value: old -> new]"), firedEvents(),
                "Change after the failing one is still fired, and so are all changes"
            );
            assertEquals(1, warnings.size(), "Failure was logged: " + warnings);
            assertTrue(warnings.get(0).contains("property other failed after the commit"), "Warning names the change: " + warnings);
        }

        @Test
        void failingObserverOfAuditedChangesAfterCommitIsLogged() {
            var entity = loaded(new AuditedEntity(), "old");
            entity.setValue("new");
            listener.afterUpdate(entity);
            cdi.setFailing(AuditedChanges.class::isInstance);
            registry.complete(STATUS_COMMITTED);
            assertEquals(List.of("value: old -> new"), firedEvents(), "Change is still fired");
            assertEquals(1, warnings.size(), "Failure was logged: " + warnings);
            assertTrue(warnings.get(0).contains("Observer of AuditedChanges failed after the commit"), "Warning names the event: " + warnings);
        }

        private void assertFiredDuringFlushWithOneWarning(AuditedEntity entity, String reason) {
            updateTwice(loaded(entity, "old"));
            assertEquals(
                List.of("value: old -> first", "[value: old -> first]", "value: first -> second", "[value: first -> second]"), firedEvents(),
                "Each change is fired during the flush, followed by the changes of the flushed entity"
            );
            assertEquals(1, warnings.size(), "Warning is logged once: " + warnings);
            assertTrue(warnings.get(0).contains(entity.getClass().getName()), "Warning names the entity class: " + warnings);
            assertTrue(warnings.get(0).contains(reason), "Warning names the reason: " + warnings);
        }

        private void updateTwice(AuditedEntity entity) {
            entity.setValue("first");
            listener.afterUpdate(entity);
            entity.setValue("second");
            listener.afterUpdate(entity);
        }

        private List<String> firedEvents() {
            return describeEvents(cdi.getFiredEvents());
        }

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

    private static List<String> entityNames(Object entity) {
        return AuditListener.detectChanges(entity).stream().map(AuditedChange::getEntityName).toList();
    }

    private static List<Object> entityIds(Object entity) {
        return AuditListener.detectChanges(entity).stream().map(AuditedChange::getEntityId).toList();
    }

    private static List<String> changes(Object entity) {
        return AuditListener.detectChanges(entity).stream().map(AuditListenerTest::describe).toList();
    }

    private static List<String> changedProperties(Object entity) {
        return AuditListener.detectChanges(entity).stream().map(AuditedChange::getPropertyName).toList();
    }

    private static List<String> describeEvents(List<Object> events) {
        return events.stream().map(AuditListenerTest::describeEvent).toList();
    }

    private static String describeEvent(Object event) {
        return event instanceof AuditedChanges changes
            ? changes.stream().map(AuditListenerTest::describe).toList().toString()
            : describe((AuditedChange) event);
    }

    private static String describe(AuditedChange change) {
        return change.getPropertyName() + ": " + change.getOldValue() + " -> " + change.getNewValue();
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

    @Entity
    public static class NoRegistryAuditedEntity extends AuditedEntity {

        private static final long serialVersionUID = 1L;

    }

    @Entity
    public static class NoTransactionAuditedEntity extends AuditedEntity {

        private static final long serialVersionUID = 1L;

    }

    @Entity
    public static class RejectedAuditedEntity extends AuditedEntity {

        private static final long serialVersionUID = 1L;

    }

    private static class StubRegistry implements TransactionSynchronizationRegistry {

        private final Map<Object, Object> resources = new HashMap<>();
        private final List<Synchronization> synchronizations = new ArrayList<>();
        private int status = STATUS_ACTIVE;
        private boolean rejecting;

        void complete(int completionStatus) {
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(completionStatus));
        }

        @Override
        public Object getTransactionKey() {
            return this;
        }

        @Override
        public void putResource(Object key, Object value) {
            resources.put(key, value);
        }

        @Override
        public Object getResource(Object key) {
            return resources.get(key);
        }

        @Override
        public void registerInterposedSynchronization(Synchronization synchronization) {
            if (rejecting) {
                throw new IllegalStateException("Registration rejected.");
            }

            synchronizations.add(synchronization);
        }

        @Override
        public int getTransactionStatus() {
            return status;
        }

        @Override
        public void setRollbackOnly() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getRollbackOnly() {
            throw new UnsupportedOperationException();
        }

    }

    private static class WarningRecorder extends Handler {

        private final List<String> warnings;

        WarningRecorder(List<String> warnings) {
            this.warnings = warnings;
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == WARNING) {
                warnings.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
            // NOOP.
        }

        @Override
        public void close() {
            // NOOP.
        }

    }

    @Entity
    public static class PlainAuditedEntity {

        @Id
        private Long code;

        @Audit
        private String value;

        public void setCode(Long code) {
            this.code = code;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    @Entity(name = "Memo")
    public static class NamedAuditedEntity {

        @Id
        private Long code;

        @Audit
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    public static class NamedAuditedSubclass extends NamedAuditedEntity {
    }

    @Entity
    public static class PropertyAccessAuditedEntity {

        private String key;

        @Audit
        private String value;

        @Id
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    @Entity
    @IdClass(CompositeId.class)
    public static class CompositeIdAuditedEntity {

        @Id
        private Long first;

        @Id
        private Long second;

        @Audit
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    public record CompositeId(Long first, Long second) implements Serializable {
    }

    @Entity
    public static class EmbeddedIdAuditedEntity {

        @EmbeddedId
        private CompositeId id;

        @Audit
        private String value;

        public void setId(CompositeId id) {
            this.id = id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    @MappedSuperclass
    public abstract static class CodedEntity {

        @Id
        private Long code;

        public void setCode(Long code) {
            this.code = code;
        }

    }

    @Entity
    public static class MappedSuperclassAuditedEntity extends CodedEntity {

        @Audit
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    @Entity
    public static class ValueTypesAuditedEntity extends CodedEntity {

        @Audit
        private Date date;

        @Audit
        private byte[] bytes;

        @Audit
        private BigDecimal amount;

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public void setBytes(byte[] bytes) {
            this.bytes = bytes;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

    }

    @Entity
    public static class TwoAuditedFieldsEntity extends CodedEntity {

        @Audit
        private String value;

        @Audit
        private String other;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getOther() {
            return other;
        }

        public void setOther(String other) {
            this.other = other;
        }

    }

    public enum Status {
        NEW,
        DONE
    }

    @Entity
    public static class TypedAuditedEntity extends CodedEntity {

        @Audit
        private boolean active;

        @Audit
        private Status status;

        @Audit
        private String hidden;

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        void hide(String hidden) {
            this.hidden = hidden;
        }

    }

}
