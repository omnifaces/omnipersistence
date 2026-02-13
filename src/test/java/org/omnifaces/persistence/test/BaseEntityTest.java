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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Phone.Type;

/**
 * Unit tests for {@link BaseEntity} identity methods: {@link BaseEntity#hashCode()}, {@link BaseEntity#equals(Object)},
 * {@link BaseEntity#compareTo(BaseEntity)} and {@link BaseEntity#toString()}.
 * <p>
 * Tests cover both the default ID-based behavior ({@link Person}) and the custom property-based overrides ({@link Phone}).
 */
public class BaseEntityTest {

	// ----------------------------------------------------------------------------------------------------------------
	// Default behavior tests (Person: uses BaseEntity defaults based on ID)
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class DefaultHashCode {

		@Test
		void sameIdProducesSameHashCode() {
			var person1 = createPerson(1L);
			var person2 = createPerson(1L);
			assertEquals(person1.hashCode(), person2.hashCode());
		}

		@Test
		void differentIdProducesDifferentHashCode() {
			var person1 = createPerson(1L);
			var person2 = createPerson(2L);
			assertNotEquals(person1.hashCode(), person2.hashCode());
		}

		@Test
		void nullIdFallsBackToSystemHashCode() {
			var person1 = new Person();
			var person2 = new Person();
			// With null ID, each instance should fall back to Object.hashCode() (identity-based).
			assertNotEquals(person1.hashCode(), person2.hashCode());
		}

		@Test
		void nullIdIsSelfConsistent() {
			var person = new Person();
			assertEquals(person.hashCode(), person.hashCode());
		}

		@Test
		void hashCodeIsConsistentAcrossInvocations() {
			var person = createPerson(42L);
			var first = person.hashCode();
			assertEquals(first, person.hashCode());
			assertEquals(first, person.hashCode());
		}

		@Test
		void entitiesWithSameIdWorkInHashSet() {
			var person1 = createPerson(1L);
			var person2 = createPerson(1L);
			var person3 = createPerson(2L);
			Set<Person> set = new HashSet<>();
			set.add(person1);
			set.add(person2);
			set.add(person3);
			assertEquals(2, set.size());
		}
	}

	@Nested
	class DefaultEquals {

		@Test
		void sameInstanceIsEqual() {
			var person = createPerson(1L);
			assertTrue(person.equals(person));
		}

		@Test
		void sameIdIsEqual() {
			var person1 = createPerson(1L);
			var person2 = createPerson(1L);
			assertTrue(person1.equals(person2));
			assertTrue(person2.equals(person1));
		}

		@Test
		void differentIdIsNotEqual() {
			var person1 = createPerson(1L);
			var person2 = createPerson(2L);
			assertFalse(person1.equals(person2));
		}

		@Test
		void nullIsNotEqual() {
			var person = createPerson(1L);
			assertFalse(person.equals(null));
		}

		@Test
		void differentTypeIsNotEqual() {
			var person = createPerson(1L);
			var phone = createPhone(1L, Type.HOME, "123");
			assertFalse(person.equals(phone));
			assertFalse(phone.equals(person));
		}

		@Test
		void nullIdEntitiesAreNotEqual() {
			var person1 = new Person();
			var person2 = new Person();
			// When all getter values are null, equals returns false (even for same type).
			assertFalse(person1.equals(person2));
		}

		@Test
		void nullIdEntityIsNotEqualToNonNullIdEntity() {
			var person1 = new Person();
			var person2 = createPerson(1L);
			assertFalse(person1.equals(person2));
			assertFalse(person2.equals(person1));
		}

		@Test
		void equalsIsSymmetric() {
			var person1 = createPerson(5L);
			var person2 = createPerson(5L);
			assertEquals(person1.equals(person2), person2.equals(person1));
		}

		@Test
		void equalsIsTransitive() {
			var person1 = createPerson(5L);
			var person2 = createPerson(5L);
			var person3 = createPerson(5L);
			assertTrue(person1.equals(person2));
			assertTrue(person2.equals(person3));
			assertTrue(person1.equals(person3));
		}

		@SuppressWarnings("unlikely-arg-type")
		@Test
		void nonEntityIsNotEqual() {
			var person = createPerson(1L);
			assertFalse(person.equals("not an entity"));
		}
	}

	@Nested
	class DefaultCompareTo {

		@Test
		void smallerIdComesFirst() {
			var person1 = createPerson(1L);
			var person2 = createPerson(2L);
			assertTrue(person1.compareTo(person2) < 0);
			assertTrue(person2.compareTo(person1) > 0);
		}

		@Test
		void sameIdIsZero() {
			var person1 = createPerson(1L);
			var person2 = createPerson(1L);
			assertEquals(0, person1.compareTo(person2));
		}

		@Test
		void nullIdSortsLast() {
			var withId = createPerson(1L);
			var withoutId = new Person();
			assertTrue(withId.compareTo(withoutId) < 0);
			assertTrue(withoutId.compareTo(withId) > 0);
		}

		@Test
		void nullEntitySortsLast() {
			var person = createPerson(1L);
			assertTrue(person.compareTo(null) < 0);
		}

		@Test
		void bothNullIdIsZero() {
			var person1 = new Person();
			var person2 = new Person();
			assertEquals(0, person1.compareTo(person2));
		}

		@Test
		void naturalOrderingSortsList() {
			var person3 = createPerson(3L);
			var person1 = createPerson(1L);
			var person2 = createPerson(2L);
			List<Person> list = new ArrayList<>(List.of(person3, person1, person2));
			Collections.sort(list);
			assertEquals(1L, list.get(0).getId());
			assertEquals(2L, list.get(1).getId());
			assertEquals(3L, list.get(2).getId());
		}

		@Test
		void treeSetDeduplicatesByCompareTo() {
			var person1 = createPerson(1L);
			var person2 = createPerson(1L);
			var person3 = createPerson(2L);
			TreeSet<Person> set = new TreeSet<>();
			set.add(person1);
			set.add(person2);
			set.add(person3);
			assertEquals(2, set.size());
		}
	}

	@Nested
	class DefaultToString {

		@Test
		void includesClassNameAndId() {
			var person = createPerson(42L);
			assertEquals("Person[42]", person.toString());
		}

		@Test
		void nullIdFallsBackToHashCode() {
			var person = new Person();
			assertEquals("Person[@" + person.hashCode() + "]", person.toString());
		}

		@Test
		void differentIdsProduceDifferentStrings() {
			var person1 = createPerson(1L);
			var person2 = createPerson(2L);
			assertNotEquals(person1.toString(), person2.toString());
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Custom override tests (Phone: overrides based on type + number)
	// ----------------------------------------------------------------------------------------------------------------

	@Nested
	class CustomHashCode {

		@Test
		void sameTypeAndNumberProducesSameHashCode() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			assertEquals(phone1.hashCode(), phone2.hashCode());
		}

		@Test
		void differentTypeProducesDifferentHashCode() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.MOBILE, "123");
			assertNotEquals(phone1.hashCode(), phone2.hashCode());
		}

		@Test
		void differentNumberProducesDifferentHashCode() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "456");
			assertNotEquals(phone1.hashCode(), phone2.hashCode());
		}

		@Test
		void nullTypeAndNumberFallsBackToSystemHashCode() {
			var phone1 = new Phone();
			var phone2 = new Phone();
			// With both custom getters returning null, falls back to Object.hashCode().
			assertNotEquals(phone1.hashCode(), phone2.hashCode());
		}

		@Test
		void partialNullStillHashesByNonNullProperty() {
			// Only number is set, type is null. The non-null value is still used for hashing.
			var phone1 = createPhone(1L, null, "123");
			var phone2 = createPhone(2L, null, "123");
			assertEquals(phone1.hashCode(), phone2.hashCode());
		}

		@Test
		void idIsIgnoredForCustomHashCode() {
			var phone1 = createPhone(1L, Type.WORK, "789");
			var phone2 = createPhone(99L, Type.WORK, "789");
			assertEquals(phone1.hashCode(), phone2.hashCode());
		}

		@Test
		void entitiesWithSameTypeAndNumberDeduplicateInHashSet() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			var phone3 = createPhone(3L, Type.MOBILE, "123");
			Set<Phone> set = new HashSet<>();
			set.add(phone1);
			set.add(phone2);
			set.add(phone3);
			assertEquals(2, set.size());
		}
	}

	@Nested
	class CustomEquals {

		@Test
		void sameInstanceIsEqual() {
			var phone = createPhone(1L, Type.HOME, "123");
			assertTrue(phone.equals(phone));
		}

		@Test
		void sameTypeAndNumberIsEqual() {
			var phone1 = createPhone(1L, Type.MOBILE, "123");
			var phone2 = createPhone(2L, Type.MOBILE, "123");
			assertTrue(phone1.equals(phone2));
		}

		@Test
		void differentTypeIsNotEqual() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.MOBILE, "123");
			assertFalse(phone1.equals(phone2));
		}

		@Test
		void differentNumberIsNotEqual() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "456");
			assertFalse(phone1.equals(phone2));
		}

		@Test
		void nullTypeWithSameNumberIsNotEqual() {
			var phone1 = createPhone(1L, null, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			assertFalse(phone1.equals(phone2));
			assertFalse(phone2.equals(phone1));
		}

		@Test
		void bothNullTypeWithSameNumberIsEqual() {
			var phone1 = createPhone(1L, null, "123");
			var phone2 = createPhone(2L, null, "123");
			assertTrue(phone1.equals(phone2));
		}

		@Test
		void allCustomPropertiesNullIsNotEqual() {
			var phone1 = new Phone();
			var phone2 = new Phone();
			// When all custom getters return null, equals returns false.
			assertFalse(phone1.equals(phone2));
		}

		@Test
		void nullIsNotEqual() {
			var phone = createPhone(1L, Type.HOME, "123");
			assertFalse(phone.equals(null));
		}

		@Test
		void differentEntityTypeIsNotEqual() {
			var phone = createPhone(1L, Type.HOME, "123");
			var person = createPerson(1L);
			assertFalse(phone.equals(person));
			assertFalse(person.equals(phone));
		}

		@Test
		void sameIdButDifferentCustomPropertiesIsNotEqual() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(1L, Type.WORK, "456");
			assertFalse(phone1.equals(phone2));
		}

		@Test
		void customEqualsIsSymmetric() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			assertEquals(phone1.equals(phone2), phone2.equals(phone1));
		}

		@Test
		void customEqualsIsTransitive() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			var phone3 = createPhone(3L, Type.HOME, "123");
			assertTrue(phone1.equals(phone2));
			assertTrue(phone2.equals(phone3));
			assertTrue(phone1.equals(phone3));
		}
	}

	@Nested
	class CustomCompareTo {

		@Test
		void orderedByTypeFirst() {
			var home = createPhone(1L, Type.HOME, "123");
			var mobile = createPhone(2L, Type.MOBILE, "123");
			var work = createPhone(3L, Type.WORK, "123");
			// Enum natural order: MOBILE(0), HOME(1), WORK(2)
			assertTrue(mobile.compareTo(home) < 0);
			assertTrue(home.compareTo(work) < 0);
			assertTrue(mobile.compareTo(work) < 0);
		}

		@Test
		void sameTypeThenOrderedByNumber() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "456");
			assertTrue(phone1.compareTo(phone2) < 0);
			assertTrue(phone2.compareTo(phone1) > 0);
		}

		@Test
		void sameTypeAndNumberIsZero() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			assertEquals(0, phone1.compareTo(phone2));
		}

		@Test
		void nullTypeSortsLast() {
			var withType = createPhone(1L, Type.HOME, "123");
			var withoutType = createPhone(2L, null, "123");
			assertTrue(withType.compareTo(withoutType) < 0);
			assertTrue(withoutType.compareTo(withType) > 0);
		}

		@Test
		void nullNumberSortsLast() {
			var withNumber = createPhone(1L, Type.HOME, "123");
			var withoutNumber = createPhone(2L, Type.HOME, null);
			assertTrue(withNumber.compareTo(withoutNumber) < 0);
			assertTrue(withoutNumber.compareTo(withNumber) > 0);
		}

		@Test
		void nullEntitySortsLast() {
			var phone = createPhone(1L, Type.HOME, "123");
			assertTrue(phone.compareTo(null) < 0);
		}

		@Test
		void naturalOrderingSortsList() {
			var work = createPhone(1L, Type.WORK, "123");
			var mobile = createPhone(2L, Type.MOBILE, "123");
			var home = createPhone(3L, Type.HOME, "123");
			List<Phone> list = new ArrayList<>(List.of(work, mobile, home));
			Collections.sort(list);
			// MOBILE(0) < HOME(1) < WORK(2)
			assertEquals(Type.MOBILE, list.get(0).getType());
			assertEquals(Type.HOME, list.get(1).getType());
			assertEquals(Type.WORK, list.get(2).getType());
		}

		@Test
		void sameTypeSortsByNumberInList() {
			var phone1 = createPhone(1L, Type.HOME, "456");
			var phone2 = createPhone(2L, Type.HOME, "123");
			var phone3 = createPhone(3L, Type.HOME, "789");
			List<Phone> list = new ArrayList<>(List.of(phone1, phone2, phone3));
			Collections.sort(list);
			assertEquals("123", list.get(0).getNumber());
			assertEquals("456", list.get(1).getNumber());
			assertEquals("789", list.get(2).getNumber());
		}

		@Test
		void treeSetDeduplicatesByTypeAndNumber() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(2L, Type.HOME, "123");
			var phone3 = createPhone(3L, Type.WORK, "123");
			TreeSet<Phone> set = new TreeSet<>();
			set.add(phone1);
			set.add(phone2);
			set.add(phone3);
			assertEquals(2, set.size());
		}
	}

	@Nested
	class CustomToString {

		@Test
		void includesClassNameAndCustomProperties() {
			var phone = createPhone(1L, Type.HOME, "123");
			assertEquals("Phone[HOME, 123]", phone.toString());
		}

		@Test
		void nullPropertiesShowAsNull() {
			var phone = new Phone();
			assertEquals("Phone[null, null]", phone.toString());
		}

		@Test
		void partialNullProperties() {
			var phone = createPhone(1L, Type.MOBILE, null);
			assertEquals("Phone[MOBILE, null]", phone.toString());
		}

		@Test
		void idIsNotIncludedInCustomToString() {
			var phone1 = createPhone(1L, Type.HOME, "123");
			var phone2 = createPhone(99L, Type.HOME, "123");
			assertEquals(phone1.toString(), phone2.toString());
		}
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Helper methods
	// ----------------------------------------------------------------------------------------------------------------

	private static Person createPerson(Long id) {
		var person = new Person();
		person.setId(id);
		return person;
	}

	private static Phone createPhone(Long id, Type type, String number) {
		var phone = new Phone();
		phone.setId(id);
		phone.setType(type);
		phone.setNumber(number);
		return phone;
	}

}
