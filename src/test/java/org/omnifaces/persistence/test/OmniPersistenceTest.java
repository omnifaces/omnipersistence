/*
 * Copyright 2021 OmniFaces
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
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.service.CommentService;
import org.omnifaces.persistence.test.service.LookupService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.TextService;

@ExtendWith(ArquillianExtension.class)
public class OmniPersistenceTest {

	@Deployment
	public static WebArchive createDeployment() {
		var maven = Maven.resolver();
		return create(WebArchive.class)
			.addPackages(true, OmniPersistenceTest.class.getPackage())
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

}