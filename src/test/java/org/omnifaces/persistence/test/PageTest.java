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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.model.dto.Page;

/**
 * Unit tests for {@link Page}.
 */
public class PageTest {

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
		void nullOffsetDefaultsToZero() {
			var page = new Page(null, 10, null, null, null);
			assertEquals(0, page.getOffset());
		}

		@Test
		void nullLimitDefaultsToMaxValue() {
			var page = new Page(0, null, null, null, null);
			assertEquals(Integer.MAX_VALUE, page.getLimit());
		}

		@Test
		void nullOrderingDefaultsToIdDescending() {
			var page = new Page(0, 10, null, null, null);
			assertEquals(Map.of("id", false), page.getOrdering());
		}

		@Test
		void nullRequiredCriteriaDefaultsToEmptyMap() {
			var page = new Page(0, 10, null, null, null);
			assertTrue(page.getRequiredCriteria().isEmpty());
		}

		@Test
		void nullOptionalCriteriaDefaultsToEmptyMap() {
			var page = new Page(0, 10, null, null, null);
			assertTrue(page.getOptionalCriteria().isEmpty());
		}

		@Test
		void reversedIsFalseWithoutLastEntity() {
			var page = new Page(0, 10, null, null, null, null, null);
			assertFalse(page.isReversed());
		}

		@Test
		void lastEntityDefaultsToNull() {
			var page = Page.of(0, 10);
			assertNull(page.getLast());
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
		void doubleRangeThrowsException() {
			assertThrows(IllegalStateException.class, () -> Page.with().range(0, 10).range(5, 20));
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
			assertFalse(Page.of(0, 10).equals("not a page"));
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
		void orderingIsUnmodifiable() {
			var page = Page.with().orderBy("name", true).build();
			assertThrows(UnsupportedOperationException.class, () -> page.getOrdering().put("new", false));
		}
	}

}
