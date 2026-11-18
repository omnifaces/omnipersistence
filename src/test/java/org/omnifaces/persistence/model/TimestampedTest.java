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
package org.omnifaces.persistence.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for the {@link Timestamped} base classes, invoking their callbacks the way the persistence provider does. The integration tests can use the base
 * classes with their own ID only, because OpenJPA cannot map the generated ID of {@link GeneratedIdEntity} in a JAR.
 */
class TimestampedTest {

    private static final Instant PAST = Instant.parse("2000-01-01T00:00:00Z");

    static Stream<Supplier<Timestamped>> timestampedEntities() {
        return Stream.of(TimestampedBase::new, TimestampedGeneratedId::new, VersionedBase::new, VersionedGeneratedId::new);
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void persistSetsCreatedAndLastModifiedToSameTimestampOverridingPresetCreated(Supplier<Timestamped> constructor) {
        var entity = constructor.get();
        entity.setCreated(PAST);
        prePersist(entity);
        assertNotNull(entity.getCreated(), "Created");
        assertNotEquals(PAST, entity.getCreated(), "Preset created is overridden");
        assertEquals(entity.getCreated(), entity.getLastModified(), "Last modified equals created");
    }

    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void updateAdvancesLastModifiedAndKeepsCreated(Supplier<Timestamped> constructor) {
        var entity = constructor.get();
        entity.setCreated(PAST);
        entity.setLastModified(PAST);
        preUpdate(entity);
        assertEquals(PAST, entity.getCreated(), "Created");
        assertTrue(entity.getLastModified().isAfter(PAST), "Last modified advances");
    }

    /**
     * {@code skipAdjustLastModified()} holds for every later update of the same instance.
     */
    @ParameterizedTest
    @MethodSource("timestampedEntities")
    void skipAdjustLastModifiedHoldsForEveryLaterUpdateOfSameInstance(Supplier<Timestamped> constructor) {
        var entity = constructor.get();
        entity.setLastModified(PAST);
        skipAdjustLastModified(entity);
        preUpdate(entity);
        assertEquals(PAST, entity.getLastModified(), "Last modified after first update");
        preUpdate(entity);
        assertEquals(PAST, entity.getLastModified(), "Last modified after second update");
    }

    private static void prePersist(Timestamped entity) {
        invoke(entity, TimestampedBaseEntity::onPrePersist, TimestampedEntity::onPrePersist);
    }

    private static void preUpdate(Timestamped entity) {
        invoke(entity, TimestampedBaseEntity::onPreUpdate, TimestampedEntity::onPreUpdate);
    }

    private static void skipAdjustLastModified(Timestamped entity) {
        invoke(entity, TimestampedBaseEntity::skipAdjustLastModified, TimestampedEntity::skipAdjustLastModified);
    }

    /**
     * The base classes with their own ID and with a generated ID share no supertype which declares the callbacks.
     */
    private static void invoke(Timestamped entity, Consumer<TimestampedBaseEntity<?>> withOwnId, Consumer<TimestampedEntity<?>> withGeneratedId) {
        if (entity instanceof TimestampedBaseEntity<?> base) {
            withOwnId.accept(base);
        }
        else {
            withGeneratedId.accept((TimestampedEntity<?>) entity);
        }
    }

    static class TimestampedBase extends TimestampedBaseEntity<Long> {

        private static final long serialVersionUID = 1L;

        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

    }

    static class TimestampedGeneratedId extends TimestampedEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

    static class VersionedBase extends VersionedBaseEntity<Long> {

        private static final long serialVersionUID = 1L;

        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

    }

    static class VersionedGeneratedId extends VersionedEntity<Long> {

        private static final long serialVersionUID = 1L;

    }

}
