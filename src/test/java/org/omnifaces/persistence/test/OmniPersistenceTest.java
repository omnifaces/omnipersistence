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
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.service.PersonService;

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

	@Test
	public void testFindPerson() {
		Optional<Person> existingPerson = personService.findById(1L);
		assertTrue("Existing person",  existingPerson.isPresent());
		Optional<Person> nonExistingPerson = personService.findById(0L);
		assertTrue("Non-existing person", !nonExistingPerson.isPresent());
	}

	@Test
	public void testGetPerson() {
		Person existingPerson = personService.getById(1L);
		assertTrue("Existing person",  existingPerson != null);
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

}