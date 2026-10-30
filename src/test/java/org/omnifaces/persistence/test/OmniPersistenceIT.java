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
import static java.util.stream.Collectors.toSet;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

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
import org.omnifaces.persistence.criteria.Criteria;
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
import org.omnifaces.persistence.test.model.InventoryItem;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.ConfigService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.PlainConfigUpdater;
import org.omnifaces.persistence.test.service.TestAuditListener;
import org.omnifaces.utils.collection.PartialResultList;

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

    @Inject
    private PlainConfigUpdater plainConfigUpdater;

    protected abstract BaseEntityService<Long, InventoryItem> inventoryItemService();

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

    // Positional parameters ------------------------------------------------------------------------------------------

    /**
     * Positional parameters are 1-based: <code>?1</code> takes the first vararg. The ranges are asymmetric so that binding them in reverse yields no match
     * rather than the same match.
     */
    @Test
    void testListByPositionalParameters() {
        var persons = personService().listByIdRange(2L, 4L);
        assertEquals(List.of(2L, 3L, 4L), persons.stream().map(Person::getId).toList(), "Persons 2 until 4");
        assertTrue(personService().listByIdRange(4L, 2L).isEmpty(), "Reversed range matches nothing");
    }

    /**
     * Each vararg lands on its own position, so a query with two of them matches only when both hold.
     *
     * @see #testListByPositionalParameters()
     */
    @Test
    void testFindByPositionalParameters() {
        var person = personService().getById(2L);
        var foundPerson = personService().findByIdAndEmail(person.getId(), person.getEmail());
        assertTrue(foundPerson.isPresent(), "Person matching both ID and email");
        assertEquals(person.getId(), foundPerson.get().getId(), "Found person");
        assertFalse(personService().findByIdAndEmail(3L, person.getEmail()).isPresent(), "Mismatching ID and email");
    }

    /**
     * @see #testListByPositionalParameters()
     */
    @Test
    void testFindFirstByPositionalParameters() {
        var person = personService().findFirstByIdRange(2L, 4L);
        assertTrue(person.isPresent(), "First person of persons 2 until 4");
        assertEquals(2L, person.get().getId(), "Found person");
        assertFalse(personService().findFirstByIdRange(4L, 2L).isPresent(), "Reversed range matches nothing");
    }

    /**
     * Bulk update binds positionally as well, across both the SET and the WHERE clause. It runs on its own ID range so that it doesn't disturb the persons
     * asserted by the other positional parameter tests.
     *
     * @see #testListByPositionalParameters()
     */
    @Test
    void testUpdateByPositionalParameters() {
        var newEmail = "testUpdateByPositionalParameters@example.com";
        assertEquals(3, personService().updateEmailByIdRange(newEmail, 6L, 8L), "Updated persons 6 until 8");

        var persons = personService().listByIdRange(6L, 8L);
        assertEquals(List.of(newEmail, newEmail, newEmail), persons.stream().map(Person::getEmail).toList(), "Email updated");
    }

    /**
     * Bulk update is a non-SELECT query, so it must be created without an entity result type. This holds for mapped parameters just as much as for positional
     * ones, hence this counterpart on its own ID range.
     *
     * @see #testUpdateByPositionalParameters()
     */
    @Test
    void testUpdateByMappedParameters() {
        var newEmail = "testUpdateByMappedParameters@example.com";
        assertEquals(3, personService().updateEmailByMappedIdRange(newEmail, 10L, 12L), "Updated persons 10 until 12");

        var persons = personService().listByIdRange(10L, 12L);
        assertEquals(List.of(newEmail, newEmail, newEmail), persons.stream().map(Person::getEmail).toList(), "Email updated");
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
        assertEquals(
            TOTAL_RECORDS, maleResult.size() + femaleResult.size()
                + personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.TRANS))).build(), true).size()
                + personService().getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.OTHER))).build(), true).size(),
            "All genders sum up to total"
        );
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

    @Test
    void testPageWithNullCriteriaValues() {
        var isNullCriteria = new HashMap<String, Object>();
        isNullCriteria.put("gender", null);
        var isNull = personService().getPage(Page.with().allMatch(isNullCriteria).build(), true);
        var isNotNull = personService().getPage(Page.with().allMatch(Map.of("gender", Not.value(null))).build(), true);
        assertEquals(0, isNull.size(), "No records have null gender");
        assertEquals(TOTAL_RECORDS, isNotNull.size(), "All records have non-null gender");
    }

    @Test
    void testPageWithCriteriaBuildingNullPredicateImposesNoRestriction() {
        var result = personService().getPage(Page.with().allMatch(Map.of("email", new NoRestriction())).build(), true);
        assertEquals(TOTAL_RECORDS, result.size(), "All records are returned");
        assertEquals(TOTAL_RECORDS, result.getEstimatedTotalNumberOfResults(), "Total count is correct");
    }

    private static class NoRestriction extends Criteria<String> {

        private static final long serialVersionUID = 1L;

        NoRestriction() {
            super("");
        }

        @Override
        public Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
            return null;
        }

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
    void testElementCollectionFilterOnPathBackThroughRootEntity() {
        var direct = personService().getPage(Page.with().allMatch(Map.of("groups", Group.USER)).build(), true);
        var viaPhoneOwner = personService().getPage(Page.with().allMatch(Map.of("phones.owner.groups", Group.USER)).build(), true);
        assertFalse(direct.isEmpty(), "Some persons have group USER");
        assertEquals(
            direct.stream().map(Person::getId).collect(toSet()),
            viaPhoneOwner.stream().map(Person::getId).collect(toSet()),
            "Every person owns a phone, so filtering on phones.owner.groups matches the same persons as filtering on groups"
        );
        assertEquals(direct.getEstimatedTotalNumberOfResults(), viaPhoneOwner.getEstimatedTotalNumberOfResults(), "Counts match");
    }

    @Test
    void testPageWithGroupsAndRequiredElementCollectionFilter() {
        var firstPage = personService()
            .getPageWithGroups(Page.with().range(0, 10).allMatch(Map.of("groups", List.of(Group.USER, Group.DEVELOPER))).build(), true);
        assertEquals(10, firstPage.size(), "First page returns exactly 10 persons, not fewer due to join row inflation");
        firstPage.forEach(
            p -> assertTrue(p.getGroups().contains(Group.USER) && p.getGroups().contains(Group.DEVELOPER), "Groups collection is populated after detachment")
        );
    }

    @Test
    void testPageWithPhonesFilteredByTypeSet() {
        var result = personService().getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.type", Set.of(Phone.Type.MOBILE))).build(), true);
        assertFalse(result.isEmpty(), "Some persons have MOBILE phones");
        if (!isOpenJPA()) {
            assertTrue(result.getEstimatedTotalNumberOfResults() < TOTAL_RECORDS, "Not all persons have MOBILE phones"); // OpenJPA generates broken nested
                                                                                                                         // correlated subqueries for @OneToMany
                                                                                                                         // in count subquery context, so the
                                                                                                                         // count is inaccurate there.
        }
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Filtered person has phones"));
        if (isOpenJPA() || isEclipseLink()) { // Hibernate JOIN FETCH returns all phone types; in-memory filtering of postponed fetches only applies to
                                              // OpenJPA/EclipseLink.
            result.forEach(
                person -> person.getPhones().forEach(
                    phone -> assertEquals(Phone.Type.MOBILE, phone.getType(), "Only MOBILE phones remain after in-memory filtering of postponed fetch")
                )
            );
        }
    }

    @Test
    void testPageWithPhonesFilteredByNumberLike() {
        var result = personService().getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.number", Like.contains("11"))).build(), true);
        assertFalse(result.isEmpty(), "Some persons have phones with number containing 11");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Filtered person has phones"));
        result.forEach(
            person -> person.getPhones().forEach(
                phone -> assertTrue(phone.getNumber().contains("11"), "Only phones with 11 in number remain after in-memory filtering of postponed fetch")
            )
        );
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
        var result = personService()
            .getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.number", Like.contains("11"))).orderBy("phones.number", false).build(), true);
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
        assertEquals(
            page1.stream().map(Person::getId).toList(),
            backToPage1.stream().map(Person::getId).toList(),
            "Reversed cursor returns same persons as original page 1 in same order"
        );
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

    @Test
    void testCursorRangeFromEntityReadsOrderingValueThroughLazyAssociation() {
        var phone = phoneService().getAllWithOwners().get(0);
        var lastOrderingValues = phoneService().getLastOrderingValuesOfCursorRangeFrom(phone.getId(), "owner.email");
        assertEquals(Map.of("owner.email", phone.getOwner().getEmail()), lastOrderingValues, "Ordering value is read through the lazy owner");
    }

    @Test
    void testCursorRangeFromEntityReadsOrderingValueOfFieldWithoutGetterThroughLazyAssociation() {
        var phone = phoneService().getAllWithOwners().get(0);
        var lastOrderingValues = phoneService().getLastOrderingValuesOfCursorRangeFrom(phone.getId(), "owner.nickname");
        assertEquals(
            Map.of("owner.nickname", phone.getOwner().nickname()), lastOrderingValues, "Ordering value of field without getter is read through the lazy owner"
        );
    }

    @Test
    void testCursorBasedPagingWithOrderingValues() {
        var page1 = personService().getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        var lastSeenId = page1.get(page1.size() - 1).getId();
        var page2 = personService().getPage(Page.with().range(10, 10, Map.of("id", lastSeenId), false).orderBy("id", true).build(), false);
        var offsetPage2 = personService().getPage(Page.with().range(10, 10).orderBy("id", true).build(), false);

        assertEquals(
            offsetPage2.stream().map(Person::getId).toList(),
            page2.stream().map(Person::getId).toList(),
            "Cursor by ordering values returns same persons as offset based second page in same order"
        );
    }

    @Test
    void testCursorBasedPagingWithOrderingValuesReversed() {
        var page1 = personService().getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        var page2 = personService().getPage(Page.with().range(10, 10).orderBy("id", true).build(), false);
        var firstOfPage2Id = page2.get(0).getId();
        var backToPage1 = personService().getPage(Page.with().range(0, 10, Map.of("id", firstOfPage2Id), true).orderBy("id", true).build(), false);

        assertEquals(
            page1.stream().map(Person::getId).toList(),
            backToPage1.stream().map(Person::getId).toList(),
            "Reversed cursor by ordering values returns same persons as original page 1 in same order"
        );
    }

    @Test
    void testCursorBasedPagingWithOrderingValuesOfMultipleFields() {
        var page1 = personService().getPage(Page.with().range(0, 10).orderBy("dateOfBirth", true).orderBy("id", true).build(), false);
        var lastSeen = page1.get(page1.size() - 1);
        var lastOrderingValues = Map.<String, Object>of("dateOfBirth", lastSeen.getDateOfBirth(), "id", lastSeen.getId());
        var page2 = personService()
            .getPage(Page.with().range(10, 10, lastOrderingValues, false).orderBy("dateOfBirth", true).orderBy("id", true).build(), false);
        var offsetPage2 = personService().getPage(Page.with().range(10, 10).orderBy("dateOfBirth", true).orderBy("id", true).build(), false);

        assertEquals(
            offsetPage2.stream().map(Person::getId).toList(),
            page2.stream().map(Person::getId).toList(),
            "Cursor by ordering values of multiple fields returns same persons as offset based second page in same order"
        );
    }

    @Test
    void testOffsetPageIsOrderedWhenLimitExceedsOffsetByOne() {
        var page = personService().getPage(Page.of(9, 10), false);
        var allIds = personService().getPage(Page.ALL, false).stream().map(Person::getId).toList();
        assertEquals(allIds.subList(9, 19), page.stream().map(Person::getId).toList(), "Page is ordered by default ordering");
    }

    @Test
    void testPageOfOneIsOrderedByGivenOrdering() {
        var page = personService().getPage(Page.with().range(0, 1).orderBy("id", false).build(), false);
        var allIds = personService().getPage(Page.with().orderBy("id", false).build(), false).stream().map(Person::getId).toList();
        assertEquals(allIds.subList(0, 1), page.stream().map(Person::getId).toList(), "Page of one is ordered by given ordering");
    }

    @Test
    void testCursorBasedPagingWithOrderingValuesAndResidualOffset() {
        assertResidualOffsetPageEqualsOffsetPage(personService()::getPage, "Cursor by ordering values with residual offset returns same persons");
    }

    @Test
    void testCursorBasedPagingWithOrderingValuesAndResidualOffsetReversed() {
        var boundaryOffset = 27;
        var pageOffset = 10;
        var limit = 10;
        var residualOffset = boundaryOffset - (pageOffset + limit);
        var boundaryId = getIdOfPersonAtOffset(boundaryOffset);
        var page = personService()
            .getPage(Page.with().range(pageOffset, limit, Map.of("id", boundaryId), residualOffset, true).orderBy("id", true).build(), false);
        var offsetPage = personService().getPage(Page.with().range(pageOffset, limit).orderBy("id", true).build(), false);

        assertEquals(
            offsetPage.stream().map(Person::getId).toList(),
            page.stream().map(Person::getId).toList(),
            "Reversed cursor by ordering values with residual offset returns same persons as offset based page in same order"
        );
    }

    @Test
    void testCursorBasedPagingWithOrderingValuesAndResidualOffsetWithPhones() {
        assertResidualOffsetPageEqualsOffsetPage(
            personService()::getPageWithPhones, "Cursor by ordering values with residual offset returns same persons with phones"
        );
    }

    @Test
    void testOrderingValuesAndResidualOffsetFallBackToOffsetWhenValueBasedPagingIsNotApplicable() {
        var pageOffset = 20;
        var boundaryOffset = 12;
        var boundaryId = getIdOfPersonAtOffset(boundaryOffset);
        var page = personService().getPageViaQueryBuilder(Page.with().range(pageOffset, 10, Map.of("id", boundaryId), 0, false).orderBy("id", true).build());
        var offsetPage = personService().getPage(Page.with().range(pageOffset, 10).orderBy("id", true).build(), false);

        assertEquals(
            offsetPage.stream().map(Person::getId).toList(),
            page.stream().map(Person::getId).toList(),
            "Offset of the page is applied when value based paging is not applicable"
        );
    }

    private void assertResidualOffsetPageEqualsOffsetPage(BiFunction<Page, Boolean, PartialResultList<Person>> getPage, String message) {
        var boundaryOffset = 12;
        var pageOffset = 20;
        var residualOffset = pageOffset - boundaryOffset - 1;
        var boundaryId = getIdOfPersonAtOffset(boundaryOffset);
        var page = getPage.apply(Page.with().range(pageOffset, 10, Map.of("id", boundaryId), residualOffset, false).orderBy("id", true).build(), false);
        var offsetPage = getPage.apply(Page.with().range(pageOffset, 10).orderBy("id", true).build(), false);

        assertEquals(
            offsetPage.stream().map(Person::getId).toList(), page.stream().map(Person::getId).toList(), message + " as offset based page in same order"
        );
        assertEquals(pageOffset, page.getOffset(), "Result reports the offset of the page, not the residual offset");
    }

    private Long getIdOfPersonAtOffset(int offset) {
        return personService().getPage(Page.with().range(offset, 1).orderBy("id", true).build(), false).get(0).getId();
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

        lookupService().softUndelete(
            List.of(
                lookupService().getSoftDeletedById("b1"),
                lookupService().getSoftDeletedById("b2")
            )
        );
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

    // LockedBaseEntity -----------------------------------------------------------------------------------------------

    @Test
    void testLockedBaseEntityVersionIsInitializedOnPersist() {
        var item = persistNewInventoryItem();
        assertNotNull(inventoryItemService().getById(item.getId()).getVersion(), "Version after persist");
    }

    @Test
    void testLockedBaseEntityVersionIsIncrementedOnUpdate() {
        var item = inventoryItemService().getById(persistNewInventoryItem().getId());
        var initialVersion = item.getVersion();
        item.setQuantity(9);
        inventoryItemService().update(item);
        assertEquals(initialVersion + 1, inventoryItemService().getById(item.getId()).getVersion(), "Version after update");
    }

    @Test
    void testLockedBaseEntityRejectsStaleUpdate() {
        var id = persistNewInventoryItem().getId();
        var current = inventoryItemService().getById(id);
        var stale = inventoryItemService().getById(id);
        current.setQuantity(9);
        inventoryItemService().update(current);
        stale.setQuantity(8);
        var exception = assertThrows(Exception.class, () -> inventoryItemService().update(stale));
        assertTrue(
            Stream.<Throwable>iterate(exception, Objects::nonNull, Throwable::getCause).anyMatch(OptimisticLockException.class::isInstance),
            "Stale update is caused by OptimisticLockException"
        );
        assertEquals(9, inventoryItemService().getById(id).getQuantity(), "Quantity after stale update");
    }

    private InventoryItem persistNewInventoryItem() {
        var item = new InventoryItem();
        item.setQuantity(10);
        inventoryItemService().persist(item);
        return item;
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

    private Config persistAuditedConfig(String key, String value) {
        var config = new Config();
        config.setKey(key);
        config.setValue(value);
        configService().persist(config);
        TestAuditListener.clearChanges();
        return config;
    }

    private static List<String> auditedValueChanges() {
        return TestAuditListener.getChanges().stream()
            .filter(change -> "value".equals(change.getPropertyName()))
            .map(change -> change.getOldValue() + " -> " + change.getNewValue())
            .toList();
    }

    @Test
    void testAuditTracksChangesOfSameEntityInConcurrentPersistenceContexts() {
        var config = persistAuditedConfig("audit.concurrent", "original");
        configService().updateValueWhileConcurrentlyUpdated(config.getId(), "outer", () -> plainConfigUpdater.updateValue(config.getId(), "inner"));
        assertEquals(List.of("original -> inner", "original -> outer"), auditedValueChanges(), "Both changes were audited against their own snapshot");
    }

    @Test
    void testAuditTracksEachChangeOfSameEntityInSamePersistenceContext() {
        var config = persistAuditedConfig("audit.twice", "original");
        configService().updateValueTwice(config.getId(), "first", "second");
        assertEquals(List.of("original -> first", "first -> second"), auditedValueChanges(), "Each flushed change was audited");
    }

    @Test
    void testAuditTracksChangeOutsideService() {
        var config = persistAuditedConfig("audit.plain", "original");
        plainConfigUpdater.updateValue(config.getId(), "plain");
        assertEquals(List.of("original -> plain"), auditedValueChanges(), "Change via plain entity manager was audited");
    }

    @Test
    void testAuditTracksValueChange() {
        var config = persistAuditedConfig("audit.key", "original");
        configService().updateValue(config.getId(), "modified");
        assertEquals(List.of("original -> modified"), auditedValueChanges(), "Value change was audited");
    }

    @Test
    void testAuditTracksValueChangeOfDetachedEntity() {
        var config = persistAuditedConfig("audit.detached", "original");
        var detachedConfig = configService().getById(config.getId());
        detachedConfig.setValue("modified");
        configService().update(detachedConfig);
        assertEquals(List.of("original -> modified"), auditedValueChanges(), "Value change of detached entity was audited");
    }

    @Test
    void testAuditDoesNotTrackNonAuditedField() {
        var config = persistAuditedConfig("audit.notrack", "stable");
        configService().updateKey(config.getId(), "audit.notrack.changed");
        assertTrue(TestAuditListener.getChanges().isEmpty(), "Non-audited field 'key' should not produce audit changes");
    }

    @Test
    void testAuditDoesNotTrackUnchangedValue() {
        var config = persistAuditedConfig("audit.same", "unchanged");
        configService().updateValue(config.getId(), "unchanged");
        assertEquals(List.of(), auditedValueChanges(), "Unchanged value should not produce audit changes");
    }

}
