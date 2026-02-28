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

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.service.StartupServiceEJB.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupServiceEJB.TOTAL_RECORDS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.criteria.Between;
import org.omnifaces.persistence.criteria.Enumerated;
import org.omnifaces.persistence.criteria.IgnoreCase;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.criteria.Numeric;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.exception.NonDeletableEntityException;
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Config;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Group;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.ConfigService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.TestAuditListener;

@ExtendWith(ArquillianExtension.class)
public abstract class OmniPersistenceIT {

    protected static WebArchive createDeployment(Class<?> testClass, Class<?> excludedStartupService) {
        var persistenceUnitName = testClass.getSimpleName();
        var maven = Maven.resolver();
        return create(WebArchive.class)
            .addPackages(true, testClass.getPackage())
            .deleteClass(excludedStartupService)
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
            .addAsResource(new StringAsset(substitute("META-INF/persistence.xml", persistenceUnitName)), "META-INF/persistence.xml")
            .addAsWebInfResource(new StringAsset(substitute("WEB-INF/web.xml", persistenceUnitName)), "web.xml")
            .addAsResource("META-INF/sql/create-test.sql")
            .addAsResource("META-INF/sql/drop-test.sql")
            .addAsResource("META-INF/sql/load-test.sql")
            .addAsLibrary(create(MavenImporter.class).loadPomFromFile("pom.xml").importBuildOutput().as(JavaArchive.class))
            .addAsLibraries(maven.loadPomFromFile("pom.xml").importCompileAndRuntimeDependencies().resolve().withTransitivity().asFile())
            .addAsLibraries(maven.resolve("com.h2database:h2:" + getProperty("test.h2.version")).withTransitivity().asFile());
    }

    private static String substitute(String resourceName, String persistenceUnitName) {
        try (var stream = OmniPersistenceIT.class.getClassLoader().getResourceAsStream(resourceName)) {
            return new String(stream.readAllBytes(), UTF_8).replace("${persistenceUnitName}", persistenceUnitName);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected abstract PersonService personService();
    protected abstract PhoneService phoneService();
    protected abstract BaseEntityService<Long, Text> textService();
    protected abstract BaseEntityService<Long, Comment> commentService();
    protected abstract BaseEntityService<String, Lookup> lookupService();
    protected abstract ConfigService configService();

    protected static boolean isEclipseLink() {
        return getenv("MAVEN_CMD_LINE_ARGS").contains("-eclipselink");
    }

    protected static boolean isOpenJPA() {
        return getenv("MAVEN_CMD_LINE_ARGS").contains("-openjpa");
    }

    private static Person createNewPerson(String email) {
        var person = new Person();
        person.setEmail(email);
        person.setGender(Gender.OTHER);
        person.setDateOfBirth(LocalDate.now());
        return person;
    }


    // Basic operations -----------------------------------------------------------------------------------------------

    @Test
    void testFindPerson() {
        var existingPerson = personService().findById(1L);
        assertTrue(existingPerson.isPresent(), "Existing person");
        var nonExistingPerson = personService().findById(0L);
        assertFalse(nonExistingPerson.isPresent(), "Non-existing person");
    }

    @Test
    void testGetPerson() {
        var existingPerson = personService().getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var nonExistingPerson = personService().getById(0L);
        assertNull(nonExistingPerson, "Non-existing person");
    }

    @Test
    void testPersistAndDeleteNewPerson() {
        var newPerson = createNewPerson("testPersistNewPerson@example.com");
        personService().persist(newPerson);
        Long expectedNewId = TOTAL_RECORDS + 1L;
        assertEquals(expectedNewId, newPerson.getId(), "New person ID");
        assertEquals(TOTAL_RECORDS + 1, personService().list().size(), "Total records");

        personService().delete(newPerson);
        assertEquals(TOTAL_RECORDS, personService().list().size(), "Total records");
    }

    @Test
    void testPersistExistingPerson() {
        var existingPerson = createNewPerson("testPersistExistingPerson@example.com");
        existingPerson.setId(1L);
        assertThrows(IllegalEntityStateException.class, () -> personService().persist(existingPerson));
    }

    @Test
    void testUpdateExistingPerson() {
        var existingPerson = personService().getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var newEmail = "testUpdateExistingPerson@example.com";
        existingPerson.setEmail(newEmail);
        personService().update(existingPerson);
        var existingPersonAfterUpdate = personService().getById(1L);
        assertEquals(newEmail, existingPersonAfterUpdate.getEmail(), "Email updated");
    }

    @Test
    void testUpdateNewPerson() {
        var newPerson = createNewPerson("testUpdateNewPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personService().update(newPerson));
    }

    @Test
    void testResetExistingPerson() {
        var existingPerson = personService().getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var oldEmail = existingPerson.getEmail();
        existingPerson.setEmail("testResetExistingPerson@example.com");
        personService().reset(existingPerson);
        assertEquals(oldEmail, existingPerson.getEmail(), "Email resetted");
    }

    @Test
    void testResetNonExistingPerson() {
        var nonExistingPerson = createNewPerson("testResetNonExistingPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personService().reset(nonExistingPerson));
    }

    @Test
    void testDeleteNonExistingPerson() {
        var nonExistingPerson = createNewPerson("testDeleteNonExistingPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personService().delete(nonExistingPerson));
    }


    // Batch operations -----------------------------------------------------------------------------------------------

    @Test
    void testGetByIds() {
        var persons = personService().getByIds(List.of(1L, 2L, 3L));
        assertEquals(3, persons.size(), "Should find 3 persons by IDs");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(1L)), "Contains person 1");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(2L)), "Contains person 2");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(3L)), "Contains person 3");
    }

    @Test
    void testGetByIdsWithNonExisting() {
        var persons = personService().getByIds(List.of(1L, 999999L));
        assertEquals(1, persons.size(), "Should find only 1 existing person");
        assertEquals(1L, persons.get(0).getId(), "Found person has correct ID");
    }

    @Test
    void testGetByIdsEmpty() {
        var persons = personService().getByIds(List.of());
        assertTrue(persons.isEmpty(), "Empty IDs should return empty list");
    }


    // Page -----------------------------------------------------------------------------------------------------------

    @Test
    void testPage() {
        var persons = personService().getPage(Page.ALL, true);
        assertEquals(TOTAL_RECORDS, persons.size(), "There are 200 records");

        var males = personService().getPage(Page.with().anyMatch(Collections.singletonMap("gender", Gender.MALE)).build(), true);
        assertTrue(males.size() < TOTAL_RECORDS, "There are less than 200 records");
    }

    @Test
    void testPageByLazyManyToOne() { // This was failing since Hibernate 6 upgrade.
        var person = personService().getById(1L);
        var phones = phoneService().getPage(Page.with().allMatch(Map.of("owner", person)).build(), true);
        assertEquals(TOTAL_PHONES_PER_PERSON_0, phones.size(), "There are 3 phones");
    }

    @Test
    void testPageWithOffsetAndLimit() {
        var firstPage = personService().getPage(Page.of(0, 10), true);
        assertEquals(10, firstPage.size(), "First page has 10 records");
        assertEquals(TOTAL_RECORDS, firstPage.getEstimatedTotalNumberOfResults(), "Total count is correct");

        var secondPage = personService().getPage(Page.of(10, 10), true);
        assertEquals(10, secondPage.size(), "Second page has 10 records");

        assertFalse(firstPage.get(0).getId().equals(secondPage.get(0).getId()), "Pages contain different records");
    }

    @Test
    void testPageWithoutCount() {
        var page = personService().getPage(Page.of(0, 10), false);
        assertEquals(10, page.size(), "Page has 10 records");
        assertEquals(-1, page.getEstimatedTotalNumberOfResults(), "Count is unknown when not requested");
    }

    @Test
    void testPageWithOrdering() {
        var ascPage = personService().getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        var descPage = personService().getPage(Page.with().range(0, 10).orderBy("id", false).build(), false);
        assertTrue(ascPage.get(0).getId() < ascPage.get(9).getId(), "Ascending order");
        assertTrue(descPage.get(0).getId() > descPage.get(9).getId(), "Descending order");
    }

    @Test
    void testPageOne() {
        var result = personService().getPage(Page.ONE, false);
        assertEquals(1, result.size(), "Page.ONE returns exactly 1 record");
    }

    @Test
    void testPageWithMultipleRequiredCriteria() {
        var person1 = personService().getById(1L);
        var criteria = Map.<String, Object>of(
            "gender", person1.getGender(),
            "email", IgnoreCase.value(person1.getEmail())
        );
        var result = personService().getPage(Page.with().allMatch(criteria).build(), true);
        assertEquals(1, result.size(), "Exact match by gender and email should return 1 record");
        assertEquals(person1.getId(), result.get(0).getId(), "Found the correct person");
    }

    @Test
    void testPageWithMultipleOptionalCriteria() {
        var person1 = personService().getById(1L);
        var person2 = personService().getById(2L);
        var criteria = Map.<String, Object>of(
            "email", person1.getEmail(),
            "gender", person2.getGender()
        );
        var result = personService().getPage(Page.with().anyMatch(criteria).build(), true);
        assertTrue(result.size() >= 1, "At least one match by email or gender");
    }


    // Page with criteria types ---------------------------------------------------------------------------------------

    @Test
    void testPageWithLikeContains() {
        var result = personService().getPage(Page.with().allMatch(Map.of("email", Like.contains("e99@e"))).build(), true);
        assertEquals(1, result.size(), "LIKE contains matches e99@e");
        assertTrue(result.get(0).getEmail().contains("name99@"), "Email contains name99@");
    }

    @Test
    void testPageWithLikeStartsWith() {
        var result = personService().getPage(Page.with().allMatch(Map.of("email", Like.startsWith("name1@"))).build(), true);
        assertTrue(result.size() >= 1, "LIKE starts with matches at least name1@");
        result.forEach(p -> assertTrue("name1@example.com".equals(p.getEmail()), "Email is name1@example.com"));
    }

    @Test
    void testPageWithLikeEndsWith() {
        var result = personService().getPage(Page.with().allMatch(Map.of("email", Like.endsWith("@example.com"))).build(), true);
        assertEquals(TOTAL_RECORDS, result.size(), "All records end with @example.com");
    }

    @Test
    void testPageWithLikeNoMatch() {
        var result = personService().getPage(Page.with().allMatch(Map.of("email", Like.contains("nonexistent_xyz"))).build(), true);
        assertEquals(0, result.size(), "No records match nonexistent search");
    }

    @Test
    void testPageWithIgnoreCase() {
        var person = personService().getById(1L);
        var uppercaseEmail = person.getEmail().toUpperCase();
        var result = personService().getPage(Page.with().allMatch(Map.of("email", IgnoreCase.value(uppercaseEmail))).build(), true);
        assertEquals(1, result.size(), "Case insensitive exact match finds the record");
        assertEquals(person.getId(), result.get(0).getId(), "Found the correct person");
    }

    @Test
    void testPageWithEnumCriteria() {
        var maleResult = personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.MALE))).build(), true);
        var femaleResult = personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.FEMALE))).build(), true);
        assertTrue(maleResult.size() > 0, "Some males exist");
        assertTrue(femaleResult.size() > 0, "Some females exist");
        assertEquals(TOTAL_RECORDS, maleResult.size() + femaleResult.size()
            + personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.TRANS))).build(), true).size()
            + personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.OTHER))).build(), true).size(),
            "All genders sum up to total");
    }

    @Test
    void testPageWithNumericCriteria() {
        var result = personService().getPage(Page.with().allMatch(Map.of("id", Numeric.value(1))).build(), true);
        assertEquals(1, result.size(), "Numeric match finds exactly 1 record");
        assertEquals(1L, result.get(0).getId(), "Found person with ID 1");
    }

    @Test
    void testPageWithOrderGreaterThan() {
        var result = personService().getPage(Page.with().allMatch(Map.of("id", Order.greaterThan(TOTAL_RECORDS - 5L))).build(), true);
        assertEquals(5, result.size(), "IDs greater than 195 should be 196-200");
        result.forEach(p -> assertTrue(p.getId() > TOTAL_RECORDS - 5L, "ID is greater than threshold"));
    }

    @Test
    void testPageWithOrderLessThanOrEqualTo() {
        var result = personService().getPage(Page.with().allMatch(Map.of("id", Order.lessThanOrEqualTo(5L))).build(), true);
        assertEquals(5, result.size(), "IDs <= 5 should be 1-5");
        result.forEach(p -> assertTrue(p.getId() <= 5L, "ID is <= 5"));
    }

    @Test
    void testPageWithBetween() {
        var result = personService().getPage(Page.with().allMatch(Map.of("id", Between.range(10L, 19L))).build(), true);
        assertEquals(10, result.size(), "IDs between 10 and 19 should be 10 records");
        result.forEach(p -> {
            assertTrue(p.getId() >= 10L, "ID >= 10");
            assertTrue(p.getId() <= 19L, "ID <= 19");
        });
    }

    @Test
    void testPageWithBetweenDates() {
        var start = LocalDate.of(1950, 1, 1);
        var end = LocalDate.of(1960, 12, 31);
        var result = personService().getPage(Page.with().allMatch(Map.of("dateOfBirth", Between.range(start, end))).build(), true);
        result.forEach(p -> {
            assertFalse(p.getDateOfBirth().isBefore(start), "Date of birth is not before start");
            assertFalse(p.getDateOfBirth().isAfter(end), "Date of birth is not after end");
        });
    }

    @Test
    void testPageWithNotCriteria() {
        var allMales = personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.MALE))).build(), true);
        var notMales = personService().getPage(Page.with().allMatch(Map.of("gender", Not.value(Gender.MALE))).build(), true);
        assertEquals(TOTAL_RECORDS, allMales.size() + notMales.size(), "Males + not-males = total");
    }


    // Page with fetch fields (PersonService/PhoneService custom methods) ---------------------------------------------

    @Test
    void testPageWithAddress() {
        var result = personService().getAllWithAddress();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        result.forEach(p -> assertNotNull(p.getAddress(), "Address is fetched"));
        result.forEach(p -> assertNotNull(p.getAddress().getStreet(), "Address street is accessible"));
    }

    @Test
    void testPageWithPhones() {
        var result = personService().getAllWithPhones();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        var person0 = result.stream().filter(p -> p.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals(TOTAL_PHONES_PER_PERSON_0, person0.getPhones().size(), "Person 1 has expected phones");
    }

    @Test
    void testPageWithGroups() {
        var result = personService().getAllWithGroups();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        result.forEach(p -> assertFalse(p.getGroups().isEmpty(), "Each person has at least one group"));
    }

    @Test
    void testPageWithPhonesAndPagination() {
        var firstPage = personService().getPageWithPhones(Page.of(0, 10), true);
        assertEquals(10, firstPage.size(), "First page returns exactly 10 persons, not fewer due to join row inflation");
        assertEquals(TOTAL_RECORDS, firstPage.getEstimatedTotalNumberOfResults(), "Total count is correct");
        firstPage.forEach(p -> assertFalse(p.getPhones().isEmpty(), "Phones collection is populated after detachment"));

        var secondPage = personService().getPageWithPhones(Page.of(10, 10), true);
        assertEquals(10, secondPage.size(), "Second page also returns exactly 10 persons");
        assertFalse(firstPage.get(0).getId().equals(secondPage.get(0).getId()), "Pages contain different persons");
    }

    @Test
    void testPageWithGroupsAndPagination() {
        var firstPage = personService().getPageWithGroups(Page.of(0, 10), true);
        assertEquals(10, firstPage.size(), "First page returns exactly 10 persons, not fewer due to join row inflation");
        assertEquals(TOTAL_RECORDS, firstPage.getEstimatedTotalNumberOfResults(), "Total count is correct");
        firstPage.forEach(p -> assertFalse(p.getGroups().isEmpty(), "Groups collection is populated after detachment"));

        var secondPage = personService().getPageWithGroups(Page.of(10, 10), true);
        assertEquals(10, secondPage.size(), "Second page also returns exactly 10 persons");
        assertFalse(firstPage.get(0).getId().equals(secondPage.get(0).getId()), "Pages contain different persons");
    }

    @Test
    void testPageWithGroupsAndOptionalElementCollectionFilter() {
        var firstPage = personService().getPageWithGroups(Page.with().range(0, 10).anyMatch(Map.of("groups", Like.contains("user"))).build(), true);
        assertEquals(10, firstPage.size(), "First page returns exactly 10 persons, not fewer due to join row inflation");
        firstPage.forEach(p -> assertTrue(p.getGroups().contains(Group.USER), "Groups collection is populated after detachment"));
    }

    @Test
    void testPageWithGroupsAndRequiredElementCollectionFilter() {
        var firstPage = personService().getPageWithGroups(Page.with().range(0, 10).allMatch(Map.of("groups", List.of(Group.USER, Group.DEVELOPER))).build(), true);
        assertEquals(10, firstPage.size(), "First page returns exactly 10 persons, not fewer due to join row inflation");
        firstPage.forEach(p -> assertTrue(p.getGroups().contains(Group.USER) && p.getGroups().contains(Group.DEVELOPER), "Groups collection is populated after detachment"));
    }

    @Test
    void testPageWithPhonesFilteredByTypeSet() {
        var result = personService().getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.type", Set.of(Phone.Type.MOBILE))).build(), true);
        assertFalse(result.isEmpty(), "Some persons have MOBILE phones");
        if (!isOpenJPA()) {
            assertTrue(result.getEstimatedTotalNumberOfResults() < TOTAL_RECORDS, "Not all persons have MOBILE phones"); // OpenJPA generates broken nested correlated subqueries for @OneToMany in count subquery context, so the count is inaccurate there.
        }
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Filtered person has phones"));
        if (isOpenJPA() || isEclipseLink()) { // Hibernate JOIN FETCH returns all phone types; in-memory filtering of postponed fetches only applies to OpenJPA/EclipseLink.
            result.forEach(person -> person.getPhones().forEach(phone -> assertEquals(Phone.Type.MOBILE, phone.getType(), "Only MOBILE phones remain after in-memory filtering of postponed fetch")));
        }
    }

    @Test
    void testPageWithPhonesFilteredByNumberLike() {
        var result = personService().getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.number", Like.contains("11"))).build(), true);
        assertFalse(result.isEmpty(), "Some persons have phones with number containing 11");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Filtered person has phones"));
        result.forEach(person -> person.getPhones().forEach(phone -> assertTrue(phone.getNumber().contains("11"), "Only phones with 11 in number remain after in-memory filtering of postponed fetch")));
    }

    @Test
    void testPageWithGroupsSortedAscending() {
        var result = personService().getPageWithGroups(Page.with().range(0, 10).orderBy("groups", true).build(), false);
        assertEquals(10, result.size(), "Page with groups sorted ascending returns 10 persons");
        result.forEach(p -> assertFalse(p.getGroups().isEmpty(), "Groups collection is populated"));
    }

    @Test
    void testPageWithPhonesSortedByNumberAscending() {
        var result = personService().getPageWithPhones(Page.with().range(0, 10).orderBy("phones.number", true).build(), false);
        assertEquals(10, result.size(), "Page has 10 records");
        result.forEach(person -> {
            var numbers = person.getPhones().stream().map(Phone::getNumber).toList();
            assertEquals(numbers.stream().sorted().toList(), numbers, "Phones within person are sorted ascending by number");
        });
        var firstNumbers = result.stream().filter(p -> !p.getPhones().isEmpty()).map(p -> p.getPhones().get(0).getNumber()).toList();
        for (var i = 0; i < firstNumbers.size() - 1; i++) {
            assertTrue(firstNumbers.get(i).compareTo(firstNumbers.get(i + 1)) <= 0, "Entities are ordered ascending by representative phone number");
        }
    }

    @Test
    void testPageWithPhonesFilteredAndSortedDescending() {
        var result = personService().getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.number", Like.contains("11"))).orderBy("phones.number", false).build(), true);
        assertFalse(result.isEmpty(), "Some persons have phones with number containing 11");
        result.forEach(person -> {
            assertFalse(person.getPhones().isEmpty(), "Filtered person has phones");
            person.getPhones().forEach(phone -> assertTrue(phone.getNumber().contains("11"), "Only phones with 11 in number remain after filtering"));
            var numbers = person.getPhones().stream().map(Phone::getNumber).toList();
            assertEquals(numbers.stream().sorted(Comparator.reverseOrder()).toList(), numbers, "Phones within person are sorted descending by number");
        });
        var firstNumbers = result.stream().filter(p -> !p.getPhones().isEmpty()).map(p -> p.getPhones().get(0).getNumber()).toList();
        for (var i = 0; i < firstNumbers.size() - 1; i++) {
            assertTrue(firstNumbers.get(i).compareTo(firstNumbers.get(i + 1)) >= 0, "Entities are ordered descending by representative phone number");
        }
    }

    @Test
    void testPhonePageWithOwners() {
        var result = phoneService().getAllWithOwners();
        assertFalse(result.isEmpty(), "Phones exist");
        result.forEach(p -> assertNotNull(p.getOwner(), "Owner is fetched"));
        result.forEach(p -> assertNotNull(p.getOwner().getEmail(), "Owner email is accessible"));
    }

    @Test
    void testPhonePageSortedByTransientEmail() {
        var result = phoneService().getPageWithOwners(Page.with().range(0, 10).orderBy("email", true).build(), false);
        assertEquals(10, result.size(), "Page has 10 phones");
        result.forEach(p -> assertNotNull(p.getOwner(), "Owner is fetched"));
        var emails = result.stream().map(Phone::getEmail).toList();
        assertEquals(emails.stream().sorted().toList(), emails, "Phones are sorted ascending by transient owner email");
    }

    @Test
    void testPhonePageFilteredByTransientEmail() {
        var expectedEmail = "name0@example.com";
        var result = phoneService().getPageWithOwners(Page.with().allMatch(Map.of("email", expectedEmail)).build(), false);
        assertEquals(TOTAL_PHONES_PER_PERSON_0, result.size(), "Only phones of person 0 match the owner email filter");
        result.forEach(p -> assertEquals(expectedEmail, p.getEmail(), "All matching phones belong to the expected owner"));
    }


    // Page with DTO mapping ------------------------------------------------------------------------------------------

    @Test
    void testPageOfPersonCards() {
        var result = personService().getAllPersonCards();
        assertEquals(TOTAL_RECORDS, result.size(), "All person cards returned");
        result.forEach(card -> {
            assertNotNull(card.getId(), "Card has ID");
            assertNotNull(card.getEmail(), "Card has email");
            assertNotNull(card.getAddressString(), "Card has address string");
            assertTrue(card.getAddressString().contains(","), "Address string is formatted with commas");
            assertNotNull(card.getTotalPhones(), "Card has total phones");
            assertTrue(card.getTotalPhones() >= 1, "Each person has at least one phone");
        });
    }

    @Test
    void testPageOfPersonCardsWithPagination() {
        var page = personService().getPageOfPersonCards(Page.of(0, 5), true);
        assertEquals(5, page.size(), "Page returns 5 cards");
        assertEquals(TOTAL_RECORDS, page.getEstimatedTotalNumberOfResults(), "Total count is correct");
    }


    // Page with cursor-based paging ----------------------------------------------------------------------------------

    @Test
    void testCursorBasedPaging() {
        var page1 = personService().getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        assertEquals(10, page1.size(), "First page has 10 persons");

        var lastSeen = page1.get(page1.size() - 1);
        var page2 = personService().getPage(Page.with().range(lastSeen, 10, false).orderBy("id", true).build(), false);
        assertEquals(10, page2.size(), "Second page has 10 persons");

        var page1Ids = page1.stream().map(Person::getId).toList();
        var page2Ids = page2.stream().map(Person::getId).toList();
        assertTrue(Collections.disjoint(page1Ids, page2Ids), "Pages do not overlap");
        assertTrue(page2.get(0).getId() > page1.get(page1.size() - 1).getId(), "Second page starts after first page");
    }

    @Test
    void testCursorBasedPagingReversed() {
        var page1 = personService().getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        var lastSeen = page1.get(page1.size() - 1);
        var page2 = personService().getPage(Page.with().range(lastSeen, 10, false).orderBy("id", true).build(), false);
        assertEquals(10, page2.size(), "Second page via cursor has 10 persons");

        var firstOfPage2 = page2.get(0);
        var backToPage1 = personService().getPage(Page.with().range(firstOfPage2, 10, true).orderBy("id", true).build(), false);
        assertEquals(10, backToPage1.size(), "Reversed cursor returns 10 persons");
        assertEquals(page1.stream().map(Person::getId).toList(),
                     backToPage1.stream().map(Person::getId).toList(),
                     "Reversed cursor returns same persons as original page 1 in same order");
    }

    @Test
    void testCursorBasedPagingWithPhones() {
        var page1 = personService().getPageWithPhones(Page.with().range(0, 10).orderBy("id", true).build(), false);
        assertEquals(10, page1.size(), "First page has 10 persons with phones");

        var lastSeen = page1.get(page1.size() - 1);
        var page2 = personService().getPageWithPhones(Page.with().range(lastSeen, 10, false).orderBy("id", true).build(), false);
        assertEquals(10, page2.size(), "Second page has 10 persons with phones, not fewer due to join row inflation");

        assertTrue(Collections.disjoint(page1.stream().map(Person::getId).toList(), page2.stream().map(Person::getId).toList()), "Pages do not overlap");
        page2.forEach(p -> assertFalse(p.getPhones().isEmpty(), "Phones collection is populated after cursor-based paging"));
    }

    @Test
    void testCursorBasedPagingWithGroups() {
        var page1 = personService().getPageWithGroups(Page.with().range(0, 10).orderBy("id", true).build(), false);
        assertEquals(10, page1.size(), "First page has 10 persons with groups");

        var lastSeen = page1.get(page1.size() - 1);
        var page2 = personService().getPageWithGroups(Page.with().range(lastSeen, 10, false).orderBy("id", true).build(), false);
        assertEquals(10, page2.size(), "Second page has 10 persons with groups, not fewer due to join row inflation");

        assertTrue(Collections.disjoint(page1.stream().map(Person::getId).toList(), page2.stream().map(Person::getId).toList()), "Pages do not overlap");
        page2.forEach(p -> assertFalse(p.getGroups().isEmpty(), "Groups collection is populated after cursor-based paging"));
    }


    // @SoftDeletable -------------------------------------------------------------------------------------------------

    @Test
    void testSoftDelete() {
        var allTexts = textService().list();
        var allComments = commentService().list();

        var activeText = textService().getById(1L);
        textService().softDelete(activeText);
        var activeTextAfterSoftDelete = textService().getSoftDeletedById(1L);
        assertFalse(activeTextAfterSoftDelete.isActive(), "Text entity was soft deleted");
        assertEquals(allTexts.size() - 1, textService().list().size(), "Total records for texts");
        assertEquals(1, textService().listSoftDeleted().size(), "Total deleted records for texts");

        var activeComment = commentService().getById(1L);
        commentService().softDelete(activeComment);
        var activeCommentAfterSoftDelete = commentService().getSoftDeletedById(1L);
        assertTrue(activeCommentAfterSoftDelete.isDeleted(), "Comment entity was soft deleted");
        assertEquals(allComments.size() - 1, commentService().list().size(), "Total records for comments");
        assertEquals(1, commentService().listSoftDeleted().size(), "Total deleted records for comments");

        var deletedText = textService().getSoftDeletedById(1L);
        textService().softUndelete(deletedText);
        var deletedTextAfterSoftUndelete = textService().getById(1L);
        assertTrue(deletedTextAfterSoftUndelete.isActive(), "Text entity was soft undeleted");
        assertEquals(allTexts.size(), textService().list().size(), "Total records for texts");
        assertEquals(0, textService().listSoftDeleted().size(), "Total deleted records for texts");

        var deletedComment = commentService().getSoftDeletedById(1L);
        commentService().softUndelete(deletedComment);
        var deletedCommentAfterSoftUndelete = commentService().getById(1L);
        assertFalse(deletedCommentAfterSoftUndelete.isDeleted(), "Comment entity was soft undeleted");
        assertEquals(allComments.size(), commentService().list().size(), "Total records for comments");
        assertEquals(0, commentService().listSoftDeleted().size(), "Total deleted records for comments");

        textService().softDelete(allTexts);
        assertEquals(0, textService().list().size(), "Total records for texts");
        assertEquals(allTexts.size(), textService().listSoftDeleted().size(), "Total deleted records for texts");

        commentService().softDelete(allComments);
        assertEquals(0, commentService().list().size(), "Total records for comments");
        assertEquals(allComments.size(), commentService().listSoftDeleted().size(), "Total deleted records for comments");
    }

    @Test
    void testSoftUndelete() {
        var lookup = new Lookup("su");
        lookupService().persist(lookup);

        lookupService().softDelete(lookup);
        assertNull(lookupService().getById("su"), "Soft deleted lookup not found by getById");

        var softDeleted = lookupService().getSoftDeletedById("su");
        assertNotNull(softDeleted, "Soft deleted lookup found by getSoftDeletedById");
        lookupService().softUndelete(softDeleted);

        var restored = lookupService().getById("su");
        assertNotNull(restored, "Undeleted lookup found by getById");
        assertTrue(restored.isActive(), "Undeleted lookup is active");
    }

    @Test
    void testSoftDeleteBatch() {
        var lookup1 = new Lookup("b1");
        var lookup2 = new Lookup("b2");
        lookupService().persist(lookup1);
        lookupService().persist(lookup2);
        var totalBefore = lookupService().list().size();

        lookupService().softDelete(List.of(lookup1, lookup2));
        assertEquals(totalBefore - 2, lookupService().list().size(), "Two less active records after batch soft delete");

        var deleted = lookupService().listSoftDeleted();
        assertTrue(deleted.stream().anyMatch(l -> "b1".equals(l.getId())), "b1 is in soft deleted list");
        assertTrue(deleted.stream().anyMatch(l -> "b2".equals(l.getId())), "b2 is in soft deleted list");

        lookupService().softUndelete(List.of(
            lookupService().getSoftDeletedById("b1"),
            lookupService().getSoftDeletedById("b2")
        ));
        assertEquals(totalBefore, lookupService().list().size(), "Records restored after batch undelete");
    }

    @Test
    void testGetAllSoftDeletedForNonSoftDeletable() {
        assertThrows(NonSoftDeletableEntityException.class, () -> personService().listSoftDeleted());
    }

    @Test
    void testSoftDeleteNonSoftDeletable() {
        var person = personService().getById(1L);
        assertThrows(NonSoftDeletableEntityException.class, () -> personService().softDelete(person));
    }

    @Test
    void testSoftUndeleteNonSoftDeletable() {
        var person = personService().getById(1L);
        assertThrows(NonSoftDeletableEntityException.class, () -> personService().softUndelete(person));
    }

    @Test
    void testGetSoftDeletableById() {
        lookupService().persist(new Lookup("aa"));
        var activeLookup = lookupService().getById("aa");
        assertNotNull(activeLookup, "Got active entity with getById method");

        lookupService().softDelete(activeLookup);
        var softDeletedLookup = lookupService().getById("aa");
        assertNull(softDeletedLookup, "Not able to get deleted entity with getById method");

        softDeletedLookup = lookupService().getSoftDeletedById("aa");
        assertNotNull(softDeletedLookup, "Got deleted entity with getSoftDeletedById method");
    }

    @Test
    void testFindSoftDeletableById() {
        lookupService().persist(new Lookup("bb"));
        var activeLookup = lookupService().findById("bb");
        assertTrue(activeLookup.isPresent(), "Got active entity with findById method");

        lookupService().softDelete(activeLookup.get());
        var softDeletedLookup = lookupService().findById("bb");
        assertFalse(softDeletedLookup.isPresent(), "Not able to get deleted entity with findById method");

        softDeletedLookup = lookupService().findSoftDeletedById("bb");
        assertTrue(softDeletedLookup.isPresent(), "Got deleted entity with findSoftDeletedById method");
    }

    @Test
    void testSave() {
        var lookup = new Lookup("cc");
        lookupService().save(lookup);
        var persistedLookup = lookupService().getById("cc");
        assertNotNull(persistedLookup, "New entity was persisted with save method");

        persistedLookup.setActive(false);
        lookupService().save(persistedLookup);
        persistedLookup = lookupService().getSoftDeletedById("cc");
        assertFalse(persistedLookup.isActive(), "Entity was merged with save method");

        persistedLookup.setActive(true);
        lookupService().update(persistedLookup);
        persistedLookup = lookupService().getById("cc");
        assertTrue(persistedLookup.isActive(), "Entity was merged with update method");
    }

    @Test
    void testPersistExistingLookup() {
        var lookup = new Lookup("dd");
        lookupService().save(lookup);
        var persistedLookup = lookupService().getById("dd");
        persistedLookup.setActive(false);
        assertThrows(IllegalEntityStateException.class, () -> lookupService().persist(lookup));
    }

    @Test
    void testUpdateNewLookup() {
        var lookup = new Lookup("ee");
        assertThrows(IllegalEntityStateException.class, () -> lookupService().update(lookup));
    }


    // @NonDeletable --------------------------------------------------------------------------------------------------

    @Test
    void testNonDeletableCanBePersisted() {
        var config = new Config();
        config.setKey("test.key");
        config.setValue("test.value");
        configService().persist(config);
        assertNotNull(config.getId(), "Config entity was persisted");

        var persisted = configService().getById(config.getId());
        assertNotNull(persisted, "Config entity found by ID");
        assertEquals("test.key", persisted.getKey(), "Key is correct");
        assertEquals("test.value", persisted.getValue(), "Value is correct");
    }

    @Test
    void testNonDeletableCanBeUpdated() {
        var config = new Config();
        config.setKey("update.key");
        config.setValue("old.value");
        configService().persist(config);

        config.setValue("new.value");
        configService().update(config);

        var updated = configService().getById(config.getId());
        assertEquals("new.value", updated.getValue(), "Value was updated");
    }

    @Test
    void testNonDeletableCannotBeDeleted() {
        var config = new Config();
        config.setKey("nodelete.key");
        config.setValue("nodelete.value");
        configService().persist(config);

        assertThrows(NonDeletableEntityException.class, () -> configService().delete(config));

        var stillExists = configService().getById(config.getId());
        assertNotNull(stillExists, "Entity still exists after failed delete");
    }


    // getDatabase() / getProvider() ----------------------------------------------------------------------------------

    @Test
    void testDatabaseIs() {
        assertTrue(configService().isDatabaseH2(), "Test database is H2");
    }

    @Test
    void testProviderIs() {
        if (isEclipseLink()) {
            assertFalse(configService().isProviderHibernate(), "Provider is not Hibernate");
            assertTrue(configService().isProviderEclipseLink(), "Provider is EclipseLink");
            assertFalse(configService().isProviderOpenJPA(), "Provider is not OpenJPA");
        }
        else if (isOpenJPA()) {
            assertFalse(configService().isProviderHibernate(), "Provider is not Hibernate");
            assertFalse(configService().isProviderEclipseLink(), "Provider is not EclipseLink");
            assertTrue(configService().isProviderOpenJPA(), "Provider is OpenJPA");
        }
        else {
            assertTrue(configService().isProviderHibernate(), "Provider is Hibernate");
            assertFalse(configService().isProviderEclipseLink(), "Provider is not EclipseLink");
            assertFalse(configService().isProviderOpenJPA(), "Provider is not OpenJPA");
        }
    }


    // @Audit ---------------------------------------------------------------------------------------------------------

    @Test
    void testAuditTracksValueChange() {
        TestAuditListener.clearChanges();

        var config = new Config();
        config.setKey("audit.key");
        config.setValue("original");
        configService().persist(config);

        configService().updateValue(config.getId(), "modified");

        var changes = TestAuditListener.getChanges();
        assertFalse(changes.isEmpty(), "Audit changes were recorded");

        var valueChange = changes.stream()
            .filter(c -> "value".equals(c.getPropertyName()))
            .findFirst();
        assertTrue(valueChange.isPresent(), "Value change was audited");
        assertEquals("original", valueChange.get().getOldValue(), "Old value is correct");
        assertEquals("modified", valueChange.get().getNewValue(), "New value is correct");
    }

    @Test
    void testAuditDoesNotTrackNonAuditedField() {
        TestAuditListener.clearChanges();

        var config = new Config();
        config.setKey("audit.notrack");
        config.setValue("stable");
        configService().persist(config);

        configService().updateKey(config.getId(), "audit.notrack.changed");

        var keyChanges = TestAuditListener.getChanges().stream()
            .filter(c -> "key".equals(c.getPropertyName()))
            .toList();
        assertTrue(keyChanges.isEmpty(), "Non-audited field 'key' should not produce audit changes");
    }

    @Test
    void testAuditDoesNotTrackUnchangedValue() {
        TestAuditListener.clearChanges();

        var config = new Config();
        config.setKey("audit.same");
        config.setValue("unchanged");
        configService().persist(config);

        configService().updateValue(config.getId(), "unchanged");

        var valueChanges = TestAuditListener.getChanges().stream()
            .filter(c -> "value".equals(c.getPropertyName()))
            .toList();
        assertTrue(valueChanges.isEmpty(), "Unchanged value should not produce audit changes");
    }
}
