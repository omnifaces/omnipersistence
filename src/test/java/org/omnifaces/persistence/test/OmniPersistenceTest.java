/*
 * Copyright 2020 OmniFaces
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

import static java.lang.System.getenv;
import static java.util.Arrays.asList;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.omnifaces.persistence.test.service.StartupService.TOTAL_RECORDS;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
import org.omnifaces.persistence.test.model.EnumEntity;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Product;
import org.omnifaces.persistence.test.model.ProductStatus;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.model.UserRole;
import org.omnifaces.persistence.test.model.enums.HardDeleteCodeEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteCodeTable;
import org.omnifaces.persistence.test.model.enums.HardDeleteIdEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteIdTable;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyCodeEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyCodeTable;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyIdEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyIdTable;
import org.omnifaces.persistence.test.model.enums.IdCodeEnumTableNonDefault;
import org.omnifaces.persistence.test.model.enums.IdCodeEnumWithoutTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteCodeEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteCodeTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteIdEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteIdTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyCodeEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyCodeTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyIdEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyIdTable;
import org.omnifaces.persistence.test.service.CommentService;
import org.omnifaces.persistence.test.service.EnumEntityService;
import org.omnifaces.persistence.test.service.LookupService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.ProductService;
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
			.addAsLibraries(maven.loadPomFromFile("pom.xml").importCompileAndRuntimeDependencies().resolve().withTransitivity().asFile());
	}

	@EJB
	private PersonService personService;

	@EJB
	private TextService textService;

	@EJB
	private CommentService commentService;

	@EJB
	private LookupService lookupService;

	@EJB
	private ProductService productService;

	@EJB
	private EnumEntityService enumEntityService;

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


	// @EnumMapping ---------------------------------------------------------------------------------------------------

	@Test
	public void testPersistedEntitiesWithEnums() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		Product product = productService.getByIdWithUserRoles(1L);
		assertEquals("Product status for product 1 was persisted", ProductStatus.IN_STOCK, product.getProductStatus());
		assertEquals("Product status id for product 1 was persisted", ProductStatus.IN_STOCK.getId(), productService.getRawProductStatus(product.getId()));
		assertTrue("User roles for product 1 were persisted", product.getUserRoles().size() == 1 && product.getUserRoles().contains(UserRole.USER));
		assertTrue("User roles code for product 1 were persisted", productService.getRawUserRoles(product.getId()).contains(UserRole.USER.getCode()));

		product = productService.getByIdWithUserRoles(2L);
		assertEquals("Product status for product 2 was persisted", ProductStatus.DISCONTINUED, product.getProductStatus());
		assertEquals("Product status id for product 2 was persisted", ProductStatus.DISCONTINUED.getId(), productService.getRawProductStatus(product.getId()));
		assertTrue("User roles for product 2 were persisted", product.getUserRoles().size() == 2 && product.getUserRoles().containsAll(asList(UserRole.EMPLOYEE, UserRole.MANAGER)));
		assertTrue("User roles code for product 2 were persisted", productService.getRawUserRoles(product.getId()).containsAll(asList(UserRole.EMPLOYEE.getCode(), UserRole.MANAGER.getCode())));
	}

	@Test
	public void testTwoValuedEnumMappingTable() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		// Test hard delete id enum
		List<Object> hardDeleteIdEnumResultList = enumEntityService.getHardDeleteIdEnumTable();
		assertTrue("Hard delete id enum table size = 3", hardDeleteIdEnumResultList.size() == 3);
		assertTrue("Hard delete id enum values size = 3", asList(HardDeleteIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(HardDeleteIdEnum.class, hardDeleteIdEnumResultList, false, true, false);

		// Test hard delete id table
		List<Object> hardDeleteIdTableResultList = enumEntityService.getHardDeleteIdTableTable();
		assertTrue("Hard delete id table table size = 2", hardDeleteIdTableResultList.size() == 2);
		assertTrue("Hard delete id table values size = 2", asList(HardDeleteIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(HardDeleteIdTable.class, hardDeleteIdTableResultList, false, true, false);

		// Test soft delete id enum
		List<Object> softDeleteIdEnumResultList = enumEntityService.getSoftDeleteIdEnumTable();
		assertTrue("Soft delete id enum table size = 3", softDeleteIdEnumResultList.size() == 3);
		assertTrue("Soft delete id enum values size = 3", asList(SoftDeleteIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(SoftDeleteIdEnum.class, softDeleteIdEnumResultList, false, true, false);
		List<Object> softDeleteIdEnumHistoryResultList = enumEntityService.getSoftDeleteIdEnumHistoryTable();
		assertTrue("Soft delete id enum history table size = 1", softDeleteIdEnumHistoryResultList.size() == 1);
		testEnumToTableCorrespondence(SoftDeleteIdEnum.class, softDeleteIdEnumHistoryResultList, false, true, true);

		// Test soft delete id table
		List<Object> softDeleteIdTableResultList = enumEntityService.getSoftDeleteIdTableTable();
		assertTrue("Soft delete id table table size = 2", softDeleteIdTableResultList.size() == 2);
		assertTrue("Soft delete id table values size = 2", asList(SoftDeleteIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(SoftDeleteIdTable.class, softDeleteIdTableResultList, false, true, false);
		List<Object> softDeleteIdTableHistoryResultList = enumEntityService.getSoftDeleteIdTableHistoryTable();
		assertTrue("Soft delete id table history table size = 2", softDeleteIdTableHistoryResultList.size() == 2);
		testEnumToTableCorrespondence(SoftDeleteIdTable.class, softDeleteIdTableHistoryResultList, false, true, true);

		// Test hard delete code enum
		List<Object> hardDeleteCodeEnumResultList = enumEntityService.getHardDeleteCodeEnumTable();
		assertTrue("Hard delete code enum table size = 3", hardDeleteCodeEnumResultList.size() == 3);
		assertTrue("Hard delete code enum values size = 3", asList(HardDeleteCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(HardDeleteCodeEnum.class, hardDeleteCodeEnumResultList, false, false, false);

		// Test hard delete code table
		List<Object> hardDeleteCodeTableResultList = enumEntityService.getHardDeleteCodeTableTable();
		assertTrue("Hard delete code table table size = 2", hardDeleteCodeTableResultList.size() == 2);
		assertTrue("Hard delete code table values size = 2", asList(HardDeleteCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(HardDeleteCodeTable.class, hardDeleteCodeTableResultList, false, false, false);

		// Test soft delete code enum
		List<Object> softDeleteCodeEnumResultList = enumEntityService.getSoftDeleteCodeEnumTable();
		assertTrue("Soft delete code enum table size = 3", softDeleteCodeEnumResultList.size() == 3);
		assertTrue("Soft delete code enum values size = 3", asList(SoftDeleteCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(SoftDeleteCodeEnum.class, softDeleteCodeEnumResultList, false, false, false);
		List<Object> softDeleteCodeEnumHistoryResultList = enumEntityService.getSoftDeleteCodeEnumHistoryTable();
		assertTrue("Soft delete code enum history table size = 2", softDeleteCodeEnumHistoryResultList.size() == 2);
		testEnumToTableCorrespondence(SoftDeleteCodeEnum.class, softDeleteCodeEnumHistoryResultList, false, false,
				true);

		// Test soft delete code table
		List<Object> softDeleteCodeTableResultList = enumEntityService.getSoftDeleteCodeTableTable();
		assertTrue("Soft delete code table table size = 2", softDeleteCodeTableResultList.size() == 2);
		assertTrue("Soft delete code table values size = 2", asList(SoftDeleteCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(SoftDeleteCodeTable.class, softDeleteCodeTableResultList, false, false, false);
		List<Object> softDeleteCodeTableHistoryResultList = enumEntityService.getSoftDeleteCodeTableHistoryTable();
		assertTrue("Soft delete code table history table size = 3", softDeleteCodeTableHistoryResultList.size() == 3);
		testEnumToTableCorrespondence(SoftDeleteCodeTable.class, softDeleteCodeTableHistoryResultList, false, false,
				true);
	}

	@Test
	public void testOneValuedEnumMappingTable() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		// Test hard delete only id enum
		List<Object> hardDeleteOnlyIdEnumResultList = enumEntityService.getHardDeleteOnlyIdEnumTable();
		assertTrue("Hard delete only id enum table size = 3", hardDeleteOnlyIdEnumResultList.size() == 3);
		assertTrue("Hard delete only id enum values size = 3", asList(HardDeleteOnlyIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(HardDeleteOnlyIdEnum.class, hardDeleteOnlyIdEnumResultList, true, true, false);

		// Test hard delete only id table
		List<Object> hardDeleteOnlyIdTableResultList = enumEntityService.getHardDeleteOnlyIdTableTable();
		assertTrue("Hard delete only id table table size = 2", hardDeleteOnlyIdTableResultList.size() == 2);
		assertTrue("Hard delete only id table values size = 2", asList(HardDeleteOnlyIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(HardDeleteOnlyIdTable.class, hardDeleteOnlyIdTableResultList, true, true, false);

		// Test soft delete only id enum
		List<Object> softDeleteOnlyIdEnumResultList = enumEntityService.getSoftDeleteOnlyIdEnumTable();
		assertTrue("Soft delete only id enum table size = 3", softDeleteOnlyIdEnumResultList.size() == 3);
		assertTrue("Soft delete only id enum values size = 3", asList(SoftDeleteOnlyIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(SoftDeleteOnlyIdEnum.class, softDeleteOnlyIdEnumResultList, true, true, false);
		List<Object> softDeleteOnlyIdEnumHistoryResultList = enumEntityService.getSoftDeleteOnlyIdEnumHistoryTable();
		assertTrue("Soft delete only id enum history table size = 1", softDeleteOnlyIdEnumHistoryResultList.size() == 1);
		testEnumToTableCorrespondence(SoftDeleteOnlyIdEnum.class, softDeleteOnlyIdEnumHistoryResultList, true, true, true);

		// Test soft delete only id table
		List<Object> softDeleteOnlyIdTableResultList = enumEntityService.getSoftDeleteOnlyIdTableTable();
		assertTrue("Soft delete only id table table size = 2", softDeleteOnlyIdTableResultList.size() == 2);
		assertTrue("Soft delete only id table values size = 2", asList(SoftDeleteOnlyIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(SoftDeleteOnlyIdTable.class, softDeleteOnlyIdTableResultList, true, true, false);
		List<Object> softDeleteOnlyIdTableHistoryResultList = enumEntityService.getSoftDeleteOnlyIdTableHistoryTable();
		assertTrue("Soft delete only id table history table size = 2", softDeleteOnlyIdTableHistoryResultList.size() == 2);
		testEnumToTableCorrespondence(SoftDeleteOnlyIdTable.class, softDeleteOnlyIdTableHistoryResultList, true, true, true);

		// Test hard delete only code enum
		List<Object> hardDeleteOnlyCodeEnumResultList = enumEntityService.getHardDeleteOnlyCodeEnumTable();
		assertTrue("Hard delete only code enum table size = 3", hardDeleteOnlyCodeEnumResultList.size() == 3);
		assertTrue("Hard delete only code enum values size = 3", asList(HardDeleteOnlyCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(HardDeleteOnlyCodeEnum.class, hardDeleteOnlyCodeEnumResultList, true, false, false);

		// Test hard delete only code table
		List<Object> hardDeleteOnlyCodeTableResultList = enumEntityService.getHardDeleteOnlyCodeTableTable();
		assertTrue("Hard delete only code table table size = 2", hardDeleteOnlyCodeTableResultList.size() == 2);
		assertTrue("Hard delete only code table values size = 2", asList(HardDeleteOnlyCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(HardDeleteOnlyCodeTable.class, hardDeleteOnlyCodeTableResultList, true, false, false);

		// Test soft delete only code enum
		List<Object> softDeleteOnlyCodeEnumResultList = enumEntityService.getSoftDeleteOnlyCodeEnumTable();
		assertTrue("Soft delete only code enum table size = 3", softDeleteOnlyCodeEnumResultList.size() == 3);
		assertTrue("Soft delete only code enum values size = 3", asList(SoftDeleteOnlyCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeEnum.class, softDeleteOnlyCodeEnumResultList, true, false, false);
		List<Object> softDeleteOnlyCodeEnumHistoryResultList = enumEntityService.getSoftDeleteOnlyCodeEnumHistoryTable();
		assertTrue("Soft delete only code enum history table size = 2", softDeleteOnlyCodeEnumHistoryResultList.size() == 2);
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeEnum.class, softDeleteOnlyCodeEnumHistoryResultList, true, false, true);

		// Test soft delete only code table
		List<Object> softDeleteOnlyCodeTableResultList = enumEntityService.getSoftDeleteOnlyCodeTableTable();
		assertTrue("Soft delete only code table table size = 2", softDeleteOnlyCodeTableResultList.size() == 2);
		assertTrue("Soft delete only code table values size = 2", asList(SoftDeleteOnlyCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeTable.class, softDeleteOnlyCodeTableResultList, true, false, false);
		List<Object> softDeleteOnlyCodeTableHistoryResultList = enumEntityService.getSoftDeleteOnlyCodeTableHistoryTable();
		assertTrue("Soft delete only code table history table size = 3", softDeleteOnlyCodeTableHistoryResultList.size() == 3);
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeTable.class, softDeleteOnlyCodeTableHistoryResultList, true, false, true);
	}

	@Test
	public void testEnumMappingTableSpecials() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		// Test non-default enum table mappings
		List<Object> idCodeEnumWithoutTableResultList = enumEntityService.getIdCodeEnumWithoutTableTable();
		assertTrue("Non-default enum mapping table size = 3", idCodeEnumWithoutTableResultList.size() == 3);
		assertTrue("Non-default enum values size = 3", asList(IdCodeEnumWithoutTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L);
		testEnumToTableCorrespondence(IdCodeEnumWithoutTable.class, idCodeEnumWithoutTableResultList, false, true, false);
		List<Object> idCodeEnumWithoutTableHistoryResultList = enumEntityService.getIdCodeEnumWithoutTableHistoryTable();
		assertTrue("Non-default enum mapping history table size = 0", idCodeEnumWithoutTableHistoryResultList.isEmpty());

		List<Object> idCodeEnumTableNonDefaultResultList = enumEntityService.getIdCodeEnumTableNonDefaultTable();
		assertTrue("Non-default table enum mapping table size = 2", idCodeEnumTableNonDefaultResultList.size() == 2);
		assertTrue("Non-default table enum values size = 2", asList(IdCodeEnumTableNonDefault.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L);
		testEnumToTableCorrespondence(IdCodeEnumTableNonDefault.class, idCodeEnumTableNonDefaultResultList, false, true, false);
		List<Object> idCodeEnumTableNonDefaultHistoryResultList = enumEntityService.getIdCodeEnumTableNonDefaultHistoryTable();
		assertTrue("Non-default table enum mapping history table size = 2", idCodeEnumTableNonDefaultHistoryResultList.size() == 2);
		testEnumToTableCorrespondence(IdCodeEnumTableNonDefault.class, idCodeEnumTableNonDefaultHistoryResultList, false, true, true);
	}

	@Test
	public void testEnumMappingPersistence() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		// Test if an entity is persisted
		EnumEntity newEnumEntity = new EnumEntity();
		newEnumEntity.setHardDeleteCodeEnum(HardDeleteCodeEnum.FIRST);
		newEnumEntity.setHardDeleteCodeTable(HardDeleteCodeTable.valueOf("DEF"));
		newEnumEntity.setHardDeleteIdEnum(HardDeleteIdEnum.FIRST);
		newEnumEntity.setHardDeleteIdTable(HardDeleteIdTable.valueOf("DEF"));
		newEnumEntity.setSoftDeleteCodeEnum(SoftDeleteCodeEnum.FIRST);
		newEnumEntity.setSoftDeleteCodeTable(SoftDeleteCodeTable.valueOf("DEF"));
		newEnumEntity.setSoftDeleteIdEnum(SoftDeleteIdEnum.FIRST);
		newEnumEntity.setSoftDeleteIdTable(SoftDeleteIdTable.valueOf("DEF"));
		newEnumEntity.setHardDeleteOnlyCodeEnum(HardDeleteOnlyCodeEnum.FIRST);
		newEnumEntity.setHardDeleteOnlyCodeTable(HardDeleteOnlyCodeTable.valueOf("DEF"));
		newEnumEntity.setHardDeleteOnlyIdEnum(HardDeleteOnlyIdEnum.FIRST);
		newEnumEntity.setHardDeleteOnlyIdTable(HardDeleteOnlyIdTable.valueOf("DEFAULT_2"));
		newEnumEntity.setSoftDeleteOnlyCodeEnum(SoftDeleteOnlyCodeEnum.FIRST);
		newEnumEntity.setSoftDeleteOnlyCodeTable(SoftDeleteOnlyCodeTable.valueOf("DEF"));
		newEnumEntity.setSoftDeleteOnlyIdEnum(SoftDeleteOnlyIdEnum.FIRST);
		newEnumEntity.setSoftDeleteOnlyIdTable(SoftDeleteOnlyIdTable.valueOf("DEFAULT_2"));
		newEnumEntity.setIdCodeEnumWithoutTable(IdCodeEnumWithoutTable.FIRST);
		newEnumEntity.setIdCodeEnumTableNonDefault(IdCodeEnumTableNonDefault.valueOf("DEF"));

		enumEntityService.persist(newEnumEntity);
		assertEquals("New enum entity id", Long.valueOf(1L), newEnumEntity.getId());

		// Test if a persisted entity equals a loaded one
		EnumEntity persistedEnumEntity = enumEntityService.getById(1L);
		boolean equality = newEnumEntity.getHardDeleteCodeEnum() == persistedEnumEntity.getHardDeleteCodeEnum()
				&& newEnumEntity.getHardDeleteCodeTable() == persistedEnumEntity.getHardDeleteCodeTable()
				&& newEnumEntity.getHardDeleteIdEnum() == persistedEnumEntity.getHardDeleteIdEnum()
				&& newEnumEntity.getHardDeleteIdTable() == persistedEnumEntity.getHardDeleteIdTable()
				&& newEnumEntity.getSoftDeleteCodeEnum() == persistedEnumEntity.getSoftDeleteCodeEnum()
				&& newEnumEntity.getSoftDeleteCodeTable() == persistedEnumEntity.getSoftDeleteCodeTable()
				&& newEnumEntity.getSoftDeleteIdEnum() == persistedEnumEntity.getSoftDeleteIdEnum()
				&& newEnumEntity.getSoftDeleteIdTable() == persistedEnumEntity.getSoftDeleteIdTable()
				&& newEnumEntity.getHardDeleteOnlyCodeEnum() == persistedEnumEntity.getHardDeleteOnlyCodeEnum()
				&& newEnumEntity.getHardDeleteOnlyCodeTable() == persistedEnumEntity.getHardDeleteOnlyCodeTable()
				&& newEnumEntity.getHardDeleteOnlyIdEnum() == persistedEnumEntity.getHardDeleteOnlyIdEnum()
				&& newEnumEntity.getHardDeleteOnlyIdTable() == persistedEnumEntity.getHardDeleteOnlyIdTable()
				&& newEnumEntity.getSoftDeleteOnlyCodeEnum() == persistedEnumEntity.getSoftDeleteOnlyCodeEnum()
				&& newEnumEntity.getSoftDeleteOnlyCodeTable() == persistedEnumEntity.getSoftDeleteOnlyCodeTable()
				&& newEnumEntity.getSoftDeleteOnlyIdEnum() == persistedEnumEntity.getSoftDeleteOnlyIdEnum()
				&& newEnumEntity.getSoftDeleteOnlyIdTable() == persistedEnumEntity.getSoftDeleteOnlyIdTable()
				&& newEnumEntity.getIdCodeEnumWithoutTable() == persistedEnumEntity.getIdCodeEnumWithoutTable()
				&& newEnumEntity.getIdCodeEnumTableNonDefault() == persistedEnumEntity.getIdCodeEnumTableNonDefault();

		assertTrue("Enum entity from the database equals persisted one", equality);
	}

	private void testEnumToTableCorrespondence(Class<? extends Enum<?>> enumClass, List<Object> tableResultList, boolean isOneValue, boolean isOrdinal, boolean isHistory) {
		asList(enumClass.getEnumConstants()).stream().filter(Objects::nonNull).forEach(enumConstant -> {
			int number = tableResultList.stream().mapToInt(object -> {
				Object[] result = isOneValue ? null : (Object[]) object;
				int id = isOneValue ? (isOrdinal ? (int) object : -1) : (int) result[0];
				String code = isOneValue ? (isOrdinal ? null : (String) object) : (String) result[1];
				return isOneValue
						? ((isOrdinal ? (id == enumConstant.ordinal()) : (enumConstant.name().equals(code))) ? 1 : 0)
						: ((id == enumConstant.ordinal() && enumConstant.name().equals(code)) ? 1 : 0);
			}).sum();
			if (isHistory) {
				assertTrue("No matches found between enum constant and database table", number == 0);
			} else {
				assertTrue("Exactly one enum constant found and corresponds to the database table", number == 1);
			}
		});
	}

}