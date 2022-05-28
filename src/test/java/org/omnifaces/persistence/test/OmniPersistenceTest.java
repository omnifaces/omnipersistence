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
import static java.util.Arrays.asList;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.service.StartupService.TOTAL_RECORDS;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.ejb.EJB;

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


	// @EnumMapping ---------------------------------------------------------------------------------------------------

	@Test
	public void testPersistedEntitiesWithEnums() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		Product product = productService.getByIdWithUserRoles(1L);
		assertEquals(ProductStatus.IN_STOCK, product.getProductStatus(), "Product status for product 1 was persisted");
		assertEquals(ProductStatus.IN_STOCK.getId(), productService.getRawProductStatus(product.getId()), "Product status id for product 1 was persisted");
		assertTrue(product.getUserRoles().size() == 1 && product.getUserRoles().contains(UserRole.USER), "User roles for product 1 were persisted");
		assertTrue(productService.getRawUserRoles(product.getId()).contains(UserRole.USER.getCode()), "User roles code for product 1 were persisted");

		product = productService.getByIdWithUserRoles(2L);
		assertEquals(ProductStatus.DISCONTINUED, product.getProductStatus(), "Product status for product 2 was persisted");
		assertEquals(ProductStatus.DISCONTINUED.getId(), productService.getRawProductStatus(product.getId()), "Product status id for product 2 was persisted");
		assertTrue(product.getUserRoles().size() == 2 && product.getUserRoles().containsAll(asList(UserRole.EMPLOYEE, UserRole.MANAGER)), "User roles for product 2 were persisted");
		assertTrue(productService.getRawUserRoles(product.getId()).containsAll(asList(UserRole.EMPLOYEE.getCode(), UserRole.MANAGER.getCode())), "User roles code for product 2 were persisted");
	}

	@Test
	public void testTwoValuedEnumMappingTable() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		// Test hard delete id enum
		List<Object> hardDeleteIdEnumResultList = enumEntityService.getHardDeleteIdEnumTable();
		assertTrue(hardDeleteIdEnumResultList.size() == 3, "Hard delete id enum table size = 3");
		assertTrue(asList(HardDeleteIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Hard delete id enum values size = 3");
		testEnumToTableCorrespondence(HardDeleteIdEnum.class, hardDeleteIdEnumResultList, false, true, false);

		// Test hard delete id table
		List<Object> hardDeleteIdTableResultList = enumEntityService.getHardDeleteIdTableTable();
		assertTrue(hardDeleteIdTableResultList.size() == 2, "Hard delete id table table size = 2");
		assertTrue(asList(HardDeleteIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Hard delete id table values size = 2");
		testEnumToTableCorrespondence(HardDeleteIdTable.class, hardDeleteIdTableResultList, false, true, false);

		// Test soft delete id enum
		List<Object> softDeleteIdEnumResultList = enumEntityService.getSoftDeleteIdEnumTable();
		assertTrue(softDeleteIdEnumResultList.size() == 3, "Soft delete id enum table size = 3");
		assertTrue(asList(SoftDeleteIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Soft delete id enum values size = 3");
		testEnumToTableCorrespondence(SoftDeleteIdEnum.class, softDeleteIdEnumResultList, false, true, false);
		List<Object> softDeleteIdEnumHistoryResultList = enumEntityService.getSoftDeleteIdEnumHistoryTable();
		assertTrue(softDeleteIdEnumHistoryResultList.size() == 1, "Soft delete id enum history table size = 1");
		testEnumToTableCorrespondence(SoftDeleteIdEnum.class, softDeleteIdEnumHistoryResultList, false, true, true);

		// Test soft delete id table
		List<Object> softDeleteIdTableResultList = enumEntityService.getSoftDeleteIdTableTable();
		assertTrue(softDeleteIdTableResultList.size() == 2, "Soft delete id table table size = 2");
		assertTrue(asList(SoftDeleteIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Soft delete id table values size = 2");
		testEnumToTableCorrespondence(SoftDeleteIdTable.class, softDeleteIdTableResultList, false, true, false);
		List<Object> softDeleteIdTableHistoryResultList = enumEntityService.getSoftDeleteIdTableHistoryTable();
		assertTrue(softDeleteIdTableHistoryResultList.size() == 2, "Soft delete id table history table size = 2");
		testEnumToTableCorrespondence(SoftDeleteIdTable.class, softDeleteIdTableHistoryResultList, false, true, true);

		// Test hard delete code enum
		List<Object> hardDeleteCodeEnumResultList = enumEntityService.getHardDeleteCodeEnumTable();
		assertTrue(hardDeleteCodeEnumResultList.size() == 3, "Hard delete code enum table size = 3");
		assertTrue(asList(HardDeleteCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Hard delete code enum values size = 3");
		testEnumToTableCorrespondence(HardDeleteCodeEnum.class, hardDeleteCodeEnumResultList, false, false, false);

		// Test hard delete code table
		List<Object> hardDeleteCodeTableResultList = enumEntityService.getHardDeleteCodeTableTable();
		assertTrue(hardDeleteCodeTableResultList.size() == 2, "Hard delete code table table size = 2");
		assertTrue(asList(HardDeleteCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Hard delete code table values size = 2");
		testEnumToTableCorrespondence(HardDeleteCodeTable.class, hardDeleteCodeTableResultList, false, false, false);

		// Test soft delete code enum
		List<Object> softDeleteCodeEnumResultList = enumEntityService.getSoftDeleteCodeEnumTable();
		assertTrue(softDeleteCodeEnumResultList.size() == 3, "Soft delete code enum table size = 3");
		assertTrue(asList(SoftDeleteCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Soft delete code enum values size = 3");
		testEnumToTableCorrespondence(SoftDeleteCodeEnum.class, softDeleteCodeEnumResultList, false, false, false);
		List<Object> softDeleteCodeEnumHistoryResultList = enumEntityService.getSoftDeleteCodeEnumHistoryTable();
		assertTrue(softDeleteCodeEnumHistoryResultList.size() == 2, "Soft delete code enum history table size = 2");
		testEnumToTableCorrespondence(SoftDeleteCodeEnum.class, softDeleteCodeEnumHistoryResultList, false, false,
				true);

		// Test soft delete code table
		List<Object> softDeleteCodeTableResultList = enumEntityService.getSoftDeleteCodeTableTable();
		assertTrue(softDeleteCodeTableResultList.size() == 2, "Soft delete code table table size = 2");
		assertTrue(asList(SoftDeleteCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Soft delete code table values size = 2");
		testEnumToTableCorrespondence(SoftDeleteCodeTable.class, softDeleteCodeTableResultList, false, false, false);
		List<Object> softDeleteCodeTableHistoryResultList = enumEntityService.getSoftDeleteCodeTableHistoryTable();
		assertTrue(softDeleteCodeTableHistoryResultList.size() == 3, "Soft delete code table history table size = 3");
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
		assertTrue(hardDeleteOnlyIdEnumResultList.size() == 3, "Hard delete only id enum table size = 3");
		assertTrue(asList(HardDeleteOnlyIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Hard delete only id enum values size = 3");
		testEnumToTableCorrespondence(HardDeleteOnlyIdEnum.class, hardDeleteOnlyIdEnumResultList, true, true, false);

		// Test hard delete only id table
		List<Object> hardDeleteOnlyIdTableResultList = enumEntityService.getHardDeleteOnlyIdTableTable();
		assertTrue(hardDeleteOnlyIdTableResultList.size() == 2, "Hard delete only id table table size = 2");
		assertTrue(asList(HardDeleteOnlyIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Hard delete only id table values size = 2");
		testEnumToTableCorrespondence(HardDeleteOnlyIdTable.class, hardDeleteOnlyIdTableResultList, true, true, false);

		// Test soft delete only id enum
		List<Object> softDeleteOnlyIdEnumResultList = enumEntityService.getSoftDeleteOnlyIdEnumTable();
		assertTrue(softDeleteOnlyIdEnumResultList.size() == 3, "Soft delete only id enum table size = 3");
		assertTrue(asList(SoftDeleteOnlyIdEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Soft delete only id enum values size = 3");
		testEnumToTableCorrespondence(SoftDeleteOnlyIdEnum.class, softDeleteOnlyIdEnumResultList, true, true, false);
		List<Object> softDeleteOnlyIdEnumHistoryResultList = enumEntityService.getSoftDeleteOnlyIdEnumHistoryTable();
		assertTrue(softDeleteOnlyIdEnumHistoryResultList.size() == 1, "Soft delete only id enum history table size = 1");
		testEnumToTableCorrespondence(SoftDeleteOnlyIdEnum.class, softDeleteOnlyIdEnumHistoryResultList, true, true, true);

		// Test soft delete only id table
		List<Object> softDeleteOnlyIdTableResultList = enumEntityService.getSoftDeleteOnlyIdTableTable();
		assertTrue(softDeleteOnlyIdTableResultList.size() == 2, "Soft delete only id table table size = 2");
		assertTrue(asList(SoftDeleteOnlyIdTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Soft delete only id table values size = 2");
		testEnumToTableCorrespondence(SoftDeleteOnlyIdTable.class, softDeleteOnlyIdTableResultList, true, true, false);
		List<Object> softDeleteOnlyIdTableHistoryResultList = enumEntityService.getSoftDeleteOnlyIdTableHistoryTable();
		assertTrue(softDeleteOnlyIdTableHistoryResultList.size() == 2, "Soft delete only id table history table size = 2");
		testEnumToTableCorrespondence(SoftDeleteOnlyIdTable.class, softDeleteOnlyIdTableHistoryResultList, true, true, true);

		// Test hard delete only code enum
		List<Object> hardDeleteOnlyCodeEnumResultList = enumEntityService.getHardDeleteOnlyCodeEnumTable();
		assertTrue(hardDeleteOnlyCodeEnumResultList.size() == 3, "Hard delete only code enum table size = 3");
		assertTrue(asList(HardDeleteOnlyCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Hard delete only code enum values size = 3");
		testEnumToTableCorrespondence(HardDeleteOnlyCodeEnum.class, hardDeleteOnlyCodeEnumResultList, true, false, false);

		// Test hard delete only code table
		List<Object> hardDeleteOnlyCodeTableResultList = enumEntityService.getHardDeleteOnlyCodeTableTable();
		assertTrue(hardDeleteOnlyCodeTableResultList.size() == 2, "Hard delete only code table table size = 2");
		assertTrue(asList(HardDeleteOnlyCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Hard delete only code table values size = 2");
		testEnumToTableCorrespondence(HardDeleteOnlyCodeTable.class, hardDeleteOnlyCodeTableResultList, true, false, false);

		// Test soft delete only code enum
		List<Object> softDeleteOnlyCodeEnumResultList = enumEntityService.getSoftDeleteOnlyCodeEnumTable();
		assertTrue(softDeleteOnlyCodeEnumResultList.size() == 3, "Soft delete only code enum table size = 3");
		assertTrue(asList(SoftDeleteOnlyCodeEnum.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Soft delete only code enum values size = 3");
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeEnum.class, softDeleteOnlyCodeEnumResultList, true, false, false);
		List<Object> softDeleteOnlyCodeEnumHistoryResultList = enumEntityService.getSoftDeleteOnlyCodeEnumHistoryTable();
		assertTrue(softDeleteOnlyCodeEnumHistoryResultList.size() == 2, "Soft delete only code enum history table size = 2");
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeEnum.class, softDeleteOnlyCodeEnumHistoryResultList, true, false, true);

		// Test soft delete only code table
		List<Object> softDeleteOnlyCodeTableResultList = enumEntityService.getSoftDeleteOnlyCodeTableTable();
		assertTrue(softDeleteOnlyCodeTableResultList.size() == 2, "Soft delete only code table table size = 2");
		assertTrue(asList(SoftDeleteOnlyCodeTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Soft delete only code table values size = 2");
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeTable.class, softDeleteOnlyCodeTableResultList, true, false, false);
		List<Object> softDeleteOnlyCodeTableHistoryResultList = enumEntityService.getSoftDeleteOnlyCodeTableHistoryTable();
		assertTrue(softDeleteOnlyCodeTableHistoryResultList.size() == 3, "Soft delete only code table history table size = 3");
		testEnumToTableCorrespondence(SoftDeleteOnlyCodeTable.class, softDeleteOnlyCodeTableHistoryResultList, true, false, true);
	}

	@Test
	public void testEnumMappingTableSpecials() {
		if (isEclipseLink()) {
			return; // EclipseLink doesn't like EnumMappingTableService's actions.
		}

		// Test non-default enum table mappings
		List<Object> idCodeEnumWithoutTableResultList = enumEntityService.getIdCodeEnumWithoutTableTable();
		assertTrue(idCodeEnumWithoutTableResultList.size() == 3, "Non-default enum mapping table size = 3");
		assertTrue(asList(IdCodeEnumWithoutTable.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 3L, "Non-default enum values size = 3");
		testEnumToTableCorrespondence(IdCodeEnumWithoutTable.class, idCodeEnumWithoutTableResultList, false, true, false);
		List<Object> idCodeEnumWithoutTableHistoryResultList = enumEntityService.getIdCodeEnumWithoutTableHistoryTable();
		assertTrue(idCodeEnumWithoutTableHistoryResultList.isEmpty(), "Non-default enum mapping history table size = 0");

		List<Object> idCodeEnumTableNonDefaultResultList = enumEntityService.getIdCodeEnumTableNonDefaultTable();
		assertTrue(idCodeEnumTableNonDefaultResultList.size() == 2, "Non-default table enum mapping table size = 2");
		assertTrue(asList(IdCodeEnumTableNonDefault.class.getEnumConstants()).stream().filter(Objects::nonNull).count() == 2L, "Non-default table enum values size = 2");
		testEnumToTableCorrespondence(IdCodeEnumTableNonDefault.class, idCodeEnumTableNonDefaultResultList, false, true, false);
		List<Object> idCodeEnumTableNonDefaultHistoryResultList = enumEntityService.getIdCodeEnumTableNonDefaultHistoryTable();
		assertTrue(idCodeEnumTableNonDefaultHistoryResultList.size() == 2, "Non-default table enum mapping history table size = 2");
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
		assertEquals(Long.valueOf(1L), newEnumEntity.getId(), "New enum entity id");

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

		assertTrue(equality, "Enum entity from the database equals persisted one");
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
				assertTrue(number == 0, "No matches found between enum constant and database table");
			} else {
				assertTrue(number == 1, "Exactly one enum constant found and corresponds to the database table");
			}
		});
	}

}