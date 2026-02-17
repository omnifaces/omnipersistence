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
import static org.omnifaces.persistence.test.service.StartupServiceEJB.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupServiceEJB.TOTAL_RECORDS;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

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
import org.omnifaces.persistence.test.service.CommentServiceCDI;
import org.omnifaces.persistence.test.service.ConfigServiceCDI;
import org.omnifaces.persistence.test.service.LookupServiceCDI;
import org.omnifaces.persistence.test.service.PersonServiceCDI;
import org.omnifaces.persistence.test.service.PhoneServiceCDI;
import org.omnifaces.persistence.test.service.StartupServiceEJB;
import org.omnifaces.persistence.test.service.TestAuditListener;
import org.omnifaces.persistence.test.service.TextServiceCDI;

@ExtendWith(ArquillianExtension.class)
public class OmniPersistenceCDIIT {

    @Deployment
    public static WebArchive createDeployment() {
        var maven = Maven.resolver();
        return create(WebArchive.class)
            .addPackages(true, OmniPersistenceCDIIT.class.getPackage())
            .deleteClass(StartupServiceEJB.class)
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

    @Inject
    private PersonServiceCDI personServiceCDI;

    @Inject
    private PhoneServiceCDI phoneServiceCDI;

    @Inject
    private TextServiceCDI textServiceCDI;

    @Inject
    private CommentServiceCDI commentServiceCDI;

    @Inject
    private LookupServiceCDI lookupServiceCDI;

    @Inject
    private ConfigServiceCDI configServiceCDI;

    protected static boolean isEclipseLink() {
        return getenv("MAVEN_CMD_LINE_ARGS").endsWith("-eclipselink");
    }


    // Basic operations with CDI --------------------------------------------------------------------------------------

    @Test
    void testFindPersonCDI() {
        var existingPerson = personServiceCDI.findById(1L);
        assertTrue(existingPerson.isPresent(), "Existing person");
        var nonExistingPerson = personServiceCDI.findById(0L);
        assertFalse(nonExistingPerson.isPresent(), "Non-existing person");
    }

    @Test
    void testGetPersonCDI() {
        var existingPerson = personServiceCDI.getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var nonExistingPerson = personServiceCDI.getById(0L);
        assertNull(nonExistingPerson, "Non-existing person");
    }

    @Test
    void testPersistAndDeleteNewPersonCDI() {
        var newPerson = createNewPerson("testPersistNewPerson@example.com");
        personServiceCDI.persist(newPerson);
        Long expectedNewId = TOTAL_RECORDS + 1L;
        assertEquals(expectedNewId, newPerson.getId(), "New person ID");
        assertEquals(TOTAL_RECORDS + 1, personServiceCDI.list().size(), "Total records");

        personServiceCDI.delete(newPerson);
        assertEquals(TOTAL_RECORDS, personServiceCDI.list().size(), "Total records");
    }

    @Test
    void testPersistExistingPersonCDI() {
        var existingPerson = createNewPerson("testPersistExistingPerson@example.com");
        existingPerson.setId(1L);
        assertThrows(IllegalEntityStateException.class, () -> personServiceCDI.persist(existingPerson));
    }

    @Test
    void testUpdateExistingPersonCDI() {
        var existingPerson = personServiceCDI.getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var newEmail = "testUpdateExistingPerson@example.com";
        existingPerson.setEmail(newEmail);
        personServiceCDI.update(existingPerson);
        var existingPersonAfterUpdate = personServiceCDI.getById(1L);
        assertEquals(newEmail, existingPersonAfterUpdate.getEmail(), "Email updated");
    }

    @Test
    void testUpdateNewPersonCDI() {
        var newPerson = createNewPerson("testUpdateNewPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personServiceCDI.update(newPerson));
    }

    @Test
    void testResetExistingPersonCDI() {
        var existingPerson = personServiceCDI.getById(1L);
        assertNotNull(existingPerson, "Existing person");
        var oldEmail = existingPerson.getEmail();
        existingPerson.setEmail("testResetExistingPerson@example.com");
        personServiceCDI.reset(existingPerson);
        assertEquals(oldEmail, existingPerson.getEmail(), "Email resetted");
    }

    @Test
    void testResetNonExistingPersonCDI() {
        var nonExistingPerson = createNewPerson("testResetNonExistingPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personServiceCDI.reset(nonExistingPerson));
    }

    @Test
    void testDeleteNonExistingPersonCDI() {
        var nonExistingPerson = createNewPerson("testDeleteNonExistingPerson@example.com");
        assertThrows(IllegalEntityStateException.class, () -> personServiceCDI.delete(nonExistingPerson));
    }

    private static Person createNewPerson(String email) {
        var person = new Person();
        person.setEmail(email);
        person.setGender(Gender.OTHER);
        person.setDateOfBirth(LocalDate.now());
        return person;
    }


    // Batch operations with CDI --------------------------------------------------------------------------------------

    @Test
    void testGetByIdsCDI() {
        var persons = personServiceCDI.getByIds(List.of(1L, 2L, 3L));
        assertEquals(3, persons.size(), "Should find 3 persons by IDs");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(1L)), "Contains person 1");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(2L)), "Contains person 2");
        assertTrue(persons.stream().anyMatch(p -> p.getId().equals(3L)), "Contains person 3");
    }

    @Test
    void testGetByIdsWithNonExistingCDI() {
        var persons = personServiceCDI.getByIds(List.of(1L, 999999L));
        assertEquals(1, persons.size(), "Should find only 1 existing person");
        assertEquals(1L, persons.get(0).getId(), "Found person has correct ID");
    }

    @Test
    void testGetByIdsEmptyCDI() {
        var persons = personServiceCDI.getByIds(List.of());
        assertTrue(persons.isEmpty(), "Empty IDs should return empty list");
    }


    // Page with CDI --------------------------------------------------------------------------------------------------

    @Test
    void testPageCDI() {
        var persons = personServiceCDI.getPage(Page.ALL, true);
        assertEquals(TOTAL_RECORDS, persons.size(), "There are 200 records");

        var males = personServiceCDI.getPage(Page.with().anyMatch(Collections.singletonMap("gender", Gender.MALE)).build(), true);
        assertTrue(males.size() < TOTAL_RECORDS, "There are less than 200 records");
    }

    @Test
    void testPageByLazyManyToOneCDI() { // This was failing since Hibernate 6 upgrade.
        var person = personServiceCDI.getById(1L);
        var phones = phoneServiceCDI.getPage(Page.with().allMatch(Map.of("owner", person)).build(), true);
        assertEquals(TOTAL_PHONES_PER_PERSON_0, phones.size(), "There are 3 phones");
    }

    @Test
    void testPageWithOffsetAndLimitCDI() {
        var firstPage = personServiceCDI.getPage(Page.of(0, 10), true);
        assertEquals(10, firstPage.size(), "First page has 10 records");
        assertEquals(TOTAL_RECORDS, firstPage.getEstimatedTotalNumberOfResults(), "Total count is correct");

        var secondPage = personServiceCDI.getPage(Page.of(10, 10), true);
        assertEquals(10, secondPage.size(), "Second page has 10 records");

        assertFalse(firstPage.get(0).getId().equals(secondPage.get(0).getId()), "Pages contain different records");
    }

    @Test
    void testPageWithoutCountCDI() {
        var page = personServiceCDI.getPage(Page.of(0, 10), false);
        assertEquals(10, page.size(), "Page has 10 records");
        assertEquals(-1, page.getEstimatedTotalNumberOfResults(), "Count is unknown when not requested");
    }

    @Test
    void testPageWithOrderingCDI() {
        var ascPage = personServiceCDI.getPage(Page.with().range(0, 10).orderBy("id", true).build(), false);
        var descPage = personServiceCDI.getPage(Page.with().range(0, 10).orderBy("id", false).build(), false);
        assertTrue(ascPage.get(0).getId() < ascPage.get(9).getId(), "Ascending order");
        assertTrue(descPage.get(0).getId() > descPage.get(9).getId(), "Descending order");
    }

    @Test
    void testPageOneCDI() {
        var result = personServiceCDI.getPage(Page.ONE, false);
        assertEquals(1, result.size(), "Page.ONE returns exactly 1 record");
    }

    @Test
    void testPageWithMultipleRequiredCriteriaCDI() {
        var person1 = personServiceCDI.getById(1L);
        var criteria = Map.<String, Object>of(
            "gender", person1.getGender(),
            "email", IgnoreCase.value(person1.getEmail())
        );
        var result = personServiceCDI.getPage(Page.with().allMatch(criteria).build(), true);
        assertEquals(1, result.size(), "Exact match by gender and email should return 1 record");
        assertEquals(person1.getId(), result.get(0).getId(), "Found the correct person");
    }

    @Test
    void testPageWithMultipleOptionalCriteriaCDI() {
        var person1 = personServiceCDI.getById(1L);
        var person2 = personServiceCDI.getById(2L);
        var criteria = Map.<String, Object>of(
            "email", person1.getEmail(),
            "gender", person2.getGender()
        );
        var result = personServiceCDI.getPage(Page.with().anyMatch(criteria).build(), true);
        assertTrue(result.size() >= 1, "At least one match by email or gender");
    }


    // Page with criteria types with CDI ------------------------------------------------------------------------------

    @Test
    void testPageWithLikeContainsCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("email", Like.contains("e99@e"))).build(), true);
        assertEquals(1, result.size(), "LIKE contains matches e99@e");
        assertTrue(result.get(0).getEmail().contains("name99@"), "Email contains name99@");
    }

    @Test
    void testPageWithLikeStartsWithCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("email", Like.startsWith("name1@"))).build(), true);
        assertTrue(result.size() >= 1, "LIKE starts with matches at least name1@");
        result.forEach(p -> assertTrue("name1@example.com".equals(p.getEmail()), "Email is name1@example.com"));
    }

    @Test
    void testPageWithLikeEndsWithCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("email", Like.endsWith("@example.com"))).build(), true);
        assertEquals(TOTAL_RECORDS, result.size(), "All records end with @example.com");
    }

    @Test
    void testPageWithLikeNoMatchCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("email", Like.contains("nonexistent_xyz"))).build(), true);
        assertEquals(0, result.size(), "No records match nonexistent search");
    }

    @Test
    void testPageWithIgnoreCaseCDI() {
        var person = personServiceCDI.getById(1L);
        var uppercaseEmail = person.getEmail().toUpperCase();
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("email", IgnoreCase.value(uppercaseEmail))).build(), true);
        assertEquals(1, result.size(), "Case insensitive exact match finds the record");
        assertEquals(person.getId(), result.get(0).getId(), "Found the correct person");
    }

    @Test
    void testPageWithEnumCriteriaCDI() {
        var maleResult = personServiceCDI.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.MALE))).build(), true);
        var femaleResult = personServiceCDI.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.FEMALE))).build(), true);
        assertTrue(maleResult.size() > 0, "Some males exist");
        assertTrue(femaleResult.size() > 0, "Some females exist");
        assertEquals(TOTAL_RECORDS, maleResult.size() + femaleResult.size()
            + personServiceCDI.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.TRANS))).build(), true).size()
            + personServiceCDI.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.OTHER))).build(), true).size(),
            "All genders sum up to total");
    }

    @Test
    void testPageWithNumericCriteriaCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("id", Numeric.value(1))).build(), true);
        assertEquals(1, result.size(), "Numeric match finds exactly 1 record");
        assertEquals(1L, result.get(0).getId(), "Found person with ID 1");
    }

    @Test
    void testPageWithOrderGreaterThanCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("id", Order.greaterThan(TOTAL_RECORDS - 5L))).build(), true);
        assertEquals(5, result.size(), "IDs greater than 195 should be 196-200");
        result.forEach(p -> assertTrue(p.getId() > TOTAL_RECORDS - 5L, "ID is greater than threshold"));
    }

    @Test
    void testPageWithOrderLessThanOrEqualToCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("id", Order.lessThanOrEqualTo(5L))).build(), true);
        assertEquals(5, result.size(), "IDs <= 5 should be 1-5");
        result.forEach(p -> assertTrue(p.getId() <= 5L, "ID is <= 5"));
    }

    @Test
    void testPageWithBetweenCDI() {
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("id", Between.range(10L, 19L))).build(), true);
        assertEquals(10, result.size(), "IDs between 10 and 19 should be 10 records");
        result.forEach(p -> {
            assertTrue(p.getId() >= 10L, "ID >= 10");
            assertTrue(p.getId() <= 19L, "ID <= 19");
        });
    }

    @Test
    void testPageWithBetweenDatesCDI() {
        var start = LocalDate.of(1950, 1, 1);
        var end = LocalDate.of(1960, 12, 31);
        var result = personServiceCDI.getPage(Page.with().allMatch(Map.of("dateOfBirth", Between.range(start, end))).build(), true);
        result.forEach(p -> {
            assertFalse(p.getDateOfBirth().isBefore(start), "Date of birth is not before start");
            assertFalse(p.getDateOfBirth().isAfter(end), "Date of birth is not after end");
        });
    }

    @Test
    void testPageWithNotCriteriaCDI() {
        var allMales = personServiceCDI.getPage(Page.with().allMatch(Map.of("gender", Enumerated.value(Gender.MALE))).build(), true);
        var notMales = personServiceCDI.getPage(Page.with().allMatch(Map.of("gender", Not.value(Gender.MALE))).build(), true);
        assertEquals(TOTAL_RECORDS, allMales.size() + notMales.size(), "Males + not-males = total");
    }


    // Page with fetch fields (PersonService/PhoneService custom methods) with CDI ------------------------------------

    @Test
    void testPageWithAddressCDI() {
        var result = personServiceCDI.getAllWithAddress();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        result.forEach(p -> assertNotNull(p.getAddress(), "Address is fetched"));
        result.forEach(p -> assertNotNull(p.getAddress().getStreet(), "Address street is accessible"));
    }

    @Test
    void testPageWithPhonesCDI() {
        var result = personServiceCDI.getAllWithPhones();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        var person0 = result.stream().filter(p -> p.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals(TOTAL_PHONES_PER_PERSON_0, person0.getPhones().size(), "Person 1 has expected phones");
    }

    @Test
    void testPageWithGroupsCDI() {
        var result = personServiceCDI.getAllWithGroups();
        assertEquals(TOTAL_RECORDS, result.size(), "All persons returned");
        result.forEach(p -> assertFalse(p.getGroups().isEmpty(), "Each person has at least one group"));
    }

    @Test
    void testPhonePageWithOwnersCDI() {
        var result = phoneServiceCDI.getAllWithOwners();
        assertFalse(result.isEmpty(), "Phones exist");
        result.forEach(p -> assertNotNull(p.getOwner(), "Owner is fetched"));
        result.forEach(p -> assertNotNull(p.getOwner().getEmail(), "Owner email is accessible"));
    }


    // Page with DTO mapping with CDI ---------------------------------------------------------------------------------

    @Test
    void testPageOfPersonCardsCDI() {
        var result = personServiceCDI.getAllPersonCards();
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
    void testPageOfPersonCardsWithPaginationCDI() {
        var page = personServiceCDI.getPageOfPersonCards(Page.of(0, 5), true);
        assertEquals(5, page.size(), "Page returns 5 cards");
        assertEquals(TOTAL_RECORDS, page.getEstimatedTotalNumberOfResults(), "Total count is correct");
    }


    // @SoftDeletable with CDI ----------------------------------------------------------------------------------------

    @Test
    void testSoftDeleteCDI() {
        var allTexts = textServiceCDI.list();
        var allComments = commentServiceCDI.list();

        var activeText = textServiceCDI.getById(1L);
        textServiceCDI.softDelete(activeText);
        var activeTextAfterSoftDelete = textServiceCDI.getSoftDeletedById(1L);
        assertFalse(activeTextAfterSoftDelete.isActive(), "Text entity was soft deleted");
        assertEquals(allTexts.size() - 1, textServiceCDI.list().size(), "Total records for texts");
        assertEquals(1, textServiceCDI.listSoftDeleted().size(), "Total deleted records for texts");

        var activeComment = commentServiceCDI.getById(1L);
        commentServiceCDI.softDelete(activeComment);
        var activeCommentAfterSoftDelete = commentServiceCDI.getSoftDeletedById(1L);
        assertTrue(activeCommentAfterSoftDelete.isDeleted(), "Comment entity was soft deleted");
        assertEquals(allComments.size() - 1, commentServiceCDI.list().size(), "Total records for comments");
        assertEquals(1, commentServiceCDI.listSoftDeleted().size(), "Total deleted records for comments");

        var deletedText = textServiceCDI.getSoftDeletedById(1L);
        textServiceCDI.softUndelete(deletedText);
        var deletedTextAfterSoftUndelete = textServiceCDI.getById(1L);
        assertTrue(deletedTextAfterSoftUndelete.isActive(), "Text entity was soft undeleted");
        assertEquals(allTexts.size(), textServiceCDI.list().size(), "Total records for texts");
        assertEquals(0, textServiceCDI.listSoftDeleted().size(), "Total deleted records for texts");

        var deletedComment = commentServiceCDI.getSoftDeletedById(1L);
        commentServiceCDI.softUndelete(deletedComment);
        var deletedCommentAfterSoftUndelete = commentServiceCDI.getById(1L);
        assertFalse(deletedCommentAfterSoftUndelete.isDeleted(), "Comment entity was soft undeleted");
        assertEquals(allComments.size(), commentServiceCDI.list().size(), "Total records for comments");
        assertEquals(0, commentServiceCDI.listSoftDeleted().size(), "Total deleted records for comments");

        textServiceCDI.softDelete(allTexts);
        assertEquals(0, textServiceCDI.list().size(), "Total records for texts");
        assertEquals(allTexts.size(), textServiceCDI.listSoftDeleted().size(), "Total deleted records for texts");

        commentServiceCDI.softDelete(allComments);
        assertEquals(0, commentServiceCDI.list().size(), "Total records for comments");
        assertEquals(allComments.size(), commentServiceCDI.listSoftDeleted().size(), "Total deleted records for comments");
    }

    @Test
    void testSoftUndeleteCDI() {
        var lookup = new Lookup("su");
        lookupServiceCDI.persist(lookup);

        lookupServiceCDI.softDelete(lookup);
        assertNull(lookupServiceCDI.getById("su"), "Soft deleted lookup not found by getById");

        var softDeleted = lookupServiceCDI.getSoftDeletedById("su");
        assertNotNull(softDeleted, "Soft deleted lookup found by getSoftDeletedById");
        lookupServiceCDI.softUndelete(softDeleted);

        var restored = lookupServiceCDI.getById("su");
        assertNotNull(restored, "Undeleted lookup found by getById");
        assertTrue(restored.isActive(), "Undeleted lookup is active");
    }

    @Test
    void testSoftDeleteBatchCDI() {
        var lookup1 = new Lookup("b1");
        var lookup2 = new Lookup("b2");
        lookupServiceCDI.persist(lookup1);
        lookupServiceCDI.persist(lookup2);
        var totalBefore = lookupServiceCDI.list().size();

        lookupServiceCDI.softDelete(List.of(lookup1, lookup2));
        assertEquals(totalBefore - 2, lookupServiceCDI.list().size(), "Two less active records after batch soft delete");

        var deleted = lookupServiceCDI.listSoftDeleted();
        assertTrue(deleted.stream().anyMatch(l -> "b1".equals(l.getId())), "b1 is in soft deleted list");
        assertTrue(deleted.stream().anyMatch(l -> "b2".equals(l.getId())), "b2 is in soft deleted list");

        lookupServiceCDI.softUndelete(List.of(
            lookupServiceCDI.getSoftDeletedById("b1"),
            lookupServiceCDI.getSoftDeletedById("b2")
        ));
        assertEquals(totalBefore, lookupServiceCDI.list().size(), "Records restored after batch undelete");
    }

    @Test
    void testGetAllSoftDeletedForNonSoftDeletableCDI() {
        assertThrows(NonSoftDeletableEntityException.class, () -> personServiceCDI.listSoftDeleted());
    }

    @Test
    void testSoftDeleteNonSoftDeletableCDI() {
        var person = personServiceCDI.getById(1L);
        assertThrows(NonSoftDeletableEntityException.class, () -> personServiceCDI.softDelete(person));
    }

    @Test
    void testSoftUndeleteNonSoftDeletableCDI() {
        var person = personServiceCDI.getById(1L);
        assertThrows(NonSoftDeletableEntityException.class, () -> personServiceCDI.softUndelete(person));
    }

    @Test
    void testGetSoftDeletableByIdCDI() {
        lookupServiceCDI.persist(new Lookup("aa"));
        var activeLookup = lookupServiceCDI.getById("aa");
        assertNotNull(activeLookup, "Got active entity with getById method");

        lookupServiceCDI.softDelete(activeLookup);
        var softDeletedLookup = lookupServiceCDI.getById("aa");
        assertNull(softDeletedLookup, "Not able to get deleted entity with getById method");

        softDeletedLookup = lookupServiceCDI.getSoftDeletedById("aa");
        assertNotNull(softDeletedLookup, "Got deleted entity with getSoftDeletedById method");
    }

    @Test
    void testFindSoftDeletableByIdCDI() {
        lookupServiceCDI.persist(new Lookup("bb"));
        var activeLookup = lookupServiceCDI.findById("bb");
        assertTrue(activeLookup.isPresent(), "Got active entity with findById method");

        lookupServiceCDI.softDelete(activeLookup.get());
        var softDeletedLookup = lookupServiceCDI.findById("bb");
        assertFalse(softDeletedLookup.isPresent(), "Not able to get deleted entity with findById method");

        softDeletedLookup = lookupServiceCDI.findSoftDeletedById("bb");
        assertTrue(softDeletedLookup.isPresent(), "Got deleted entity with findSoftDeletedById method");
    }

    @Test
    void testSaveCDI() {
        var lookup = new Lookup("cc");
        lookupServiceCDI.save(lookup);
        var persistedLookup = lookupServiceCDI.getById("cc");
        assertNotNull(persistedLookup, "New entity was persisted with save method");

        persistedLookup.setActive(false);
        lookupServiceCDI.save(persistedLookup);
        persistedLookup = lookupServiceCDI.getSoftDeletedById("cc");
        assertFalse(persistedLookup.isActive(), "Entity was merged with save method");

        persistedLookup.setActive(true);
        lookupServiceCDI.update(persistedLookup);
        persistedLookup = lookupServiceCDI.getById("cc");
        assertTrue(persistedLookup.isActive(), "Entity was merged with update method");
    }

    @Test
    void testPersistExistingLookupCDI() {
        var lookup = new Lookup("dd");
        lookupServiceCDI.save(lookup);
        var persistedLookup = lookupServiceCDI.getById("dd");
        persistedLookup.setActive(false);
        assertThrows(IllegalEntityStateException.class, () -> lookupServiceCDI.persist(lookup));
    }

    @Test
    void testUpdateNewLookupCDI() {
        var lookup = new Lookup("ee");
        assertThrows(IllegalEntityStateException.class, () -> lookupServiceCDI.update(lookup));
    }


    // @NonDeletable with CDI -----------------------------------------------------------------------------------------

    @Test
    void testNonDeletableCanBePersistedCDI() {
        var config = new Config();
        config.setKey("test.key");
        config.setValue("test.value");
        configServiceCDI.persist(config);
        assertNotNull(config.getId(), "Config entity was persisted");

        var persisted = configServiceCDI.getById(config.getId());
        assertNotNull(persisted, "Config entity found by ID");
        assertEquals("test.key", persisted.getKey(), "Key is correct");
        assertEquals("test.value", persisted.getValue(), "Value is correct");
    }

    @Test
    void testNonDeletableCanBeUpdatedCDI() {
        var config = new Config();
        config.setKey("update.key");
        config.setValue("old.value");
        configServiceCDI.persist(config);

        config.setValue("new.value");
        configServiceCDI.update(config);

        var updated = configServiceCDI.getById(config.getId());
        assertEquals("new.value", updated.getValue(), "Value was updated");
    }

    @Test
    void testNonDeletableCannotBeDeletedCDI() {
        var config = new Config();
        config.setKey("nodelete.key");
        config.setValue("nodelete.value");
        configServiceCDI.persist(config);

        assertThrows(NonDeletableEntityException.class, () -> configServiceCDI.delete(config));

        var stillExists = configServiceCDI.getById(config.getId());
        assertNotNull(stillExists, "Entity still exists after failed delete");
    }


    // getDatabase() / getProvider with EJB ---------------------------------------------------------------------------

    @Test
    void testDatabaseIsCDI() {
        assertTrue(configServiceCDI.isDatabaseH2(), "Test database is H2");
    }

    @Test
    void testProviderIsCDI() {
        if (isEclipseLink()) {
            assertTrue(configServiceCDI.isProviderEclipseLink(), "Provider is EclipseLink");
            assertFalse(configServiceCDI.isProviderHibernate(), "Provider is not Hibernate");
        }
        else {
            assertTrue(configServiceCDI.isProviderHibernate(), "Provider is Hibernate");
            assertFalse(configServiceCDI.isProviderEclipseLink(), "Provider is not EclipseLink");
        }
    }


    // @Audit with EJB ------------------------------------------------------------------------------------------------

    @Test
    void testAuditTracksValueChangeCDI() {
        TestAuditListener.clearChanges();

        var config = new Config();
        config.setKey("audit.key");
        config.setValue("original");
        configServiceCDI.persist(config);

        configServiceCDI.updateValue(config.getId(), "modified");

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
    void testAuditDoesNotTrackNonAuditedFieldCDI() {
        TestAuditListener.clearChanges();

        var config = new Config();
        config.setKey("audit.notrack");
        config.setValue("stable");
        configServiceCDI.persist(config);

        configServiceCDI.updateKey(config.getId(), "audit.notrack.changed");

        var keyChanges = TestAuditListener.getChanges().stream()
            .filter(c -> "key".equals(c.getPropertyName()))
            .toList();
        assertTrue(keyChanges.isEmpty(), "Non-audited field 'key' should not produce audit changes");
    }

    @Test
    void testAuditDoesNotTrackUnchangedValueCDI() {
        TestAuditListener.clearChanges();

        var config = new Config();
        config.setKey("audit.same");
        config.setValue("unchanged");
        configServiceCDI.persist(config);

        configServiceCDI.updateValue(config.getId(), "unchanged");

        var valueChanges = TestAuditListener.getChanges().stream()
            .filter(c -> "value".equals(c.getPropertyName()))
            .toList();
        assertTrue(valueChanges.isEmpty(), "Unchanged value should not produce audit changes");
    }
}
