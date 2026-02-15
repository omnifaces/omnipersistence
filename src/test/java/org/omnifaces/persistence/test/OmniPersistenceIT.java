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
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.service.StartupService.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupService.TOTAL_RECORDS;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ejb.EJB;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
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
import org.omnifaces.persistence.test.model.Config;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.service.CommentService;
import org.omnifaces.persistence.test.service.ConfigService;
import org.omnifaces.persistence.test.service.LookupService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.TextService;

@ExtendWith(ArquillianExtension.class)
public class OmniPersistenceIT {

    @Deployment
    public static WebArchive createDeployment() {
        var maven = Maven.resolver();
        return create(WebArchive.class)
            .addPackages(true, OmniPersistenceIT.class.getPackage())
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
            .addAsWebInfResource("web.xml")
            .addAsResource("META-INF/persistence.xml")
            .addAsResource("META-INF/sql/create-test.sql")
            .addAsResource("META-INF/sql/drop-test.sql")
            .addAsResource("META-INF/sql/load-test.sql")
            .addAsLibrary(create(MavenImporter.class).loadPomFromFile("pom.xml").importBuildOutput().as(JavaArchive.class))
            .addAsLibraries(maven.loadPomFromFile("pom.xml").importCompileAndRuntimeDependencies().resolve().withTransitivity().asFile())
            .addAsLibraries(maven.resolve("com.h2database:h2:" + getProperty("test.h2.version")).withTransitivity().asFile());
    }

    @EJB
    private PersonService personService;

    @EJB
    private PhoneService phoneService;

    @EJB
    private TextService textService;

    @EJB
    private CommentService commentService;

    @EJB
    private LookupService lookupService;

    @EJB
    private ConfigService configService;

    protected static boolean isEclipseLink() {
        return getenv("MAVEN_CMD_LINE_ARGS").endsWith("-eclipselink");
    }

    // Basic ----------------------------------------------------------------------------------------------------------

    @Test
    void testFindPerson() {
        var existingPerson = personService.findById(1L);
        assertTrue(existingPerson.isPresent(), "Existing person");
        var nonExistingPerson = personService.findById(0L);
        assertFalse(nonExistingPerson.isPresent(), "Non-existing person");
    }

    @Test
    void testGetPerson() {
        var existingPerson = personService.getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var nonExistingPerson = personService.getById(0L);
        assertNull(nonExistingPerson, "Non-existing person");
    }

    @Test
    void testPersistAndDeleteNewPerson() {
        var newPerson = createNewPerson("testPersistNewPerson@example.com");
        personService.persist(newPerson);
        Long expectedNewId = TOTAL_RECORDS + 1L;
        assertEquals(expectedNewId, newPerson.getId(), "New person ID");
        assertEquals(TOTAL_RECORDS + 1, personService.list().size(), "Total records");

        personService.delete(newPerson);
        assertEquals(TOTAL_RECORDS, personService.list().size(), "Total records");
    }

    @Test
    void testPersistExistingPerson() {
        var existingPerson = createNewPerson("testPersistExistingPerson@example.com");
        existingPerson.setId(1L);
        assertThrows(IllegalEntityStateException.class, () -> personService.persist(existingPerson));
    }

    @Test
    void testUpdateExistingPerson() {
        var existingPerson = personService.getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var newEmail = "testUpdateExistingPerson@example.com";
        existingPerson.setEmail(newEmail);
        personService.update(existingPerson);
        var existingPersonAfterUpdate = personService.getById(1L);
        assertEquals(newEmail, existingPersonAfterUpdate.getEmail(), "Email updated");
    }

    @Test
    void testUpdateNewPerson() {
        var newPerson = createNewPerson("testUpdateNewPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personService.update(newPerson));
    }

    @Test
    void testResetExistingPerson() {
        var existingPerson = personService.getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var oldEmail = existingPerson.getEmail();
        existingPerson.setEmail("testResetExistingPerson@example.com");
        personService.reset(existingPerson);
        assertEquals(oldEmail, existingPerson.getEmail(), "Email resetted");
    }

    @Test
    void testResetNonExistingPerson() {
        var nonExistingPerson = createNewPerson("testResetNonExistingPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personService.reset(nonExistingPerson));
    }

    @Test
    void testDeleteNonExistingPerson() {
        var nonExistingPerson = createNewPerson("testDeleteNonExistingPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personService.delete(nonExistingPerson));
    }

    private static Person createNewPerson(String email) {
        var person = new Person();
        person.setEmail(email);
        person.setGender(Gender.OTHER);
        person.setDateOfBirth(LocalDate.now());
        return person;
    }


    // Batch operations -----------------------------------------------------------------------------------------------

    @Test
    void testGetByIds() {
        var persons = personService.getByIds(List.of(1L, 2L, 3L));
        assertEquals(3, persons.size(), "Should find 3 persons by IDs");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(1L)), "Contains person 1");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(2L)), "Contains person 2");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(3L)), "Contains person 3");
    }

    @Test
    void testGetByIdsWithNonExisting() {
        var persons = personService.getByIds(List.of(1L, 999999L));
        assertEquals(1, persons.size(), "Should find only 1 existing person");
        assertEquals(1L, persons.get(0).getId(), "Found person has correct ID");
    }

    @Test
    void testGetByIdsEmpty() {
        var persons = personService.getByIds(List.of());
        assertTrue(persons.isEmpty(), "Empty IDs should return empty list");
    }


    // Page -----------------------------------------------------------------------------------------------------------

    @Test
    void testPage() {
        var persons = personService.getPage(Page.ALL, true);
        assertEquals(TOTAL_RECORDS, persons.size(), "There are 200 records");

        var males = personService.getPage(Page.with().anyMatch(Collections.singletonMap("gender", Gender.MALE)).build(), true);
        assertTrue(males.size() < TOTAL_RECORDS, "There are less than 200 records");
    }

    @Test
    void testPageByLazyManyToOne() { // This was failing since Hibernate 6 upgrade.
        var person = personService.getById(1L);
        var phones = phoneService.getPage(Page.with().allMatch(Map.of("owner", person)).build(), true);
        assertEquals(TOTAL_PHONES_PER_PERSON_0, phones.size(), "There are 3 phones");
    }

    @Test
    void testPageWithOffsetAndLimit() {
        var firstPage = personService.getPage(Page.of(0, 10), true);
        assertEquals(10, firstPage.size(), "First page has 10 records");
        assertEquals(TOTAL_RECORDS, firstPage.getEstimatedTotalNumberOfResults(), "Total count is correct");

        var secondPage = personService.getPage(Page.of(10, 10), true);
        assertEquals(10, secondPage.size(), "Second page has 10 records");

        assertFalse(firstPage.get(0).getId().equals(secondPage.get(0).getId()), "Pages contain different records");
    }

    @Test
    void testPageWithoutCount() {
        var page = personService.getPage(Page.of(0, 10), false);
        assertEquals(10, page.size(), "Page has 10 records");
        assertEquals(-1, page.getEstimatedTotalNumberOfResults(), "Count is unknown when not requested");
    }

    @Test
    void testPageWithOrdering() {
        var ascPage = personService.getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        var descPage = personService.getPage(Page.with().range(0, 10).orderBy("id", false).build(), false);
        assertTrue(ascPage.get(0).getId() < ascPage.get(9).getId(), "Ascending order");
        assertTrue(descPage.get(0).getId() > descPage.get(9).getId(), "Descending order");
    }

    @Test
    void testPageOne() {
        var result = personService.getPage(Page.ONE, false);
        assertEquals(1, result.size(), "Page.ONE returns exactly 1 record");
    }

    @Test
    void testPageWithMultipleRequiredCriteria() {
        var person1 = personService.getById(1L);
        var criteria = Map.<String, Object>of(
            "gender", person1.getGender(),
            "email", IgnoreCase.value(person1.getEmail())
        );
        var result = personService.getPage(Page.with().allMatch(criteria).build(), true);
        assertEquals(1, result.size(), "Exact match by gender and email should return 1 record");
        assertEquals(person1.getId(), result.get(0).getId(), "Found the correct person");
    }

    @Test
    void testPageWithMultipleOptionalCriteria() {
        var person1 = personService.getById(1L);
        var person2 = personService.getById(2L);
        var criteria = Map.<String, Object>of(
            "email", person1.getEmail(),
            "gender", person2.getGender()
        );
        var result = personService.getPage(Page.with().anyMatch(criteria).build(), true);
        assertTrue(result.size() >= 1, "At least one match by email or gender");
    }


    // Page with criteria types ---------------------------------------------------------------------------------------

    @Test
    void testPageWithLikeContains() {
        var result = personService.getPage(Page.with().allMatch(Map.of("email", Like.contains("e99@e"))).build(), true);
        assertEquals(1, result.size(), "LIKE contains matches e99@e");
        assertTrue(result.get(0).getEmail().contains("name99@"), "Email contains name99@");
    }

    @Test
    void testPageWithLikeStartsWith() {
        var result = personService.getPage(Page.with().allMatch(Map.of("email", Like.startsWith("name1@"))).build(), true);
        assertTrue(result.size() >= 1, "LIKE starts with matches at least name1@");
        result.forEach(p -> assertTrue("name1@example.com".equals(p.getEmail()), "Email is name1@example.com"));
    }

    @Test
    void testPageWithLikeEndsWith() {
        var result = personService.getPage(Page.with().allMatch(Map.of("email", Like.endsWith("@example.com"))).build(), true);
        assertEquals(TOTAL_RECORDS, result.size(), "All records end with @example.com");
    }

    @Test
    void testPageWithLikeNoMatch() {
        var result = personService.getPage(Page.with().allMatch(Map.of("email", Like.contains("nonexistent_xyz"))).build(), true);
        assertEquals(0, result.size(), "No records match nonexistent search");
    }

    @Test
    void testPageWithIgnoreCase() {
        var person = personService.getById(1L);
        var uppercaseEmail = person.getEmail().toUpperCase();
        var result = personService.getPage(Page.with().allMatch(Map.of("email", IgnoreCase.value(uppercaseEmail))).build(), true);
        assertEquals(1, result.size(), "Case insensitive exact match finds the record");
        assertEquals(person.getId(), result.get(0).getId(), "Found the correct person");
    }

    @Test
    void testPageWithEnumCriteria() {
        var maleResult = personService.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.MALE))).build(), true);
        var femaleResult = personService.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.FEMALE))).build(), true);
        assertTrue(maleResult.size() > 0, "Some males exist");
        assertTrue(femaleResult.size() > 0, "Some females exist");
        assertEquals(TOTAL_RECORDS, maleResult.size() + femaleResult.size()
            + personService.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.TRANS))).build(), true).size()
            + personService.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.OTHER))).build(), true).size(),
            "All genders sum up to total");
    }

    @Test
    void testPageWithNumericCriteria() {
        var result = personService.getPage(Page.with().allMatch(Map.of("id", Numeric.value(1))).build(), true);
        assertEquals(1, result.size(), "Numeric match finds exactly 1 record");
        assertEquals(1L, result.get(0).getId(), "Found person with ID 1");
    }

    @Test
    void testPageWithOrderGreaterThan() {
        var result = personService.getPage(Page.with().allMatch(Map.of("id", Order.greaterThan(TOTAL_RECORDS - 5L))).build(), true);
        assertEquals(5, result.size(), "IDs greater than 195 should be 196-200");
        result.forEach(p -> assertTrue(p.getId() > TOTAL_RECORDS - 5L, "ID is greater than threshold"));
    }

    @Test
    void testPageWithOrderLessThanOrEqualTo() {
        var result = personService.getPage(Page.with().allMatch(Map.of("id", Order.lessThanOrEqualTo(5L))).build(), true);
        assertEquals(5, result.size(), "IDs <= 5 should be 1-5");
        result.forEach(p -> assertTrue(p.getId() <= 5L, "ID is <= 5"));
    }

    @Test
    void testPageWithBetween() {
        var result = personService.getPage(Page.with().allMatch(Map.of("id", Between.range(10L, 19L))).build(), true);
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
        var result = personService.getPage(Page.with().allMatch(Map.of("dateOfBirth", Between.range(start, end))).build(), true);
        result.forEach(p -> {
            assertFalse(p.getDateOfBirth().isBefore(start), "Date of birth is not before start");
            assertFalse(p.getDateOfBirth().isAfter(end), "Date of birth is not after end");
        });
    }

    @Test
    void testPageWithNotCriteria() {
        var allMales = personService.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.MALE))).build(), true);
        var notMales = personService.getPage(Page.with().allMatch(Map.of("gender", Not.value(Gender.MALE))).build(), true);
        assertEquals(TOTAL_RECORDS, allMales.size() + notMales.size(), "Males + not-males = total");
    }


    // Page with fetch fields (PersonService/PhoneService custom methods) ---------------------------------------------

    @Test
    void testPageWithAddress() {
        var result = personService.getAllWithAddress();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        result.forEach(p -> assertNotNull(p.getAddress(), "Address is fetched"));
        result.forEach(p -> assertNotNull(p.getAddress().getStreet(), "Address street is accessible"));
    }

    @Test
    void testPageWithPhones() {
        var result = personService.getAllWithPhones();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        var person0 = result.stream().filter(p -> p.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals(TOTAL_PHONES_PER_PERSON_0, person0.getPhones().size(), "Person 1 has expected phones");
    }

    @Test
    void testPageWithGroups() {
        var result = personService.getAllWithGroups();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        result.forEach(p -> assertFalse(p.getGroups().isEmpty(), "Each person has at least one group"));
    }

    @Test
    void testPhonePageWithOwners() {
        var result = phoneService.getAllWithOwners();
        assertFalse(result.isEmpty(), "Phones exist");
        result.forEach(p -> assertNotNull(p.getOwner(), "Owner is fetched"));
        result.forEach(p -> assertNotNull(p.getOwner().getEmail(), "Owner email is accessible"));
    }


    // Page with DTO mapping ------------------------------------------------------------------------------------------

    @Test
    void testPageOfPersonCards() {
        var result = personService.getAllPersonCards();
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
        var page = personService.getPageOfPersonCards(Page.of(0, 5), true);
        assertEquals(5, page.size(), "Page returns 5 cards");
        assertEquals(TOTAL_RECORDS, page.getEstimatedTotalNumberOfResults(), "Total count is correct");
    }


    // @SoftDeletable -------------------------------------------------------------------------------------------------

    @Test
    void testSoftDelete() {
        var allTexts = textService.list();
        var allComments = commentService.list();

        var activeText = textService.getById(1L);
        textService.softDelete(activeText);
        var activeTextAfterSoftDelete = textService.getSoftDeletedById(1L);
        assertFalse(activeTextAfterSoftDelete.isActive(), "Text entity was soft deleted");
        assertEquals(allTexts.size() - 1, textService.list().size(), "Total records for texts");
        assertEquals(1, textService.listSoftDeleted().size(), "Total deleted records for texts");

        var activeComment = commentService.getById(1L);
        commentService.softDelete(activeComment);
        var activeCommentAfterSoftDelete = commentService.getSoftDeletedById(1L);
        assertTrue(activeCommentAfterSoftDelete.isDeleted(), "Comment entity was soft deleted");
        assertEquals(allComments.size() - 1, commentService.list().size(), "Total records for comments");
        assertEquals(1, commentService.listSoftDeleted().size(), "Total deleted records for comments");

        var deletedText = textService.getSoftDeletedById(1L);
        textService.softUndelete(deletedText);
        var deletedTextAfterSoftUndelete = textService.getById(1L);
        assertTrue(deletedTextAfterSoftUndelete.isActive(), "Text entity was soft undeleted");
        assertEquals(allTexts.size(), textService.list().size(), "Total records for texts");
        assertEquals(0, textService.listSoftDeleted().size(), "Total deleted records for texts");

        var deletedComment = commentService.getSoftDeletedById(1L);
        commentService.softUndelete(deletedComment);
        var deletedCommentAfterSoftUndelete = commentService.getById(1L);
        assertFalse(deletedCommentAfterSoftUndelete.isDeleted(), "Comment entity was soft undeleted");
        assertEquals(allComments.size(), commentService.list().size(), "Total records for comments");
        assertEquals(0, commentService.listSoftDeleted().size(), "Total deleted records for comments");

        textService.softDelete(allTexts);
        assertEquals(0, textService.list().size(), "Total records for texts");
        assertEquals(allTexts.size(), textService.listSoftDeleted().size(), "Total deleted records for texts");

        commentService.softDelete(allComments);
        assertEquals(0, commentService.list().size(), "Total records for comments");
        assertEquals(allComments.size(), commentService.listSoftDeleted().size(), "Total deleted records for comments");
    }

    @Test
    void testSoftUndelete() {
        var lookup = new Lookup("su");
        lookupService.persist(lookup);

        lookupService.softDelete(lookup);
        assertNull(lookupService.getById("su"), "Soft deleted lookup not found by getById");

        var softDeleted = lookupService.getSoftDeletedById("su");
        assertNotNull(softDeleted, "Soft deleted lookup found by getSoftDeletedById");
        lookupService.softUndelete(softDeleted);

        var restored = lookupService.getById("su");
        assertNotNull(restored, "Undeleted lookup found by getById");
        assertTrue(restored.isActive(), "Undeleted lookup is active");
    }

    @Test
    void testSoftDeleteBatch() {
        var lookup1 = new Lookup("b1");
        var lookup2 = new Lookup("b2");
        lookupService.persist(lookup1);
        lookupService.persist(lookup2);
        var totalBefore = lookupService.list().size();

        lookupService.softDelete(List.of(lookup1, lookup2));
        assertEquals(totalBefore - 2, lookupService.list().size(), "Two less active records after batch soft delete");

        var deleted = lookupService.listSoftDeleted();
        assertTrue(deleted.stream().anyMatch(l -> "b1".equals(l.getId())), "b1 is in soft deleted list");
        assertTrue(deleted.stream().anyMatch(l -> "b2".equals(l.getId())), "b2 is in soft deleted list");

        lookupService.softUndelete(List.of(
            lookupService.getSoftDeletedById("b1"),
            lookupService.getSoftDeletedById("b2")
        ));
        assertEquals(totalBefore, lookupService.list().size(), "Records restored after batch undelete");
    }

    @Test
    void testGetAllSoftDeletedForNonSoftDeletable() {
        assertThrows(NonSoftDeletableEntityException.class, () -> personService.listSoftDeleted());
    }

    @Test
    void testSoftDeleteNonSoftDeletable() {
        var person = personService.getById(1L);
        assertThrows(NonSoftDeletableEntityException.class, () -> personService.softDelete(person));
    }

    @Test
    void testSoftUndeleteNonSoftDeletable() {
        var person = personService.getById(1L);
        assertThrows(NonSoftDeletableEntityException.class, () -> personService.softUndelete(person));
    }

    @Test
    void testGetSoftDeletableById() {
        lookupService.persist(new Lookup("aa"));
        var activeLookup = lookupService.getById("aa");
        assertNotNull(activeLookup, "Got active entity with getById method");

        lookupService.softDelete(activeLookup);
        var softDeletedLookup = lookupService.getById("aa");
        assertNull(softDeletedLookup, "Not able to get deleted entity with getById method");

        softDeletedLookup = lookupService.getSoftDeletedById("aa");
        assertNotNull(softDeletedLookup, "Got deleted entity with getSoftDeletedById method");
    }

    @Test
    void testFindSoftDeletableById() {
        lookupService.persist(new Lookup("bb"));
        var activeLookup = lookupService.findById("bb");
        assertTrue(activeLookup.isPresent(), "Got active entity with findById method");

        lookupService.softDelete(activeLookup.get());
        var softDeletedLookup = lookupService.findById("bb");
        assertFalse(softDeletedLookup.isPresent(), "Not able to get deleted entity with findById method");

        softDeletedLookup = lookupService.findSoftDeletedById("bb");
        assertTrue(softDeletedLookup.isPresent(), "Got deleted entity with findSoftDeletedById method");
    }

    @Test
    void testSave() {
        var lookup = new Lookup("cc");
        lookupService.save(lookup);
        var persistedLookup = lookupService.getById("cc");
        assertNotNull(persistedLookup, "New entity was persisted with save method");

        persistedLookup.setActive(false);
        lookupService.save(persistedLookup);
        persistedLookup = lookupService.getSoftDeletedById("cc");
        assertFalse(persistedLookup.isActive(), "Entity was merged with save method");

        persistedLookup.setActive(true);
        lookupService.update(persistedLookup);
        persistedLookup = lookupService.getById("cc");
        assertTrue(persistedLookup.isActive(), "Entity was merged with update method");
    }

    @Test
    void testPersistExistingLookup() {
        var lookup = new Lookup("dd");
        lookupService.save(lookup);
        var persistedLookup = lookupService.getById("dd");
        persistedLookup.setActive(false);
        assertThrows(IllegalEntityStateException.class, () -> lookupService.persist(lookup));
    }

    @Test
    void testUpdateNewLookup() {
        var lookup = new Lookup("ee");
        assertThrows(IllegalEntityStateException.class, () -> lookupService.update(lookup));
    }


    // @NonDeletable --------------------------------------------------------------------------------------------------

    @Test
    void testNonDeletableCanBePersisted() {
        var config = new Config();
        config.setKey("test.key");
        config.setValue("test.value");
        configService.persist(config);
        assertNotNull(config.getId(), "Config entity was persisted");

        var persisted = configService.getById(config.getId());
        assertNotNull(persisted, "Config entity found by ID");
        assertEquals("test.key", persisted.getKey(), "Key is correct");
        assertEquals("test.value", persisted.getValue(), "Value is correct");
    }

    @Test
    void testNonDeletableCanBeUpdated() {
        var config = new Config();
        config.setKey("update.key");
        config.setValue("old.value");
        configService.persist(config);

        config.setValue("new.value");
        configService.update(config);

        var updated = configService.getById(config.getId());
        assertEquals("new.value", updated.getValue(), "Value was updated");
    }

    @Test
    void testNonDeletableCannotBeDeleted() {
        var config = new Config();
        config.setKey("nodelete.key");
        config.setValue("nodelete.value");
        configService.persist(config);

        assertThrows(NonDeletableEntityException.class, () -> configService.delete(config));

        var stillExists = configService.getById(config.getId());
        assertNotNull(stillExists, "Entity still exists after failed delete");
    }

}
