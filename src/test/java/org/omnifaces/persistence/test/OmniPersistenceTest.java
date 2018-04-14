/*
 * Copyright 2018 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.test;

import static java.lang.System.getProperty;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.ejb.EJB;

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
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.CommentService;
import org.omnifaces.persistence.test.service.LookupService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.TextService;

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
			.addAsLibrary(create(MavenImporter.class).loadPomFromFile("pom.xml").importBuildOutput().as(JavaArchive.class))
			.addAsLibraries(maven.loadPomFromFile("pom.xml").importRuntimeDependencies().resolve().withTransitivity().asFile())
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
	public void testPersistNewPerson() {
		int totalRecordsBeforeInsert = personService.getAll().size();
		Person newPerson = createNewPerson("testPersistNewPerson@example.com");
		personService.persist(newPerson);
		assertEquals("New person ID", Long.valueOf(totalRecordsBeforeInsert + 1), newPerson.getId());
		assertEquals("Total records", totalRecordsBeforeInsert + 1, personService.getAll().size());
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

	@Test
	public void testDeleteExistingPerson() {
		int totalRecordsBeforeDelete = personService.getAll().size();
		long lastId = totalRecordsBeforeDelete;
		Person existingPerson = personService.getById(lastId);
		assertTrue("Existing person", existingPerson != null);
		assertTrue("Existing person ID before delete", existingPerson.getId() != null);
		personService.delete(existingPerson);
		assertTrue("Existing person ID after delete", existingPerson.getId() == null);
		Optional<Person> deletedPerson = personService.findById(lastId);
		assertTrue("Deleted person", !deletedPerson.isPresent());
		assertEquals("Total records", totalRecordsBeforeDelete - 1, personService.getAll().size());
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


	// @SoftDeletable -------------------------------------------------------------------------------------------------

	@Test
	public void testSoftDelete() {
		Text activeText = textService.getById(1L);
		textService.softDelete(activeText);
		Text activeTextAfterSoftDelete = textService.getSoftDeletedById(1L);
		assertTrue("Text entity was soft deleted", !activeTextAfterSoftDelete.isActive());

		Comment activeComment = commentService.getById(1L);
		commentService.softDelete(activeComment);
		Comment activeCommentAfterSoftDelete = commentService.getSoftDeletedById(1L);
		assertTrue("Comment entity was soft deleted", activeCommentAfterSoftDelete.isDeleted());

		Text deletedText = textService.getSoftDeletedById(1L);
		textService.softUndelete(deletedText);
		Text deletedTextAfterSoftUndelete = textService.getById(1L);
		assertTrue("Text entity was soft undeleted", deletedTextAfterSoftUndelete.isActive());

		Comment deletedComment = commentService.getSoftDeletedById(1L);
		commentService.softUndelete(deletedComment);
		Comment deletedCommentAfterSoftUndelete = commentService.getById(1L);
		assertTrue("Comment entity was soft undeleted", !deletedCommentAfterSoftUndelete.isDeleted());

		List<Text> allTexts = textService.getAll();
		textService.softDelete(allTexts);
		assertEquals("Total records for texts", textService.getAll().size(), 0);
		assertEquals("Total deleted records for texts", textService.getAllSoftDeleted().size(), allTexts.size());

		List<Comment> allComments = commentService.getAll();
		commentService.softDelete(allComments);
		assertEquals("Total records for comments", commentService.getAll().size(), 0);
		assertEquals("Total deleted records for comments", commentService.getAllSoftDeleted().size(), allComments.size());
	}

	@Test(expected = NonSoftDeletableEntityException.class)
	public void testGetAllSoftDeletedForNonSoftDeletable() {
		personService.getAllSoftDeleted();
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