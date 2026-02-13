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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.criteria.Between;
import org.omnifaces.persistence.criteria.Bool;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.Enumerated;
import org.omnifaces.persistence.criteria.IgnoreCase;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.criteria.Numeric;
import org.omnifaces.persistence.criteria.Order;

/**
 * Unit tests for all {@link Criteria} subclasses.
 * <p>
 * Tests focus on the {@code applies()} method (in-memory evaluation), factory methods, type checking,
 * parsing, and the identity methods ({@code equals()}, {@code hashCode()}, {@code toString()}).
 * <p>
 * The {@code build()} method is not tested here as it requires a JPA CriteriaBuilder and is covered by integration tests.
 */
public class CriteriaTest {

	// ----------------------------------------------------------------------------------------------------------------
	// Criteria (base class)
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class CriteriaBase {

		@Test
		void unwrapReturnsCriteriaValue() {
			var numeric = Numeric.value(42);
			assertEquals(42, Criteria.unwrap(numeric));
		}

		@Test
		void unwrapReturnsPlainValueUnmodified() {
			assertEquals("hello", Criteria.unwrap("hello"));
			assertEquals(42, Criteria.unwrap(42));
		}

		@Test
		void unwrapReturnsNullForNull() {
			assertEquals(null, Criteria.unwrap(null));
		}

		@Test
		void unwrapNestedCriteria() {
			var inner = Like.contains("test");
			var outer = Not.value(inner);
			// Not wraps Like which wraps "test"; unwrap recursively unwraps all Criteria layers.
			assertEquals("test", Criteria.unwrap(outer));
		}

		@Test
		void nullValueThrowsNullPointerException() {
			assertThrows(NullPointerException.class, () -> Numeric.value(null));
			assertThrows(NullPointerException.class, () -> Like.contains(null));
			assertThrows(NullPointerException.class, () -> Bool.value(null));
			assertThrows(NullPointerException.class, () -> IgnoreCase.value(null));
		}

		@Test
		void criteriaCannotNestSameType() {
			assertThrows(IllegalArgumentException.class, () -> Not.value(Not.value("test")));
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Like
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class LikeTest {

		@Test
		void containsMatchesSubstring() {
			var like = Like.contains("john");
			assertTrue(like.applies("John Doe"));
			assertTrue(like.applies("johnny"));
			assertTrue(like.applies("JOHN"));
			assertFalse(like.applies("Jane Doe"));
		}

		@Test
		void containsIsCaseInsensitive() {
			var like = Like.contains("TEST");
			assertTrue(like.applies("this is a test"));
			assertTrue(like.applies("Testing"));
			assertTrue(like.applies("TEST"));
		}

		@Test
		void startsWithMatchesPrefix() {
			var like = Like.startsWith("john");
			assertTrue(like.applies("John Doe"));
			assertTrue(like.applies("johnny"));
			assertFalse(like.applies("Big John"));
		}

		@Test
		void endsWithMatchesSuffix() {
			var like = Like.endsWith("doe");
			assertTrue(like.applies("John Doe"));
			assertTrue(like.applies("Jane DOE"));
			assertFalse(like.applies("Doe Smith"));
		}

		@Test
		void appliesReturnsFalseForNull() {
			assertFalse(Like.contains("test").applies(null));
			assertFalse(Like.startsWith("test").applies(null));
			assertFalse(Like.endsWith("test").applies(null));
		}

		@Test
		void appliesWorksWithEnumValues() {
			var like = Like.contains("mob");
			assertTrue(like.applies(TestEnum.MOBILE));
			assertFalse(like.applies(TestEnum.HOME));
		}

		@Test
		void emptyStringMatchesEverything() {
			var like = Like.contains("");
			assertTrue(like.applies("anything"));
			assertTrue(like.applies(""));
		}

		@Test
		void typeCheckMethods() {
			assertTrue(Like.startsWith("x").startsWith());
			assertFalse(Like.startsWith("x").endsWith());
			assertFalse(Like.startsWith("x").contains());

			assertFalse(Like.endsWith("x").startsWith());
			assertTrue(Like.endsWith("x").endsWith());
			assertFalse(Like.endsWith("x").contains());

			assertFalse(Like.contains("x").startsWith());
			assertFalse(Like.contains("x").endsWith());
			assertTrue(Like.contains("x").contains());
		}

		@Test
		void equalsSameTypeAndValue() {
			assertEquals(Like.contains("test"), Like.contains("test"));
			assertNotEquals(Like.contains("test"), Like.startsWith("test"));
			assertNotEquals(Like.contains("test"), Like.contains("other"));
		}

		@Test
		void hashCodeSameForEqualInstances() {
			assertEquals(Like.contains("test").hashCode(), Like.contains("test").hashCode());
			assertNotEquals(Like.contains("test").hashCode(), Like.startsWith("test").hashCode());
		}

		@Test
		void toStringIncludesWildcards() {
			assertEquals("LIKE %test%", Like.contains("test").toString());
			assertEquals("LIKE test%", Like.startsWith("test").toString());
			assertEquals("LIKE %test", Like.endsWith("test").toString());
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Order
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class OrderTest {

		@Test
		void greaterThanApplies() {
			var order = Order.greaterThan(18);
			assertTrue(order.applies(19));
			assertTrue(order.applies(100));
			assertFalse(order.applies(18));
			assertFalse(order.applies(17));
		}

		@Test
		void greaterThanOrEqualToApplies() {
			var order = Order.greaterThanOrEqualTo(18);
			assertTrue(order.applies(18));
			assertTrue(order.applies(19));
			assertFalse(order.applies(17));
		}

		@Test
		void lessThanApplies() {
			var order = Order.lessThan(18);
			assertTrue(order.applies(17));
			assertTrue(order.applies(0));
			assertFalse(order.applies(18));
			assertFalse(order.applies(19));
		}

		@Test
		void lessThanOrEqualToApplies() {
			var order = Order.lessThanOrEqualTo(18);
			assertTrue(order.applies(18));
			assertTrue(order.applies(17));
			assertFalse(order.applies(19));
		}

		@Test
		void appliesReturnsFalseForNonComparable() {
			assertFalse(Order.greaterThan(18).applies(new Object()));
		}

		@Test
		void worksWithDates() {
			var cutoff = LocalDate.of(2025, 1, 1);
			var order = Order.lessThan(cutoff);
			assertTrue(order.applies(LocalDate.of(2024, 12, 31)));
			assertFalse(order.applies(LocalDate.of(2025, 1, 1)));
			assertFalse(order.applies(LocalDate.of(2025, 6, 1)));
		}

		@Test
		void worksWithStrings() {
			var order = Order.greaterThanOrEqualTo("M");
			assertTrue(order.applies("Z"));
			assertTrue(order.applies("M"));
			assertFalse(order.applies("A"));
		}

		@Test
		void typeCheckMethods() {
			assertTrue(Order.lessThan(1).lessThan());
			assertFalse(Order.lessThan(1).lessThanOrEqualTo());
			assertFalse(Order.lessThan(1).greaterThan());
			assertFalse(Order.lessThan(1).greaterThanOrEqualTo());

			assertTrue(Order.greaterThan(1).greaterThan());
			assertFalse(Order.greaterThan(1).greaterThanOrEqualTo());

			assertTrue(Order.lessThanOrEqualTo(1).lessThanOrEqualTo());
			assertTrue(Order.greaterThanOrEqualTo(1).greaterThanOrEqualTo());
		}

		@Test
		void equalsSameTypeAndValue() {
			assertEquals(Order.greaterThan(18), Order.greaterThan(18));
			assertNotEquals(Order.greaterThan(18), Order.greaterThanOrEqualTo(18));
			assertNotEquals(Order.greaterThan(18), Order.greaterThan(21));
		}

		@Test
		void hashCodeSameForEqualInstances() {
			assertEquals(Order.lessThan(5).hashCode(), Order.lessThan(5).hashCode());
			assertNotEquals(Order.lessThan(5).hashCode(), Order.lessThanOrEqualTo(5).hashCode());
		}

		@Test
		void toStringFormat() {
			assertEquals("> 18", Order.greaterThan(18).toString());
			assertEquals(">= 18", Order.greaterThanOrEqualTo(18).toString());
			assertEquals("< 18", Order.lessThan(18).toString());
			assertEquals("<= 18", Order.lessThanOrEqualTo(18).toString());
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Between
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class BetweenTest {

		@Test
		void appliesWithinRange() {
			var between = Between.range(10, 20);
			assertTrue(between.applies(10));
			assertTrue(between.applies(15));
			assertTrue(between.applies(20));
			assertFalse(between.applies(9));
			assertFalse(between.applies(21));
		}

		@Test
		void appliesWithDates() {
			var start = LocalDate.of(2025, 1, 1);
			var end = LocalDate.of(2025, 12, 31);
			var between = Between.range(start, end);
			assertTrue(between.applies(LocalDate.of(2025, 6, 15)));
			assertTrue(between.applies(start));
			assertTrue(between.applies(end));
			assertFalse(between.applies(LocalDate.of(2024, 12, 31)));
			assertFalse(between.applies(LocalDate.of(2026, 1, 1)));
		}

		@Test
		void appliesWithStrings() {
			var between = Between.range("B", "D");
			assertTrue(between.applies("B"));
			assertTrue(between.applies("C"));
			assertTrue(between.applies("D"));
			assertFalse(between.applies("A"));
			assertFalse(between.applies("E"));
		}

		@Test
		void appliesReturnsFalseForNull() {
			assertFalse(Between.range(1, 10).applies(null));
		}

		@Test
		void appliesReturnsFalseForNonComparable() {
			assertFalse(Between.range(1, 10).applies(new Object()));
		}

		@Test
		void toStringFormat() {
			assertEquals("BETWEEN 10 AND 20", Between.range(10, 20).toString());
		}

		@Test
		void getValueReturnsRange() {
			var between = Between.range(5, 15);
			assertEquals(5, between.getValue().getMin());
			assertEquals(15, between.getValue().getMax());
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Bool
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class BoolTest {

		@Test
		void trueAppliesToTrue() {
			var bool = Bool.value(true);
			assertTrue(bool.applies(true));
			assertFalse(bool.applies(false));
		}

		@Test
		void falseAppliesToFalse() {
			var bool = Bool.value(false);
			assertTrue(bool.applies(false));
			assertFalse(bool.applies(true));
		}

		@Test
		void trueAppliesToTruthyValues() {
			var bool = Bool.value(true);
			assertTrue(bool.applies(1));
			assertTrue(bool.applies(42));
			assertTrue(bool.applies("true"));
			assertTrue(bool.applies("1"));
		}

		@Test
		void falseAppliesToFalsyValues() {
			var bool = Bool.value(false);
			assertTrue(bool.applies(0));
			assertTrue(bool.applies(-1));
			assertTrue(bool.applies("false"));
			assertTrue(bool.applies("0"));
			assertTrue(bool.applies(null));
		}

		@Test
		void parseFromTruthyStrings() {
			assertTrue(Bool.parse("true").getValue());
			assertTrue(Bool.parse("1").getValue());
			assertTrue(Bool.parse("42").getValue());
		}

		@Test
		void parseFromFalsyStrings() {
			assertFalse(Bool.parse("false").getValue());
			assertFalse(Bool.parse("0").getValue());
			assertFalse(Bool.parse("abc").getValue());
			assertFalse(Bool.parse(null).getValue());
		}

		@Test
		void parseFromNumbers() {
			assertTrue(Bool.parse(1).getValue());
			assertTrue(Bool.parse(100L).getValue());
			assertFalse(Bool.parse(0).getValue());
			assertFalse(Bool.parse(-5).getValue());
		}

		@Test
		void isTruthyWithVariousTypes() {
			assertTrue(Bool.isTruthy(true));
			assertTrue(Bool.isTruthy(1));
			assertTrue(Bool.isTruthy(0.5));
			assertTrue(Bool.isTruthy("1"));
			assertTrue(Bool.isTruthy("true"));
			assertTrue(Bool.isTruthy("99"));
			assertFalse(Bool.isTruthy(false));
			assertFalse(Bool.isTruthy(0));
			assertFalse(Bool.isTruthy(-1));
			assertFalse(Bool.isTruthy("0"));
			assertFalse(Bool.isTruthy("false"));
			assertFalse(Bool.isTruthy(null));
		}

		@Test
		void isChecksType() {
			assertTrue(Bool.is(boolean.class));
			assertTrue(Bool.is(Boolean.class));
			assertFalse(Bool.is(String.class));
			assertFalse(Bool.is(int.class));
		}

		@Test
		void equalInstances() {
			assertEquals(Bool.value(true), Bool.value(true));
			assertEquals(Bool.value(false), Bool.value(false));
			assertNotEquals(Bool.value(true), Bool.value(false));
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Numeric
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class NumericTest {

		@Test
		void appliesWithSameValue() {
			assertTrue(Numeric.value(42).applies(42));
			assertTrue(Numeric.value(42L).applies(42L));
		}

		@Test
		void appliesReturnsFalseForDifferentValue() {
			assertFalse(Numeric.value(42).applies(43));
		}

		@Test
		void appliesReturnsFalseForNull() {
			assertFalse(Numeric.value(42).applies(null));
		}

		@Test
		void appliesReturnsTrueForNumberPassedDirectly() {
			assertTrue(Numeric.value(42).applies(42));
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseInteger() {
			var numeric = Numeric.parse("42", (Class<Number>) (Class<?>) Integer.class);
			assertEquals(42, numeric.getValue());
			assertTrue(numeric.getValue() instanceof Integer);
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseLong() {
			var numeric = Numeric.parse("42", (Class<Number>) (Class<?>) Long.class);
			assertEquals(42L, numeric.getValue());
			assertTrue(numeric.getValue() instanceof Long);
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseBigDecimal() {
			var numeric = Numeric.parse("42.5", (Class<Number>) (Class<?>) BigDecimal.class);
			assertEquals(new BigDecimal("42.5"), numeric.getValue());
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseBigInteger() {
			var numeric = Numeric.parse("12345678901234567890", (Class<Number>) (Class<?>) BigInteger.class);
			assertEquals(new BigInteger("12345678901234567890"), numeric.getValue());
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseInvalidThrowsException() {
			assertThrows(IllegalArgumentException.class, () -> Numeric.parse("abc", (Class<Number>) (Class<?>) Integer.class));
		}

		@Test
		void parseReturnsNumberDirectlyWhenAlreadyNumber() {
			var numeric = Numeric.parse(42, Number.class);
			assertEquals(42, numeric.getValue());
		}

		@Test
		void isChecksNumericTypes() {
			assertTrue(Numeric.is(int.class));
			assertTrue(Numeric.is(long.class));
			assertTrue(Numeric.is(byte.class));
			assertTrue(Numeric.is(short.class));
			assertTrue(Numeric.is(float.class));
			assertTrue(Numeric.is(double.class));
			assertTrue(Numeric.is(Integer.class));
			assertTrue(Numeric.is(Long.class));
			assertTrue(Numeric.is(BigDecimal.class));
			assertTrue(Numeric.is(BigInteger.class));
			assertFalse(Numeric.is(String.class));
			assertFalse(Numeric.is(Boolean.class));
		}

		@Test
		void equalInstances() {
			assertEquals(Numeric.value(42), Numeric.value(42));
			assertNotEquals(Numeric.value(42), Numeric.value(43));
		}

		@Test
		void toStringFormat() {
			assertEquals("NUMERIC(42)", Numeric.value(42).toString());
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Enumerated
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class EnumeratedTest {

		@Test
		void appliesWithSameEnum() {
			assertTrue(Enumerated.value(TestEnum.HOME).applies(TestEnum.HOME));
		}

		@Test
		void appliesReturnsFalseForDifferentEnum() {
			assertFalse(Enumerated.value(TestEnum.HOME).applies(TestEnum.MOBILE));
		}

		@Test
		void appliesReturnsFalseForNull() {
			assertFalse(Enumerated.value(TestEnum.HOME).applies(null));
		}

		@Test
		void appliesWithStringMatchesCaseInsensitive() {
			assertTrue(Enumerated.value(TestEnum.HOME).applies("home"));
			assertTrue(Enumerated.value(TestEnum.HOME).applies("HOME"));
			assertTrue(Enumerated.value(TestEnum.HOME).applies("Home"));
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseCaseInsensitive() {
			var enumerated = Enumerated.parse("mobile", (Class<Enum<?>>) (Class<?>) TestEnum.class);
			assertEquals(TestEnum.MOBILE, enumerated.getValue());
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseMixedCase() {
			var enumerated = Enumerated.parse("HoMe", (Class<Enum<?>>) (Class<?>) TestEnum.class);
			assertEquals(TestEnum.HOME, enumerated.getValue());
		}

		@SuppressWarnings("unchecked")
		@Test
		void parseInvalidThrowsException() {
			assertThrows(IllegalArgumentException.class, () -> Enumerated.parse("INVALID", (Class<Enum<?>>) (Class<?>) TestEnum.class));
		}

		@Test
		void parseReturnsEnumDirectlyWhenAlreadyEnum() {
			var enumerated = Enumerated.parse(TestEnum.WORK, null);
			assertEquals(TestEnum.WORK, enumerated.getValue());
		}

		@Test
		void equalInstances() {
			assertEquals(Enumerated.value(TestEnum.HOME), Enumerated.value(TestEnum.HOME));
			assertNotEquals(Enumerated.value(TestEnum.HOME), Enumerated.value(TestEnum.WORK));
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// IgnoreCase
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class IgnoreCaseTest {

		@Test
		void appliesCaseInsensitiveMatch() {
			var criteria = IgnoreCase.value("John");
			assertTrue(criteria.applies("john"));
			assertTrue(criteria.applies("JOHN"));
			assertTrue(criteria.applies("John"));
			assertTrue(criteria.applies("jOhN"));
		}

		@Test
		void appliesReturnsFalseForDifferentValue() {
			assertFalse(IgnoreCase.value("John").applies("Jane"));
		}

		@Test
		void appliesReturnsFalseForNull() {
			assertFalse(IgnoreCase.value("John").applies(null));
		}

		@Test
		void appliesReturnsFalseForPartialMatch() {
			assertFalse(IgnoreCase.value("John").applies("John Doe"));
		}

		@Test
		void appliesWorksWithNonStringToString() {
			assertTrue(IgnoreCase.value("42").applies(42));
		}

		@Test
		void equalInstances() {
			assertEquals(IgnoreCase.value("test"), IgnoreCase.value("test"));
			assertNotEquals(IgnoreCase.value("test"), IgnoreCase.value("TEST"));
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Not
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class NotTest {

		@Test
		void appliesNegatesPlainValue() {
			var not = Not.value("INACTIVE");
			assertTrue(not.applies("ACTIVE"));
			assertFalse(not.applies("INACTIVE"));
		}

		@Test
		void appliesWithNull() {
			var not = Not.value("test");
			assertTrue(not.applies(null));
		}

		@Test
		void appliesNegatesNumber() {
			var not = Not.value(42);
			assertTrue(not.applies(43));
			assertFalse(not.applies(42));
		}

		@Test
		void canWrapOtherCriteria() {
			var notLike = Not.value(Like.contains("test"));
			assertEquals(Like.contains("test"), notLike.getValue());
		}

		@Test
		void canWrapNull() {
			var not = Not.value(null);
			assertEquals(null, not.getValue());
		}

		@Test
		void cannotNestNotInNot() {
			assertThrows(IllegalArgumentException.class, () -> Not.value(Not.value("test")));
		}

		@Test
		void equalInstances() {
			assertEquals(Not.value("test"), Not.value("test"));
			assertNotEquals(Not.value("test"), Not.value("other"));
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Helper enum for tests
	// ----------------------------------------------------------------------------------------------------------------

	enum TestEnum {
		MOBILE,
		HOME,
		WORK;
	}

}
