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

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.Serializations.serializeAndDeserialize;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.model.dto.Page;

/**
 * Unit tests for {@link Page}.
 */
public class PageTest {

    /**
     * Keys which a {@link HashMap} iterates in a different order than they were inserted.
     */
    private static final List<String> CRITERIA_INSERTION_ORDER = List.of("name", "email", "id", "dateOfBirth");

    private static Map<String, Object> criteriaInInsertionOrder() {
        var criteria = new LinkedHashMap<String, Object>();
        CRITERIA_INSERTION_ORDER.forEach(key -> criteria.put(key, Like.contains(key)));
        return criteria;
    }

    @Nested
    class Constants {

        @Test
        void allHasZeroOffsetAndMaxLimit() {
            assertEquals(0, Page.ALL.getOffset());
            assertEquals(Integer.MAX_VALUE, Page.ALL.getLimit());
        }

        @Test
        void oneHasZeroOffsetAndLimitOne() {
            assertEquals(0, Page.ONE.getOffset());
            assertEquals(1, Page.ONE.getLimit());
        }

        @Test
        void constantsHaveDefaultOrdering() {
            assertEquals(Map.of("id", false), Page.ALL.getOrdering());
            assertEquals(Map.of("id", false), Page.ONE.getOrdering());
        }

        @Test
        void constantsHaveEmptyCriteria() {
            assertTrue(Page.ALL.getRequiredCriteria().isEmpty());
            assertTrue(Page.ALL.getOptionalCriteria().isEmpty());
        }

    }

    @Nested
    class FactoryMethod {

        @Test
        void ofCreatesPageWithOffsetAndLimit() {
            var page = Page.of(10, 25);
            assertEquals(10, page.getOffset());
            assertEquals(25, page.getLimit());
        }

        @Test
        void ofUsesDefaultOrdering() {
            assertEquals(Map.of("id", false), Page.of(0, 10).getOrdering());
        }

        @Test
        void ofHasEmptyCriteria() {
            var page = Page.of(0, 10);
            assertTrue(page.getRequiredCriteria().isEmpty());
            assertTrue(page.getOptionalCriteria().isEmpty());
        }

    }

    @Nested
    class Defaults {

        @Test
        void unsetOffsetDefaultsToZero() {
            assertEquals(0, Page.with().build().getOffset());
        }

        @Test
        void unsetLimitDefaultsToMaxValue() {
            assertEquals(Integer.MAX_VALUE, Page.with().build().getLimit());
        }

        @Test
        void unsetOrderingDefaultsToIdDescending() {
            assertEquals(Map.of("id", false), Page.with().build().getOrdering());
        }

        @Test
        void unsetRequiredCriteriaDefaultsToEmptyMap() {
            assertTrue(Page.with().build().getRequiredCriteria().isEmpty());
        }

        @Test
        void unsetOptionalCriteriaDefaultsToEmptyMap() {
            assertTrue(Page.with().build().getOptionalCriteria().isEmpty());
        }

        @Test
        void reversedIsFalseWithoutLastOrderingValues() {
            assertFalse(Page.of(0, 10).isReversed());
        }

        @Test
        void lastOrderingValuesDefaultsToEmptyMap() {
            assertTrue(Page.of(0, 10).getLastOrderingValues().isEmpty());
        }

    }

    @Nested
    class Validation {

        @Test
        void negativeOffsetThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> Page.of(-1, 10));
        }

        @Test
        void zeroLimitThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> Page.of(0, 0));
        }

        @Test
        void negativeLimitThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> Page.of(0, -1));
        }

        @Test
        void zeroOffsetIsValid() {
            var page = Page.of(0, 10);
            assertEquals(0, page.getOffset());
        }

        @Test
        void limitOneIsValid() {
            var page = Page.of(0, 1);
            assertEquals(1, page.getLimit());
        }

    }

    @Nested
    class BuilderTest {

        @Test
        void buildsWithRange() {
            var page = Page.with().range(5, 20).build();
            assertEquals(5, page.getOffset());
            assertEquals(20, page.getLimit());
        }

        @Test
        void buildsWithOrdering() {
            var page = Page.with().range(0, 10).orderBy("name", true).build();
            assertEquals(Map.of("name", true), page.getOrdering());
        }

        @Test
        void buildsWithMultipleOrderings() {
            var page = Page.with().range(0, 10).orderBy("name", true).orderBy("email", false).build();
            var ordering = page.getOrdering();
            assertEquals(2, ordering.size());
            assertTrue(ordering.get("name"));
            assertFalse(ordering.get("email"));
        }

        @Test
        void orderingKeepsInsertionOrder() {
            var page = Page.with().range(0, 10).orderBy("name", true).orderBy("email", false).orderBy("id", true).orderBy("dateOfBirth", false).build();
            var expectedOrder = List.of("name", "email", "id", "dateOfBirth");
            assertEquals(expectedOrder, List.copyOf(page.getOrdering().keySet()));
            assertEquals(expectedOrder, List.copyOf(page.all().getOrdering().keySet()));
        }

        @Test
        void requiredCriteriaKeepsInsertionOrder() {
            var page = Page.with().allMatch(criteriaInInsertionOrder()).build();
            assertEquals(CRITERIA_INSERTION_ORDER, List.copyOf(page.getRequiredCriteria().keySet()));
            assertEquals(CRITERIA_INSERTION_ORDER, List.copyOf(page.all().getRequiredCriteria().keySet()));
        }

        @Test
        void optionalCriteriaKeepsInsertionOrder() {
            var page = Page.with().anyMatch(criteriaInInsertionOrder()).build();
            assertEquals(CRITERIA_INSERTION_ORDER, List.copyOf(page.getOptionalCriteria().keySet()));
            assertEquals(CRITERIA_INSERTION_ORDER, List.copyOf(page.all().getOptionalCriteria().keySet()));
        }

        @Test
        void criteriaWithNullValueIsAccepted() {
            var criteria = new HashMap<String, Object>();
            criteria.put("name", null);
            var page = Page.with().allMatch(criteria).anyMatch(criteria).build();
            assertEquals(singletonMap("name", null), page.getRequiredCriteria());
            assertEquals(singletonMap("name", null), page.getOptionalCriteria());
        }

        @Test
        void buildsWithRequiredCriteria() {
            var criteria = Map.<String, Object>of("name", Like.contains("john"));
            var page = Page.with().range(0, 10).allMatch(criteria).build();
            assertEquals(criteria, page.getRequiredCriteria());
        }

        @Test
        void buildsWithOptionalCriteria() {
            var criteria = Map.<String, Object>of("name", Like.contains("john"));
            var page = Page.with().range(0, 10).anyMatch(criteria).build();
            assertEquals(criteria, page.getOptionalCriteria());
        }

        @Test
        void buildsWithBothCriteria() {
            var required = Map.<String, Object>of("active", true);
            var optional = Map.<String, Object>of("name", Like.contains("john"));
            var page = Page.with().range(0, 10).allMatch(required).anyMatch(optional).build();
            assertEquals(required, page.getRequiredCriteria());
            assertEquals(optional, page.getOptionalCriteria());
        }

        @Test
        void buildsWithOrderByFieldWithoutStandardGetter() {
            var entity = new TestEntity();
            entity.setFieldWithoutStandardGetter("foo");
            var page = Page.with().range(entity, 10, false).orderBy("fieldWithoutStandardGetter", true).build();
            assertEquals(Map.of("fieldWithoutStandardGetter", "foo"), page.getLastOrderingValues());
            assertEquals(10, page.getLimit());
            assertFalse(page.isReversed());
        }

        @Test
        void buildsWithCursorRange() {
            var entity = new TestEntity();
            entity.setId(42L);
            var page = Page.with().range(entity, 10, false).orderBy("id", true).build();
            assertEquals(Map.of("id", 42L), page.getLastOrderingValues());
            assertEquals(10, page.getLimit());
            assertFalse(page.isReversed());
        }

        @Test
        void buildsWithCursorRangeReversed() {
            var entity = new TestEntity();
            entity.setId(42L);
            var page = Page.with().range(entity, 10, true).build();
            assertTrue(page.isReversed());
        }

        @Test
        void cursorRangeExtractsValuesOfAllOrderingFieldsInOrder() {
            var entity = new TestEntity();
            entity.setId(42L);
            entity.setName("name");
            var page = Page.with().range(entity, 10, false).orderBy("name", true).orderBy("id", false).build();
            assertEquals(List.of("name", "id"), List.copyOf(page.getLastOrderingValues().keySet()));
            assertEquals(Map.of("name", "name", "id", 42L), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeExtractsValuesOfDefaultOrdering() {
            var entity = new TestEntity();
            entity.setId(42L);
            var page = Page.with().range(entity, 10, false).build();
            assertEquals(Map.of("id", 42L), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeExtractsNullValue() {
            var entity = new TestEntity();
            entity.setId(42L);
            var page = Page.with().range(entity, 10, false).orderBy("name", true).build();
            assertEquals(singletonMap("name", null), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeExtractsValueOfNestedPath() {
            var parent = new TestEntity();
            parent.setName("parent");
            var entity = new TestEntity();
            entity.setId(42L);
            entity.setParent(parent);
            var page = Page.with().range(entity, 10, false).orderBy("parent.name", true).build();
            assertEquals(Map.of("parent.name", "parent"), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeReadsOrderingValueOfLazyProxyViaItsGetter() {
            var parent = new TestEntity();
            parent.setName("parent");
            var entity = new TestEntity();
            entity.setId(42L);
            entity.setParent(new LazyTestEntityProxy(parent));
            var page = Page.with().range(entity, 10, false).orderBy("parent.name", true).build();
            assertEquals(Map.of("parent.name", "parent"), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeReadsOrderingValueOfPropertyWithoutField() {
            var entity = new TestEntity();
            entity.setId(42L);
            entity.setName("name");
            var page = Page.with().range(entity, 10, false).orderBy("upperCaseName", true).build();
            assertEquals(Map.of("upperCaseName", "NAME"), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeExtractsNullValueOfNestedPathWithNullIntermediate() {
            var entity = new TestEntity();
            entity.setId(42L);
            var page = Page.with().range(entity, 10, false).orderBy("parent.name", true).build();
            assertEquals(singletonMap("parent.name", null), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeExtractsNullValueOfDeeplyNestedPathWithNullIntermediate() {
            var entity = new TestEntity();
            entity.setId(42L);
            entity.setParent(new TestEntity());
            var page = Page.with().range(entity, 10, false).orderBy("parent.parent.name", true).build();
            assertEquals(singletonMap("parent.parent.name", null), page.getLastOrderingValues());
        }

        @Test
        void cursorRangeWithNestedPathWithoutGetterThrowsException() {
            var entity = new TestEntity();
            entity.setId(42L);
            entity.setParent(new TestEntity());
            var builder = Page.with().range(entity, 10, false).orderBy("parent.nonExistent", true);
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        void cursorRangeWithOrderingFieldWithoutGetterThrowsException() {
            var entity = new TestEntity();
            entity.setId(42L);
            var builder = Page.with().range(entity, 10, false).orderBy("nonExistent", true);
            assertThrows(IllegalStateException.class, builder::build);
        }

        @Test
        void buildsWithOrderingValuesRange() {
            var page = Page.with().range(20, 10, Map.of("id", 42L), true).orderBy("id", true).build();
            assertEquals(20, page.getOffset());
            assertEquals(10, page.getLimit());
            assertEquals(Map.of("id", 42L), page.getLastOrderingValues());
            assertTrue(page.isReversed());
        }

        @Test
        void orderingValuesRangeAcceptsNullValue() {
            var page = Page.with().range(0, 10, singletonMap("name", null), false).orderBy("name", true).build();
            assertEquals(singletonMap("name", null), page.getLastOrderingValues());
        }

        @Test
        void orderingValuesRangeFollowsOrderOfOrdering() {
            var lastOrderingValues = new LinkedHashMap<String, Object>();
            lastOrderingValues.put("id", 42L);
            lastOrderingValues.put("name", "name");
            var page = Page.with().range(0, 10, lastOrderingValues, false).orderBy("name", true).orderBy("id", true).build();
            assertEquals(List.of("name", "id"), List.copyOf(page.getLastOrderingValues().keySet()));
        }

        @Test
        void orderingValuesRangeMatchesDefaultOrdering() {
            var page = Page.with().range(0, 10, Map.of("id", 42L), false).build();
            assertEquals(Map.of("id", 42L), page.getLastOrderingValues());
        }

        @Test
        void orderingValuesRangeWithMissingKeyThrowsException() {
            var builder = Page.with().range(0, 10, Map.of("name", "name"), false).orderBy("name", true).orderBy("id", true);
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void orderingValuesRangeWithExtraKeyThrowsException() {
            var builder = Page.with().range(0, 10, Map.of("name", "name", "id", 42L), false).orderBy("name", true);
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void orderingValuesRangeWithEmptyMapThrowsException() {
            var builder = Page.with().range(0, 10, Map.of(), false);
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void orderingValuesRangeWithNullThrowsException() {
            assertThrows(NullPointerException.class, () -> Page.with().range(0, 10, null, false));
        }

        @Test
        void orderingValuesRangeAfterOffsetRangeThrowsException() {
            assertThrows(IllegalStateException.class, () -> Page.with().range(0, 10).range(0, 10, Map.of("id", 1L), false));
        }

        @Test
        void orderingValuesRangeAfterCursorRangeThrowsException() {
            var entity = new TestEntity();
            entity.setId(1L);
            assertThrows(IllegalStateException.class, () -> Page.with().range(entity, 10, false).range(0, 10, Map.of("id", 1L), false));
        }

        @Test
        void doubleRangeThrowsException() {
            assertThrows(IllegalStateException.class, () -> Page.with().range(0, 10).range(5, 20));
        }

        @Test
        void cursorRangeAfterOffsetRangeThrowsException() {
            var entity = new TestEntity();
            entity.setId(1L);
            assertThrows(IllegalStateException.class, () -> Page.with().range(0, 10).range(entity, 10, false));
        }

        @Test
        void offsetRangeAfterCursorRangeThrowsException() {
            var entity = new TestEntity();
            entity.setId(1L);
            assertThrows(IllegalStateException.class, () -> Page.with().range(entity, 10, false).range(0, 10));
        }

        @Test
        void doubleCursorRangeThrowsException() {
            var entity = new TestEntity();
            entity.setId(1L);
            assertThrows(IllegalStateException.class, () -> Page.with().range(entity, 10, false).range(entity, 10, false));
        }

        @Test
        void cursorRangeWithNullLastThrowsException() {
            assertThrows(NullPointerException.class, () -> Page.with().range(null, 10, false));
        }

        @Test
        void orderByWithNullFieldThrowsException() {
            assertThrows(NullPointerException.class, () -> Page.with().orderBy(null, true));
        }

        @Test
        void requiredCriteriaWithNullThrowsException() {
            assertThrows(NullPointerException.class, () -> Page.with().allMatch(null));
        }

        @Test
        void optionalCriteriaWithNullThrowsException() {
            assertThrows(NullPointerException.class, () -> Page.with().anyMatch(null));
        }

        @Test
        void doubleRequiredCriteriaThrowsException() {
            var criteria = Map.<String, Object>of("name", "test");
            assertThrows(IllegalStateException.class, () -> Page.with().allMatch(criteria).allMatch(criteria));
        }

        @Test
        void doubleOptionalCriteriaThrowsException() {
            var criteria = Map.<String, Object>of("name", "test");
            assertThrows(IllegalStateException.class, () -> Page.with().anyMatch(criteria).anyMatch(criteria));
        }

        @Test
        void buildWithoutRangeUsesDefaults() {
            var page = Page.with().build();
            assertEquals(0, page.getOffset());
            assertEquals(Integer.MAX_VALUE, page.getLimit());
        }

    }

    @Nested
    class AllMethod {

        @Test
        void allPreservesOrderingAndCriteria() {
            var required = Map.<String, Object>of("active", true);
            var page = Page.with().range(5, 20).orderBy("name", true).allMatch(required).build();
            var all = page.all();
            assertEquals(0, all.getOffset());
            assertEquals(Integer.MAX_VALUE, all.getLimit());
            assertEquals(page.getOrdering(), all.getOrdering());
            assertEquals(page.getRequiredCriteria(), all.getRequiredCriteria());
        }

        @Test
        void allDropsLastOrderingValuesAndReversed() {
            var all = Page.with().range(0, 10, Map.of("id", 1L), true).build().all();
            assertTrue(all.getLastOrderingValues().isEmpty());
            assertFalse(all.isReversed());
        }

    }

    @Nested
    class Identity {

        @Test
        void equalPages() {
            var page1 = Page.of(0, 10);
            var page2 = Page.of(0, 10);
            assertEquals(page1, page2);
            assertEquals(page1.hashCode(), page2.hashCode());
        }

        @Test
        void differentOffsetNotEqual() {
            assertNotEquals(Page.of(0, 10), Page.of(5, 10));
        }

        @Test
        void differentLimitNotEqual() {
            assertNotEquals(Page.of(0, 10), Page.of(0, 20));
        }

        @Test
        void cursorRangesWithSameOrderingValuesAreEqual() {
            var entity1 = new TestEntity();
            entity1.setId(1L);
            entity1.setName("same");
            var entity2 = new TestEntity();
            entity2.setId(2L);
            entity2.setName("same");
            var page1 = Page.with().range(entity1, 10, false).orderBy("name", true).build();
            var page2 = Page.with().range(entity2, 10, false).orderBy("name", true).build();
            assertEquals(page1, page2);
            assertEquals(page1.hashCode(), page2.hashCode());
        }

        @Test
        void sameInstanceIsEqual() {
            var page = Page.of(0, 10);
            assertEquals(page, page);
        }

        @Test
        void notEqualToNull() {
            assertNotEquals(null, Page.of(0, 10));
        }

        @SuppressWarnings("unlikely-arg-type")
        @Test
        void notEqualToDifferentType() {
            assertFalse("not a page".equals(Page.of(0, 10)));
        }

        @Test
        void toStringContainsOffsetAndLimit() {
            var str = Page.of(5, 20).toString();
            assertTrue(str.contains("5"));
            assertTrue(str.contains("20"));
        }

    }

    @Nested
    class Immutability {

        @Test
        void requiredCriteriaIsUnmodifiable() {
            var criteria = Map.<String, Object>of("name", "test");
            var page = Page.with().allMatch(criteria).build();
            assertThrows(UnsupportedOperationException.class, () -> page.getRequiredCriteria().put("new", "value"));
        }

        @Test
        void optionalCriteriaIsUnmodifiable() {
            var criteria = Map.<String, Object>of("name", "test");
            var page = Page.with().anyMatch(criteria).build();
            assertThrows(UnsupportedOperationException.class, () -> page.getOptionalCriteria().put("new", "value"));
        }

        @Test
        void lastOrderingValuesIsUnmodifiable() {
            var page = Page.with().range(0, 10, Map.of("id", 1L), false).build();
            assertThrows(UnsupportedOperationException.class, () -> page.getLastOrderingValues().put("new", "value"));
        }

        @Test
        void lastOrderingValuesIsNotAffectedByLaterChangesOfGivenMap() {
            var lastOrderingValues = new HashMap<String, Object>(Map.of("id", 1L));
            var page = Page.with().range(0, 10, lastOrderingValues, false).build();
            lastOrderingValues.put("id", 2L);
            assertEquals(Map.of("id", 1L), page.getLastOrderingValues());
        }

        @Test
        void lastOrderingValuesIsNotAffectedByLaterChangesOfLastEntity() {
            var entity = new TestEntity();
            entity.setId(1L);
            var page = Page.with().range(entity, 10, false).build();
            entity.setId(2L);
            assertEquals(Map.of("id", 1L), page.getLastOrderingValues());
        }

        @Test
        void orderingIsNotAffectedByLaterChangesOfBuilder() {
            var builder = Page.with().range(0, 10, Map.of("id", 1L), false).orderBy("id", true);
            var page = builder.build();
            builder.orderBy("name", true);
            assertEquals(Map.of("id", true), page.getOrdering());
        }

        @Test
        void requiredCriteriaIsNotAffectedByLaterChangesOfGivenMap() {
            var criteria = new HashMap<String, Object>(Map.of("name", "test"));
            var page = Page.with().allMatch(criteria).build();
            criteria.put("name", "changed");
            criteria.put("new", "value");
            assertEquals(Map.of("name", "test"), page.getRequiredCriteria());
            assertEquals(Map.of("name", "test"), page.all().getRequiredCriteria());
        }

        @Test
        void optionalCriteriaIsNotAffectedByLaterChangesOfGivenMap() {
            var criteria = new HashMap<String, Object>(Map.of("name", "test"));
            var page = Page.with().anyMatch(criteria).build();
            criteria.put("name", "changed");
            criteria.put("new", "value");
            assertEquals(Map.of("name", "test"), page.getOptionalCriteria());
            assertEquals(Map.of("name", "test"), page.all().getOptionalCriteria());
        }

        @Test
        void orderingIsUnmodifiable() {
            var page = Page.with().orderBy("name", true).build();
            assertThrows(UnsupportedOperationException.class, () -> page.getOrdering().put("new", false));
        }

    }

    @Nested
    class Serialization {

        @Test
        void pageWithCriteriaAndLastEntitySurvivesSerialization() throws Exception {
            var last = new TestEntity();
            last.setId(42L);
            var page = Page.with()
                .range(last, 10, false)
                .orderBy("name", true)
                .allMatch(Map.of("name", Like.contains("test")))
                .anyMatch(Map.of("email", Like.endsWith("@example.com")))
                .build();

            assertEquals(page, serializeAndDeserialize(page));
        }

        @Test
        void pageWithCursorRangeDoesNotSerializeLastEntity() throws Exception {
            var last = new EntityWithUnserializableState();
            last.setId(42L);
            var page = Page.with().range(last, 10, false).orderBy("id", true).build();

            assertEquals(page, serializeAndDeserialize(page));
        }

        @Test
        void constantsSurviveSerialization() throws Exception {
            assertEquals(Page.ALL, serializeAndDeserialize(Page.ALL));
            assertEquals(Page.ONE, serializeAndDeserialize(Page.ONE));
        }

    }

    static class TestEntity extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

        private String name;
        private TestEntity parent;
        private String fieldWithoutStandardGetter;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TestEntity getParent() {
            return parent;
        }

        public void setParent(TestEntity parent) {
            this.parent = parent;
        }

        public String fieldWithoutStandardGetter() {
            return fieldWithoutStandardGetter;
        }

        public void setFieldWithoutStandardGetter(String fieldWithoutStandardGetter) {
            this.fieldWithoutStandardGetter = fieldWithoutStandardGetter;
        }

        public String getUpperCaseName() {
            return name == null ? null : name.toUpperCase();
        }
    }

    /**
     * Mimics a lazy loading proxy: a subclass whose own fields stay empty, and whose getters delegate to the loaded entity.
     */
    static class LazyTestEntityProxy extends TestEntity {

        private static final long serialVersionUID = 1L;

        private final TestEntity target;

        LazyTestEntityProxy(TestEntity target) {
            this.target = target;
        }

        @Override
        public String getName() {
            return target.getName();
        }

    }

    static class EntityWithUnserializableState extends GeneratedIdEntity<Long> {

        private static final long serialVersionUID = 1L;

        @SuppressWarnings("unused")
        private final Object unserializableState = new Object();

    }

}
