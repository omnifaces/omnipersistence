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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.service.StartupService.TOTAL_RECORDS;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.ejb.EJB;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.CommentService;
import org.omnifaces.persistence.test.service.LookupService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.TextService;
import org.omnifaces.utils.collection.PartialResultList;

@ExtendWith(ArquillianExtension.class)
public class OmniPersistenceTest {

	@Deployment
	public static WebArchive createDeployment() {
		MavenResolverSystem maven = Maven.resolver();
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
	public void testFindPerson() {
		Optional<Person> existingPerson = personService.findById(1L);
		assertTrue(existingPerson.isPresent(), "Existing person");
		Optional<Person> nonExistingPerson = personService.findById(0L);
		assertTrue(!nonExistingPerson.isPresent(), "Non-existing person");
	}

	@Test
	public void testGetPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue(existingPerson != null, "Existing person");
		Person nonExistingPerson = personService.getById(0L);
		assertTrue(nonExistingPerson == null, "Non-existing person");
	}

	@Test
	public void testPersistAndDeleteNewPerson() {
		Person newPerson = createNewPerson("testPersistNewPerson@example.com");
		personService.persist(newPerson);
		Long expectedNewId = TOTAL_RECORDS + 1L;
		assertEquals(expectedNewId, newPerson.getId(), "New person ID");
		assertEquals(TOTAL_RECORDS + 1, personService.list().size(), "Total records");

		personService.delete(newPerson);
		assertEquals(TOTAL_RECORDS, personService.list().size(), "Total records");
	}

	@Test
	public void testPersistExistingPerson() {
		Person existingPerson = createNewPerson("testPersistExistingPerson@example.com");
		existingPerson.setId(1L);
		assertThrows(IllegalEntityStateException.class, () -> personService.persist(existingPerson));
	}

	@Test
	public void testUpdateExistingPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue(existingPerson != null, "Existing person");
		String newEmail = "testUpdateExistingPerson@example.com";
		existingPerson.setEmail(newEmail);
		personService.update(existingPerson);
		Person existingPersonAfterUpdate = personService.getById(1L);
		assertEquals(newEmail, existingPersonAfterUpdate.getEmail(), "Email updated");
	}

	@Test
	public void testUpdateNewPerson() {
		Person newPerson = createNewPerson("testUpdateNewPerson@example.com");
		assertThrows(IllegalEntityStateException.class, () -> personService.update(newPerson));
	}

	@Test
	public void testResetExistingPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue(existingPerson != null, "Existing person");
		String oldEmail = existingPerson.getEmail();
		existingPerson.setEmail("testResetExistingPerson@example.com");
		personService.reset(existingPerson);
		assertEquals(oldEmail, existingPerson.getEmail(), "Email resetted");
	}

	@Test
	public void testResetNonExistingPerson() {
		Person nonExistingPerson = createNewPerson("testResetNonExistingPerson@example.com");
		assertThrows(IllegalEntityStateException.class, () -> personService.reset(nonExistingPerson));
	}

	@Test
	public void testDeleteNonExistingPerson() {
		Person nonExistingPerson = createNewPerson("testDeleteNonExistingPerson@example.com");
		assertThrows(IllegalEntityStateException.class, () -> personService.delete(nonExistingPerson));
	}

	private static Person createNewPerson(String email) {
		Person person = new Person();
		person.setEmail(email);
		person.setGender(Gender.OTHER);
		person.setDateOfBirth(LocalDate.now());
		return person;
	}


	// Page -----------------------------------------------------------------------------------------------------------

	@Test
	public void testPage() {
		PartialResultList<Person> persons = personService.getPage(Page.ALL, true);
		assertEquals(TOTAL_RECORDS, persons.size(), "There are 200 records");

		PartialResultList<Person> males = personService.getPage(Page.with().anyMatch(Collections.singletonMap("gender", Gender.MALE)).build(), true);
		assertTrue(males.size() < TOTAL_RECORDS, "There are less than 200 records");
	}

	// @SoftDeletable -------------------------------------------------------------------------------------------------

	@Test
	public void testSoftDelete() {
		List<Text> allTexts = textService.list();
		List<Comment> allComments = commentService.list();

		Text activeText = textService.getById(1L);
		textService.softDelete(activeText);
		Text activeTextAfterSoftDelete = textService.getSoftDeletedById(1L);
		assertTrue(!activeTextAfterSoftDelete.isActive(), "Text entity was soft deleted");
		assertEquals(textService.list().size(), allTexts.size() - 1, "Total records for texts");
		assertEquals(textService.listSoftDeleted().size(), 1, "Total deleted records for texts");

		Comment activeComment = commentService.getById(1L);
		commentService.softDelete(activeComment);
		Comment activeCommentAfterSoftDelete = commentService.getSoftDeletedById(1L);
		assertTrue(activeCommentAfterSoftDelete.isDeleted(), "Comment entity was soft deleted");
		assertEquals(commentService.list().size(), allComments.size() - 1, "Total records for comments");
		assertEquals(commentService.listSoftDeleted().size(), 1, "Total deleted records for comments");

		Text deletedText = textService.getSoftDeletedById(1L);
		textService.softUndelete(deletedText);
		Text deletedTextAfterSoftUndelete = textService.getById(1L);
		assertTrue(deletedTextAfterSoftUndelete.isActive(), "Text entity was soft undeleted");
		assertEquals(textService.list().size(), allTexts.size(), "Total records for texts");
		assertEquals(textService.listSoftDeleted().size(), 0, "Total deleted records for texts");

		Comment deletedComment = commentService.getSoftDeletedById(1L);
		commentService.softUndelete(deletedComment);
		Comment deletedCommentAfterSoftUndelete = commentService.getById(1L);
		assertTrue(!deletedCommentAfterSoftUndelete.isDeleted(), "Comment entity was soft undeleted");
		assertEquals(commentService.list().size(), allComments.size(), "Total records for comments");
		assertEquals(commentService.listSoftDeleted().size(), 0, "Total deleted records for comments");

		textService.softDelete(allTexts);
		assertEquals(textService.list().size(), 0, "Total records for texts");
		assertEquals(textService.listSoftDeleted().size(), allTexts.size(), "Total deleted records for texts");

		commentService.softDelete(allComments);
		assertEquals(commentService.list().size(), 0, "Total records for comments");
		assertEquals(commentService.listSoftDeleted().size(), allComments.size(), "Total deleted records for comments");
	}

	@Test
	public void testGetAllSoftDeletedForNonSoftDeletable() {
		assertThrows(NonSoftDeletableEntityException.class, () -> personService.listSoftDeleted());
	}

	@Test
	public void testSoftDeleteNonSoftDeletable() {
		Person person = personService.getById(1L);
		assertThrows(NonSoftDeletableEntityException.class, () -> personService.softDelete(person));
	}

	@Test
	public void testSoftUndeleteNonSoftDeletable() {
		Person person = personService.getById(1L);
		assertThrows(NonSoftDeletableEntityException.class, () -> personService.softUndelete(person));
	}

	@Test
	public void testGetSoftDeletableById() {
		lookupService.persist(new Lookup("aa"));
		Lookup activeLookup = lookupService.getById("aa");
		assertTrue(activeLookup != null, "Got active entity with getById method");

		lookupService.softDelete(activeLookup);
		Lookup softDeletedLookup = lookupService.getById("aa");
		assertTrue(softDeletedLookup == null, "Not able to get deleted entity with getById method");

		softDeletedLookup = lookupService.getSoftDeletedById("aa");
		assertTrue(softDeletedLookup != null, "Got deleted entity with getSoftDeletedById method");
	}

	@Test
	public void testFindSoftDeletableById() {
		lookupService.persist(new Lookup("bb"));
		Optional<Lookup> activeLookup = lookupService.findById("bb");
		assertTrue(activeLookup.isPresent(), "Got active entity with findById method");

		lookupService.softDelete(activeLookup.get());
		Optional<Lookup> softDeletedLookup = lookupService.findById("bb");
		assertTrue(!softDeletedLookup.isPresent(), "Not able to get deleted entity with findById method");

		softDeletedLookup = lookupService.findSoftDeletedById("bb");
		assertTrue(softDeletedLookup.isPresent(), "Got deleted entity with findSoftDeletedById method");
	}

	@Test
	public void testSave() {
		Lookup lookup = new Lookup("cc");
		lookupService.save(lookup);
		Lookup persistedLookup = lookupService.getById("cc");
		assertTrue(persistedLookup != null, "New entity was persisted with save method");

		persistedLookup.setActive(false);
		lookupService.save(persistedLookup);
		persistedLookup = lookupService.getSoftDeletedById("cc");
		assertTrue(persistedLookup != null && !persistedLookup.isActive(), "Entity was merged with save method");

		persistedLookup.setActive(true);
		lookupService.update(persistedLookup);
		persistedLookup = lookupService.getById("cc");
		assertTrue(persistedLookup != null && persistedLookup.isActive(), "Entity was merged with update method");
	}

	@Test
	public void testPersistExistingLookup() {
		Lookup lookup = new Lookup("dd");
		lookupService.save(lookup);
		Lookup persistedLookup = lookupService.getById("dd");
		persistedLookup.setActive(false);
		assertThrows(IllegalEntityStateException.class, () -> lookupService.persist(lookup));
	}

	@Test
	public void testUpdateNewLookup() {
		Lookup lookup = new Lookup("ee");
		assertThrows(IllegalEntityStateException.class, () -> lookupService.update(lookup));
	}

}