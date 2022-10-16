package org.omnifaces.persistence.test;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Phone.Type;

/**
 * Tests following identity methods of {@link BaseEntity}:
 * - {@link BaseEntity#hashCode()}
 * - {@link BaseEntity#equals(Object)}
 * - {@link BaseEntity#compareTo(BaseEntity)}
 * - {@link BaseEntity#toString()}
 *
 */
public class TestBaseEntityIdentity {

	@Test
	public void testDefaultHashCode() {
		List<Person> list1 = createPersons();
		assertEquals("2,1,3,2", getIds(list1));

		List<Person> list2 = new ArrayList<>(new HashSet<>(list1));
		assertEquals("1,2,3", getSortedIds(list2));
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void testDefaultEquals() {
		List list = createPersons();
		Person person0 = new Person();
		int indexOf0 = list.indexOf(person0);
		assertEquals(-1, indexOf0);

		Person person1 = new Person();
		person1.setId(3L);
		int indexOf1 = list.indexOf(person1);
		assertEquals(2, indexOf1);

		Person person2 = new Person();
		int indexOf2 = list.indexOf(person2);
		assertEquals(-1, indexOf2);

		Phone phone1 = new Phone();
		phone1.setId(3L);
		int indexOf3 = list.indexOf(phone1);
		assertEquals(-1, indexOf3);
	}

	@Test
	public void testDefaultCompareTo() {
		List<Person> list = createPersons();
		list.sort(Comparator.naturalOrder());
		assertEquals("1,2,2,3", getIds(list));

		list.add(2, new Person());
		list.sort(Comparator.naturalOrder());
		assertEquals("1,2,2,3,-1", getIds(list));
	}

	@Test
	public void testDefaultToString() {
		List<Person> list = createPersons();
		assertEquals("[Person[2], Person[1], Person[3], Person[2]]", list.toString());
	}

	@Test
	public void testCustomHashCode() {
		List<Phone> list1 = createPhones();
		assertEquals("1,2,3,4,5", getIds(list1));

		List<Phone> list2 = new ArrayList<>(new HashSet<>(list1));
		assertEquals("1,2,4,5", getSortedIds(list2));
	}

	@Test
	public void testCustomEquals() {
		List<Phone> list = createPhones();
		Phone phone0 = new Phone();
		int indexOf0 = list.indexOf(phone0);
		assertEquals(-1, indexOf0);

		Phone phone1 = new Phone();
		phone1.setId(3L);
		int indexOf1 = list.indexOf(phone1);
		assertEquals(-1, indexOf1);

		phone1.setType(Type.MOBILE);
		indexOf1 = list.indexOf(phone1);
		assertEquals(-1, indexOf1);

		phone1.setType(null);
		phone1.setNumber("123");
		indexOf1 = list.indexOf(phone1);
		assertEquals(-1, indexOf1);

		phone1.setType(Type.MOBILE);
		indexOf1 = list.indexOf(phone1);
		assertEquals(1, indexOf1);
	}

	@Test
	public void testCustomCompareTo() {
		List<Phone> list1 = createPhones();
		list1.sort(Comparator.naturalOrder());
		assertEquals("2,1,3,5,4", getIds(list1));

		SortedSet<Phone> set1 = new TreeSet<>(list1);
		assertEquals("2,1,5,4", getIds(set1));

		set1.add(new Phone());
		assertEquals("2,1,5,4,-1", getIds(set1));

		List<Phone> list2 = createPhones();
		list2.add(2, new Phone());
		assertEquals("1,2,-1,3,4,5", getIds(list2));

		list2.add(new Phone());
		assertEquals("1,2,-1,3,4,5,-1", getIds(list2));

		SortedSet<Phone> set2 = new TreeSet<>(list2);
		assertEquals("2,1,5,4,-1", getIds(set2));

		set2.add(new Phone());
		assertEquals("2,1,5,4,-1", getIds(set2));
	}

	@Test
	public void testCustomToString() {
		List<Phone> list = createPhones();
		assertEquals("[Phone[HOME, 123], Phone[MOBILE, 123], Phone[HOME, 123], Phone[WORK, 123], Phone[HOME, 456]]", list.toString());
	}

	private static List<Person> createPersons() {
		return stream(new Long[] { 2L, 1L, 3L, 2L }).map(id -> {
			Person person = new Person();
			person.setId(id);
			return person;
		}).collect(toList());
	}

	private static List<Phone> createPhones() {
		List<Phone> phones = new ArrayList<>();

		Phone phone1 = new Phone();
		phone1.setId(1L);
		phone1.setType(Type.HOME);
		phone1.setNumber("123");
		phones.add(phone1);

		Phone phone2 = new Phone();
		phone2.setId(2L);
		phone2.setType(Type.MOBILE);
		phone2.setNumber("123");
		phones.add(phone2);

		Phone phone3 = new Phone();
		phone3.setId(3L);
		phone3.setType(Type.HOME);
		phone3.setNumber("123");
		phones.add(phone3);

		Phone phone4 = new Phone();
		phone4.setId(4L);
		phone4.setType(Type.WORK);
		phone4.setNumber("123");
		phones.add(phone4);

		Phone phone5 = new Phone();
		phone5.setId(5L);
		phone5.setType(Type.HOME);
		phone5.setNumber("456");
		phones.add(phone5);

		return phones;
	}

	private static <E extends BaseEntity<Long>> String getIds(Collection<E> entities) {
		return entities.stream().map(e -> Objects.toString(e.getId(), "-1")).collect(joining(","));
	}

	private static <E extends BaseEntity<Long>> String getSortedIds(List<E> entities) {
		return entities.stream().map(e -> Objects.toString(e.getId(), "-1")).sorted().collect(joining(","));
	}

}
