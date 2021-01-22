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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.omnifaces.persistence.test.service.StartupService.TOTAL_RECORDS;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.ejb.EJB;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;
import org.junit.Test;
import org.junit.runner.RunWith;
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

@RunWith(Arquillian.class)
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
		assertTrue("Existing person", existingPerson.isPresent());
		Optional<Person> nonExistingPerson = personService.findById(0L);
		assertTrue("Non-existing person", !nonExistingPerson.isPresent());
	}

	@Test
	public void testGetPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue("Existing person", existingPerson != null);
		Person nonExistingPerson = personService.getById(0L);
		assertTrue("Non-existing person", nonExistingPerson == null);
	}

	@Test
	public void testPersistAndDeleteNewPerson() {
		Person newPerson = createNewPerson("testPersistNewPerson@example.com");
		personService.persist(newPerson);
		Long expectedNewId = TOTAL_RECORDS + 1L;
		assertEquals("New person ID", expectedNewId, newPerson.getId());
		assertEquals("Total records", TOTAL_RECORDS + 1, personService.list().size());

		personService.delete(newPerson);
		assertEquals("Total records", TOTAL_RECORDS, personService.list().size());
	}

	@Test(expected = IllegalEntityStateException.class)
	public void testPersistExistingPerson() {
		Person existingPerson = createNewPerson("testPersistExistingPerson@example.com");
		existingPerson.setId(1L);
		personService.persist(existingPerson);
	}

	@Test
	public void testUpdateExistingPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue("Existing person", existingPerson != null);
		String newEmail = "testUpdateExistingPerson@example.com";
		existingPerson.setEmail(newEmail);
		personService.update(existingPerson);
		Person existingPersonAfterUpdate = personService.getById(1L);
		assertEquals("Email updated", newEmail, existingPersonAfterUpdate.getEmail());
	}

	@Test(expected = IllegalEntityStateException.class)
	public void testUpdateNewPerson() {
		Person newPerson = createNewPerson("testUpdateNewPerson@example.com");
		personService.update(newPerson);
	}

	@Test
	public void testResetExistingPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue("Existing person", existingPerson != null);
		String oldEmail = existingPerson.getEmail();
		existingPerson.setEmail("testResetExistingPerson@example.com");
		personService.reset(existingPerson);
		assertEquals("Email resetted", oldEmail, existingPerson.getEmail());
	}

	@Test(expected = IllegalEntityStateException.class)
	public void testResetNonExistingPerson() {
		Person nonExistingPerson = createNewPerson("testResetNonExistingPerson@example.com");
		personService.reset(nonExistingPerson);
	}

	@Test(expected = IllegalEntityStateException.class)
	public void testDeleteNonExistingPerson() {
		Person nonExistingPerson = createNewPerson("testDeleteNonExistingPerson@example.com");
		personService.delete(nonExistingPerson);
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
		assertEquals("There are 200 records", TOTAL_RECORDS, persons.size());

		PartialResultList<Person> males = personService.getPage(Page.with().anyMatch(Collections.singletonMap("gender", Gender.MALE)).build(), true);
		assertTrue("There are less than 200 records", males.size() < TOTAL_RECORDS);
	}

	// @SoftDeletable -------------------------------------------------------------------------------------------------

	@Test
	public void testSoftDelete() {
		List<Text> allTexts = textService.list();
		List<Comment> allComments = commentService.list();

		Text activeText = textService.getById(1L);
		textService.softDelete(activeText);
		Text activeTextAfterSoftDelete = textService.getSoftDeletedById(1L);
		assertTrue("Text entity was soft deleted", !activeTextAfterSoftDelete.isActive());
		assertEquals("Total records for texts", textService.list().size(), allTexts.size() - 1);
		assertEquals("Total deleted records for texts", textService.listSoftDeleted().size(), 1);

		Comment activeComment = commentService.getById(1L);
		commentService.softDelete(activeComment);
		Comment activeCommentAfterSoftDelete = commentService.getSoftDeletedById(1L);
		assertTrue("Comment entity was soft deleted", activeCommentAfterSoftDelete.isDeleted());
		assertEquals("Total records for comments", commentService.list().size(), allComments.size() - 1);
		assertEquals("Total deleted records for comments", commentService.listSoftDeleted().size(), 1);

		Text deletedText = textService.getSoftDeletedById(1L);
		textService.softUndelete(deletedText);
		Text deletedTextAfterSoftUndelete = textService.getById(1L);
		assertTrue("Text entity was soft undeleted", deletedTextAfterSoftUndelete.isActive());
		assertEquals("Total records for texts", textService.list().size(), allTexts.size());
		assertEquals("Total deleted records for texts", textService.listSoftDeleted().size(), 0);

		Comment deletedComment = commentService.getSoftDeletedById(1L);
		commentService.softUndelete(deletedComment);
		Comment deletedCommentAfterSoftUndelete = commentService.getById(1L);
		assertTrue("Comment entity was soft undeleted", !deletedCommentAfterSoftUndelete.isDeleted());
		assertEquals("Total records for comments", commentService.list().size(), allComments.size());
		assertEquals("Total deleted records for comments", commentService.listSoftDeleted().size(), 0);

		textService.softDelete(allTexts);
		assertEquals("Total records for texts", textService.list().size(), 0);
		assertEquals("Total deleted records for texts", textService.listSoftDeleted().size(), allTexts.size());

		commentService.softDelete(allComments);
		assertEquals("Total records for comments", commentService.list().size(), 0);
		assertEquals("Total deleted records for comments", commentService.listSoftDeleted().size(), allComments.size());
	}

	@Test(expected = NonSoftDeletableEntityException.class)
	public void testGetAllSoftDeletedForNonSoftDeletable() {
		personService.listSoftDeleted();
	}

	@Test(expected = NonSoftDeletableEntityException.class)
	public void testSoftDeleteNonSoftDeletable() {
		Person person = personService.getById(1L);
		personService.softDelete(person);
	}

	@Test(expected = NonSoftDeletableEntityException.class)
	public void testSoftUndeleteNonSoftDeletable() {
		Person person = personService.getById(1L);
		personService.softUndelete(person);
	}

	@Test
	public void testGetSoftDeletableById() {
		lookupService.persist(new Lookup("aa"));
		Lookup activeLookup = lookupService.getById("aa");
		assertTrue("Got active entity with getById method", activeLookup != null);

		lookupService.softDelete(activeLookup);
		Lookup softDeletedLookup = lookupService.getById("aa");
		assertTrue("Not able to get deleted entity with getById method", softDeletedLookup == null);

		softDeletedLookup = lookupService.getSoftDeletedById("aa");
		assertTrue("Got deleted entity with getSoftDeletedById method", softDeletedLookup != null);
	}

	@Test
	public void testFindSoftDeletableById() {
		lookupService.persist(new Lookup("bb"));
		Optional<Lookup> activeLookup = lookupService.findById("bb");
		assertTrue("Got active entity with findById method", activeLookup.isPresent());

		lookupService.softDelete(activeLookup.get());
		Optional<Lookup> softDeletedLookup = lookupService.findById("bb");
		assertTrue("Not able to get deleted entity with findById method", !softDeletedLookup.isPresent());

		softDeletedLookup = lookupService.findSoftDeletedById("bb");
		assertTrue("Got deleted entity with findSoftDeletedById method", softDeletedLookup.isPresent());
	}

	@Test
	public void testSave() {
		Lookup lookup = new Lookup("cc");
		lookupService.save(lookup);
		Lookup persistedLookup = lookupService.getById("cc");
		assertTrue("New entity was persisted with save method", persistedLookup != null);

		persistedLookup.setActive(false);
		lookupService.save(persistedLookup);
		persistedLookup = lookupService.getSoftDeletedById("cc");
		assertTrue("Entity was merged with save method", persistedLookup != null && !persistedLookup.isActive());

		persistedLookup.setActive(true);
		lookupService.update(persistedLookup);
		persistedLookup = lookupService.getById("cc");
		assertTrue("Entity was merged with update method", persistedLookup != null && persistedLookup.isActive());
	}

	@Test(expected = IllegalEntityStateException.class)
	public void testPersistExistingLookup() {
		Lookup lookup = new Lookup("dd");
		lookupService.save(lookup);
		Lookup persistedLookup = lookupService.getById("dd");
		persistedLookup.setActive(false);
		lookupService.persist(lookup);
	}

	@Test(expected = IllegalEntityStateException.class)
	public void testUpdateNewLookup() {
		Lookup lookup = new Lookup("ee");
		lookupService.update(lookup);
	}

}