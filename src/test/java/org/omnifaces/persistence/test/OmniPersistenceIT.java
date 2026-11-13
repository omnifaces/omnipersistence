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
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.Database.DB2;
import static org.omnifaces.persistence.test.service.StartupServiceEJB.ROWS_PER_PAGE;
import static org.omnifaces.persistence.test.service.StartupServiceEJB.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupServiceEJB.TOTAL_RECORDS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.archive.importer.MavenImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.Database;
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
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Address;
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Config;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Group;
import org.omnifaces.persistence.test.model.InventoryItem;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.model.dto.PersonCard;
import org.omnifaces.persistence.test.service.ConfigService;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.PlainConfigUpdater;
import org.omnifaces.persistence.test.service.TestAuditListener;
import org.omnifaces.persistence.test.service.TrackingTextService;
import org.omnifaces.utils.collection.PartialResultList;

@ExtendWith(ArquillianExtension.class)
public abstract class OmniPersistenceIT {

    static final String PROFILE_ID = "profile.id";
    private static final String READ_COMMITTED = "<isolation-level>TRANSACTION_READ_COMMITTED</isolation-level>";
    private static final String DB2_USER = "db2inst1";
    private static final String DB2_PASSWORD = "PassW0rd!";
    private static final String TEST_DATABASE_RESOURCE = "META-INF/test.database";
    static final String OPENJPA = ".*-openjpa";
    static final String ECLIPSELINK = ".*-eclipselink";
    static final String ECLIPSELINK_OR_OPENJPA = ".*-(eclipselink|openjpa)";
    static final String HIBERNATE_FORMULA = "Address.string is a Hibernate specific @Formula";
    static final String OPENJPA_COUNT = "OpenJPA correlates a nested subquery in the count query with an extra cross-joined entity instead of the counted one,"
        + " so this criteria is left out of the count query";
    private static final Map<String, Object> UNPARSEABLE_PLAIN_ITEM_OF_ELEMENT_COLLECTION = Map.of("groups", List.of("USER", "abc"));
    private static final int ORDERED_PAGES = 3;
    private static final int STRING_IDS = 3;
    private static final long MIN_ID = 10;
    private static final long MAX_ID = 190;
    private static final LocalDate MAX_DATE_OF_BIRTH = LocalDate.of(1970, 1, 1);
    private static final long TOTAL_PHONES = 3;

    protected static WebArchive createDeployment(Class<?> testClass, Class<?> excludedStartupService) {
        var persistenceUnitName = testClass.getSimpleName();
        var database = Database.valueOf(getProperty("test.database").toUpperCase());
        var wildFlyDB2 = database == DB2 && getProperty(PROFILE_ID).startsWith("wildfly-");
        var jtaDataSource = (wildFlyDB2 ? "java:jboss/datasources/" : "java:app/") + persistenceUnitName;
        var maven = Maven.resolver();
        var archive = create(WebArchive.class)
            .addPackages(true, testClass.getPackage())
            .deleteClass(excludedStartupService)
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
            .addAsResource(
                new StringAsset(
                    substitute(
                        "META-INF/persistence.xml", persistenceUnitName,
                        Map.of("${jtaDataSource}", jtaDataSource, "${openJpaSchema}", openJpaSchemaOf(database, persistenceUnitName))
                    )
                ),
                "META-INF/persistence.xml"
            )
            .addAsWebInfResource(
                new StringAsset(
                    substitute("WEB-INF/web.xml", persistenceUnitName, Map.of("${dataSource}", wildFlyDB2 ? "" : dataSourceOf(database, persistenceUnitName)))
                ),
                "web.xml"
            )
            .addAsResource(new StringAsset(database.name()), TEST_DATABASE_RESOURCE)
            .addAsResource("META-INF/sql/create-test.sql")
            .addAsResource("META-INF/sql/drop-test.sql")
            .addAsResource("META-INF/sql/load-test.sql")
            .addAsLibrary(create(MavenImporter.class).loadPomFromFile("pom.xml").importBuildOutput().as(JavaArchive.class))
            .addAsLibraries(maven.loadPomFromFile("pom.xml").importCompileAndRuntimeDependencies().resolve().withTransitivity().asFile())
            .addAsLibraries(
                maven
                    .resolve(
                        "com.h2database:h2:" + getProperty("test.h2.version"),
                        "com.mysql:mysql-connector-j:" + getProperty("test.mysql-driver.version"),
                        "org.postgresql:postgresql:" + getProperty("test.postgresql-driver.version"),
                        "com.microsoft.sqlserver:mssql-jdbc:" + getProperty("test.sqlserver-driver.version"),
                        "com.ibm.db2:jcc:" + getProperty("test.db2-driver.version")
                    )
                    .withTransitivity().asFile()
            );

        if (wildFlyDB2) {
            archive.addAsWebInfResource(new StringAsset(wildFlyDB2DataSourceOf(jtaDataSource, persistenceUnitName)), "db2-ds.xml");
        }

        return archive;
    }

    /**
     * Returns the <code>data-source</code> of the given test database, substituted as <code>${dataSource}</code> in <code>web.xml</code>.
     */
    private static String dataSourceOf(Database database, String persistenceUnitName) {
        return switch (database) {
            case H2 -> dataSourceOf(
                persistenceUnitName, "org.h2.jdbcx.JdbcDataSource", urlOf("jdbc:h2:mem:" + persistenceUnitName + ";DB_CLOSE_DELAY=-1;MODE=LEGACY"),
                READ_COMMITTED
            );
            case MYSQL -> dataSourceOf(
                persistenceUnitName, "com.mysql.cj.jdbc.MysqlDataSource",
                urlOf(getProperty("test.mysql.url") + persistenceUnitName + "?fallbackToSystemKeyStore=false") // Avoids GlassFish keystore.
                    + credentialsOf("test", "test"),
                READ_COMMITTED
            );
            case POSTGRESQL -> dataSourceOf(
                persistenceUnitName, "org.postgresql.ds.PGSimpleDataSource",
                urlOf(getProperty("test.postgresql.url") + persistenceUnitName) + credentialsOf("test", "test"), READ_COMMITTED
            );
            case SQLSERVER -> dataSourceOf(
                persistenceUnitName, "com.microsoft.sqlserver.jdbc.SQLServerDataSource",
                urlOf(getProperty("test.sqlserver.url") + persistenceUnitName) + credentialsOf("test", "PassW0rd!"), READ_COMMITTED
            );
            case DB2 -> dataSourceOf(
                // TomEE maps an isolation level onto the int defaultIsolationLevel of the DB2 driver.
                persistenceUnitName, "com.ibm.db2.jcc.DB2Driver", urlOf(db2UrlOf(persistenceUnitName)) + credentialsOf(DB2_USER, DB2_PASSWORD), ""
            );
            default -> throw new UnsupportedOperationException(database.name());
        };
    }

    private static String dataSourceOf(String persistenceUnitName, String className, String settings, String isolationLevel) {
        return "<data-source><name>java:app/" + persistenceUnitName + "</name><class-name>" + className + "</class-name>" + settings
            + "<transactional>true</transactional>" + isolationLevel + "</data-source>";
    }

    /**
     * Returns the DB2 data source as a WildFly <code>-ds.xml</code>, because WildFly cannot configure the DB2 driver via <code>web.xml</code>: it does not
     * accept a driver class, and it ignores the URL and the <code>int</code> properties of a DB2 data source class.
     */
    private static String wildFlyDB2DataSourceOf(String jndiName, String persistenceUnitName) {
        return "<datasources><datasource jndi-name=\"" + jndiName + "\" pool-name=\"" + persistenceUnitName + "\">"
            + "<connection-url>" + db2UrlOf(persistenceUnitName) + "</connection-url><driver>db2.jar</driver>"
            + "<security><user-name>" + DB2_USER + "</user-name><password>" + DB2_PASSWORD + "</password></security></datasource></datasources>";
    }

    /**
     * Returns the DB2 URL with the persistence unit name as schema, because a DB2 database name has at most 8 characters.
     */
    private static String db2UrlOf(String persistenceUnitName) {
        return "jdbc:db2://localhost:50000/test:currentSchema=" + persistenceUnitName.toUpperCase() + ";";
    }

    /**
     * Returns the OpenJPA schema property for DB2, substituted as <code>${openJpaSchema}</code> in <code>persistence.xml</code>, because OpenJPA otherwise
     * finds the tables of the other schemas of the same database.
     */
    private static String openJpaSchemaOf(Database database, String persistenceUnitName) {
        return database == DB2 ? "<property name=\"openjpa.jdbc.Schema\" value=\"" + persistenceUnitName.toUpperCase() + "\" />" : "";
    }

    private static String urlOf(String url) {
        return "<url>" + url + "</url>";
    }

    /**
     * Returns the given credentials as elements as well as properties of the data source, because TomEE ignores the elements.
     */
    private static String credentialsOf(String user, String password) {
        return "<user>" + user + "</user><password>" + password + "</password>" + propertyOf("user", user) + propertyOf("password", password);
    }

    private static String propertyOf(String name, String value) {
        return "<property><name>" + name + "</name><value>" + value + "</value></property>";
    }

    private static String substitute(String resourceName, String persistenceUnitName, Map<String, String> substitutions) {
        try (var stream = OmniPersistenceIT.class.getClassLoader().getResourceAsStream(resourceName)) {
            var content = new String(stream.readAllBytes(), UTF_8).replace("${persistenceUnitName}", persistenceUnitName);

            for (var substitution : substitutions.entrySet()) {
                content = content.replace(substitution.getKey(), substitution.getValue());
            }

            return content;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the test database as added to the deployment by {@link #createDeployment(Class, Class)}, because the test runs in the container, which does not
     * have the system properties of the test run.
     */
    private static Database getTestDatabase() {
        try (var stream = OmniPersistenceIT.class.getClassLoader().getResourceAsStream(TEST_DATABASE_RESOURCE)) {
            return Database.valueOf(new String(stream.readAllBytes(), UTF_8));
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

    protected abstract TrackingTextService trackingTextService();

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
        public jakarta.persistence.criteria.Predicate build(Expression<?> path, CriteriaBuilder criteriaBuilder, ParameterBuilder parameterBuilder) {
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
    void testOneToManyCriteriaMatchingMultipleElements() {
        var all = personService().getPage(Page.ALL, true);
        var result = personService()
            .getPage(Page.with().allMatch(Map.of("phones.number", List.of(Like.startsWith("0"), Like.contains("0")))).build(), true);
        assertEquals(all.size(), result.size(), "Every phone number starts with 0, so every person matches, also with multiple phones");
    }

    @Test
    void testSingleCriteriaOnOneToManyReturnsEachEntityOnce() {
        var expectedIds = personService().getAllWithPhones().stream()
            .filter(person -> person.getPhones().stream().anyMatch(phone -> phone.getNumber().contains("1")))
            .map(Person::getId).collect(toSet());
        assertSingleCriteriaOnOneToManyReturnsEachEntityOnce(expectedIds, personService()::getPage);
        assertSingleCriteriaOnOneToManyReturnsEachEntityOnce(expectedIds, personService()::getPageWithPhones);
    }

    private static void assertSingleCriteriaOnOneToManyReturnsEachEntityOnce(
        Set<Long> expectedIds, BiFunction<Page, Boolean, PartialResultList<Person>> getPage
    )
    {
        assertTrue(expectedIds.size() >= 20, "At least two pages of persons have a phone number containing 1");
        var criteria = Map.<String, Object>of("phones.number", Like.contains("1"));
        var firstPage = getPage.apply(Page.with().range(0, 10).allMatch(criteria).build(), true);
        var secondPage = getPage.apply(Page.with().range(10, 10).allMatch(criteria).build(), false);
        var ids = Stream.concat(firstPage.stream(), secondPage.stream()).map(Person::getId).toList();
        assertEquals(20, ids.size(), "Two full pages");
        assertEquals(ids.size(), ids.stream().distinct().count(), "Each entity is returned once");
        assertTrue(expectedIds.containsAll(ids), "Only entities with a matching element");
        assertEquals(expectedIds.size(), firstPage.getEstimatedTotalNumberOfResults(), "Count");
    }

    @Test
    void testNegatedSingleCriteriaOnOneToManyMatchesEntitiesWithoutMatchingElement() {
        var expectedIds = idsOfPersonsWithoutPhoneNumberContaining1();
        var result = pageOfPersonsWithoutPhoneNumberContaining1();
        assertFalse(expectedIds.isEmpty(), "Some persons have no phone number containing 1");
        assertEquals(expectedIds, result.stream().map(Person::getId).collect(toSet()), "Persons without matching phone number");
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testNegatedSingleCriteriaOnOneToManyCountsEntitiesWithoutMatchingElement() {
        assertEquals(
            idsOfPersonsWithoutPhoneNumberContaining1().size(), pageOfPersonsWithoutPhoneNumberContaining1().getEstimatedTotalNumberOfResults(), "Count"
        );
    }

    private Set<Long> idsOfPersonsWithoutPhoneNumberContaining1() {
        return personService().getAllWithPhones().stream()
            .filter(person -> person.getPhones().stream().noneMatch(phone -> phone.getNumber().contains("1")))
            .map(Person::getId).collect(toSet());
    }

    private PartialResultList<Person> pageOfPersonsWithoutPhoneNumberContaining1() {
        return personService().getPage(Page.with().allMatch(Map.of("phones.number", Not.value(Like.contains("1")))).build(), true);
    }

    @Test
    void testSingleEntityCriteriaOnOneToMany() {
        var person = personService().getById(1L);
        var result = personService().getPage(Page.with().allMatch(Map.of("phones.owner", person)).build(), true);
        assertEquals(List.of(person.getId()), result.stream().map(Person::getId).toList(), "Only the owner of the phones");
        assertEquals(1, result.getEstimatedTotalNumberOfResults(), "Count");
    }

    @Test
    void testElementCollectionFilterOnPathBackThroughRootEntity() {
        var direct = personService().getPage(Page.with().allMatch(Map.of("groups", Group.USER)).build(), true);
        var viaPhoneOwner = personService().getPage(Page.with().allMatch(Map.of("phones.owner.groups", Group.USER)).build(), true);
        assertFalse(direct.isEmpty(), "Some persons have group USER");
        assertEquals(direct.stream().map(Person::getId).distinct().count(), direct.size(), "Each person is returned once");
        assertEquals(direct.getEstimatedTotalNumberOfResults(), direct.size(), "Count matches result size");
        assertEquals(
            direct.stream().map(Person::getId).toList(),
            viaPhoneOwner.stream().map(Person::getId).toList(),
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
        var result = pageWithMobilePhones();
        assertFalse(result.isEmpty(), "Some persons have MOBILE phones");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Filtered person has phones"));
        result.forEach(
            person -> person.getPhones().forEach(
                phone -> assertEquals(Phone.Type.MOBILE, phone.getType(), "Only MOBILE phones remain after in-memory filtering of postponed fetch")
            )
        );
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testPageWithPhonesFilteredByTypeSetCountsOnlyMatchingPersons() {
        assertTrue(pageWithMobilePhones().getEstimatedTotalNumberOfResults() < TOTAL_RECORDS, "Not all persons have MOBILE phones");
    }

    private PartialResultList<Person> pageWithMobilePhones() {
        return personService().getPageWithPhones(Page.with().range(0, 10).allMatch(Map.of("phones.type", Set.of(Phone.Type.MOBILE))).build(), true);
    }

    @Test
    void testPageWithPhonesFetchedWithoutTransaction() {
        var result = personService()
            .getPageWithPhonesWithoutTransaction(Page.with().range(0, 10).allMatch(Map.of("phones.number", Like.contains("11"))).build(), false);
        assertFalse(result.isEmpty(), "Some persons have phones with number containing 11");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Phones are fetched without transaction"));
        result.forEach(
            person -> person.getPhones().forEach(phone -> assertTrue(phone.getNumber().contains("11"), "Only phones with 11 in number remain"))
        );
    }

    @Test
    void testPageWithPhonesFetchedViaFetchAttribute() {
        var result = personService()
            .getPageWithPhonesViaFetchAttribute(Page.with().range(0, 10).allMatch(Map.of("phones.number", Like.contains("11"))).build(), false);
        assertFalse(result.isEmpty(), "Some persons have phones with number containing 11");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Phones are fetched via fetch attribute"));
        result.forEach(
            person -> person.getPhones().forEach(phone -> assertTrue(phone.getNumber().contains("11"), "Only phones with 11 in number remain"))
        );
    }

    @Test
    void testPageWithPhonesDetachesManagedEntities() {
        assertFalse(personService().isManagedAfterPageWithPhones(1L, Page.of(0, 10)), "Managed entity is detached after paged fetch");
    }

    @Test
    void testPageWithNestedFetch() {
        var result = personService().getPageWithPhoneOwners(Page.of(0, 10), false);
        assertEquals(10, result.size(), "Page has 10 records");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Phones are fetched"));
        result.forEach(person -> person.getPhones().forEach(phone -> assertNotNull(phone.getOwner().getEmail(), "Phone owners are fetched")));
    }

    @Test
    void testAllWithNestedFetch() {
        var result = personService().getPageWithPhoneOwners(Page.ALL, false);
        assertEquals(TOTAL_RECORDS, result.size(), "All records are returned");
        result.forEach(person -> person.getPhones().forEach(phone -> assertNotNull(phone.getOwner().getEmail(), "Phone owners are fetched")));
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
    void testPageOrderedByOneToManyAcrossPages() {
        var all = personService().getAllWithPhones();

        for (var ascending : List.of(true, false)) {
            Function<Person, String> key = person -> extreme(person.getPhones().stream().map(Phone::getNumber), ascending);
            BiFunction<Integer, Integer, Page> page = (offset, limit) -> Page.with().range(offset, limit).orderBy("phones.number", ascending).build();
            assertOrderedAcrossPages(all, key, ascending, (offset, limit) -> personService().getPage(page.apply(offset, limit), false));
            assertOrderedAcrossPages(all, key, ascending, (offset, limit) -> personService().getPageWithPhones(page.apply(offset, limit), false));
        }
    }

    @Test
    void testPageOrderedByOneToManyWithCriteriaOnIt() {
        var all = personService().getAllWithPhones();
        Function<Person, String> key = person -> extreme(person.getPhones().stream().map(Phone::getNumber).filter(number -> number.contains("11")), true);
        BiFunction<Integer, Integer, Page> page = (offset, limit) -> Page.with()
            .range(offset, limit).allMatch(Map.of("phones.number", Like.contains("11"))).orderBy("phones.number", true).build();
        assertOrderedAcrossPages(all, key, true, (offset, limit) -> personService().getPage(page.apply(offset, limit), false));
        assertOrderedAcrossPages(all, key, true, (offset, limit) -> personService().getPageWithPhones(page.apply(offset, limit), false));
    }

    @Test
    void testPageOrderedByElementCollectionNestedInOneToMany() {
        var all = personService().getAllWithGroups();
        Function<Person, String> key = person -> extreme(person.getGroups().stream().map(Group::name), true); // Every phone is owned by the person itself.
        BiFunction<Integer, Integer, Page> page = (offset, limit) -> Page.with().range(offset, limit).orderBy("phones.owner.groups", true).build();
        assertOrderedAcrossPages(all, key, true, (offset, limit) -> personService().getPage(page.apply(offset, limit), false));
    }

    @Test
    void testPageOrderedByOneToManyWithListAndNegatedCriteriaOnIt() {
        var all = personService().getAllWithPhones();
        Function<Person, String> key = person -> extreme(person.getPhones().stream().map(Phone::getNumber), true);

        BiFunction<Integer, Integer, Page> listPage = (offset, limit) -> Page.with()
            .range(offset, limit).allMatch(Map.of("phones.number", List.of(Like.startsWith("0")))).orderBy("phones.number", true).build();
        assertOrderedAcrossPages(all, key, true, (offset, limit) -> personService().getPage(listPage.apply(offset, limit), false));

        var withoutOne = all.stream().filter(person -> person.getPhones().stream().noneMatch(phone -> phone.getNumber().contains("1"))).toList();
        BiFunction<Integer, Integer, Page> negatedPage = (offset, limit) -> Page.with()
            .range(offset, limit).allMatch(Map.of("phones.number", Not.value(Like.contains("1")))).orderBy("phones.number", true).build();
        assertOrderedAcrossPages(withoutOne, key, true, (offset, limit) -> personService().getPage(negatedPage.apply(offset, limit), false));
    }

    /**
     * Ordering on a collection field must not drop entities which match the criteria on the collection but have no single element matching all of them, such as
     * an entity with only a MOBILE phone for "not both MOBILE and HOME", or an entity whose MOBILE phone and whose number with a 1 are on different phones.
     */
    @Test
    void testPageOrderedByOneToManyKeepsEntitiesWithoutMatchingElement() {
        var persons = personService().getAllWithPhones();
        var notBothMobileAndHome = Map.<String, Object>of("phones.type", Not.value(List.of(Phone.Type.MOBILE, Phone.Type.HOME)));
        assertTrue(
            persons.stream().map(OmniPersistenceIT::phoneTypes)
                .anyMatch(types -> types.equals(Set.of(Phone.Type.MOBILE)) || types.equals(Set.of(Phone.Type.HOME))),
            "Some person has only MOBILE or only HOME phones"
        );
        assertKeepsAllMatchingEntities("phones.number", notBothMobileAndHome);

        var mobileAndNumberWith1 = Map.<String, Object>of("phones.type", Phone.Type.MOBILE, "phones.number", Like.contains("1"));
        assertTrue(
            persons.stream().map(Person::getPhones).anyMatch(
                phones -> phones.stream().anyMatch(phone -> phone.getType() == Phone.Type.MOBILE)
                    && phones.stream().anyMatch(phone -> phone.getNumber().contains("1"))
                    && phones.stream().noneMatch(phone -> phone.getType() == Phone.Type.MOBILE && phone.getNumber().contains("1"))
            ),
            "Some person has a MOBILE phone and a number with a 1 on different phones"
        );
        assertKeepsAllMatchingEntities("phones.number", mobileAndNumberWith1);
    }

    /**
     * Ordering on an enum field of a <code>@OneToMany</code> and on an <code>@ElementCollection</code>, with criteria on the same collection, must return each
     * matching entity once.
     */
    @Test
    void testPageOrderedByEnumOfCollectionWithCriteriaOnIt() {
        assertKeepsAllMatchingEntities("phones.type", Map.of("phones.number", Like.contains("1")));
        assertKeepsAllMatchingEntities("groups", Map.of("groups", Not.value(Group.USER)));
    }

    private static Set<Phone.Type> phoneTypes(Person person) {
        return person.getPhones().stream().map(Phone::getType).collect(toSet());
    }

    private void assertKeepsAllMatchingEntities(String orderBy, Map<String, Object> criteria) {
        var expected = personService().getPage(Page.with().allMatch(criteria).build(), false).stream().map(Person::getId).sorted().toList();
        assertFalse(expected.isEmpty(), "Some entities match " + criteria);
        var ids = new ArrayList<Long>();

        for (var offset = 0;; offset += 10) {
            var persons = personService().getPage(Page.with().range(offset, 10).allMatch(criteria).orderBy(orderBy, true).build(), false);

            if (persons.isEmpty()) {
                break;
            }

            persons.forEach(person -> ids.add(person.getId()));
        }

        assertEquals(expected, ids.stream().sorted().toList(), "Each entity matching " + criteria + " is on exactly one page when ordered by " + orderBy);
    }

    @Test
    void testPageOrderedByOneToManyWithOptionalCriteria() {
        var matching = personService().getAllWithPhones().stream()
            .filter(person -> person.getEmail().contains("1") || person.getPhones().stream().anyMatch(phone -> phone.getNumber().contains("11")))
            .toList();
        Function<Person, String> key = person -> extreme(person.getPhones().stream().map(Phone::getNumber), true);
        var criteria = Map.<String, Object>of("phones.number", Like.contains("11"), "email", Like.contains("1"));
        BiFunction<Integer, Integer, Page> page = (offset, limit) -> Page.with().range(offset, limit).anyMatch(criteria).orderBy("phones.number", true).build();
        assertOrderedAcrossPages(matching, key, true, (offset, limit) -> personService().getPage(page.apply(offset, limit), false));
    }

    @Test
    void testPageOrderOnElementCollectionWithOptionalCriteriaIsStable() {
        var criteria = Map.<String, Object>of("groups", Group.USER, "email", Like.contains("1"));
        var ids = Stream.of(0, 10)
            .flatMap(offset -> personService().getPage(Page.with().range(offset, 10).anyMatch(criteria).orderBy("groups", true).build(), false).stream())
            .map(Person::getId).toList();
        assertEquals(20, ids.size(), "Two full pages");
        assertEquals(ids.size(), ids.stream().distinct().count(), "Each entity is on only one page");
    }

    @Test
    void testPageOrderedByElementCollectionAcrossPages() {
        var all = personService().getAllWithGroups();

        for (var ascending : List.of(true, false)) {
            Function<Person, String> key = person -> extreme(person.getGroups().stream().map(Group::name), ascending); // @Enumerated(STRING)
            BiFunction<Integer, Integer, Page> page = (offset, limit) -> Page.with().range(offset, limit).orderBy("groups", ascending).build();
            assertOrderedAcrossPages(all, key, ascending, (offset, limit) -> personService().getPage(page.apply(offset, limit), false));
            assertOrderedAcrossPages(all, key, ascending, (offset, limit) -> personService().getPageWithGroups(page.apply(offset, limit), false));
        }
    }

    private static <K extends Comparable<K>> K extreme(Stream<K> values, boolean smallest) {
        return values.reduce((left, right) -> (left.compareTo(right) <= 0) == smallest ? left : right).orElse(null);
    }

    private static <K extends Comparable<K>> void assertOrderedAcrossPages(
        List<Person> all, Function<Person, K> key, boolean ascending, BiFunction<Integer, Integer, List<Person>> pageLoader
    )
    {
        var keys = new HashMap<Long, K>();
        all.forEach(person -> keys.put(person.getId(), key.apply(person)));
        var ids = Stream.concat(pageLoader.apply(0, 10).stream(), pageLoader.apply(10, 10).stream()).map(Person::getId).toList();
        assertEquals(ids.size(), ids.stream().distinct().count(), "Each entity is on only one page");

        Comparator<K> order = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        var actualKeys = ids.stream().map(keys::get).toList();
        var expectedKeys = keys.values().stream().filter(Objects::nonNull).sorted(order).limit(ids.size()).toList();
        assertEquals(expectedKeys, actualKeys, "Entities are ordered across pages by the " + (ascending ? "smallest" : "largest") + " element");
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
    void testPhonePageWithOwnersSortedByOwnerEmailAndOptionalCriteria() {
        var page = Page.with().range(0, 10).orderBy("owner.email", true).anyMatch(Map.of("owner.email", Like.contains("@"))).build();
        var result = phoneService().getPageWithOwners(page, false);
        assertEquals(10, result.size(), "Page has 10 phones");
        var emails = result.stream().map(p -> p.getOwner().getEmail()).toList();
        assertEquals(emails.stream().sorted().toList(), emails, "Phones are sorted ascending by owner email and owners are fetched");
    }

    @Test
    void testPhonePageWithOwnerPhonesAndPagination() {
        var result = phoneService().getPageWithOwnerPhones(Page.of(0, 10), true);
        assertEquals(10, result.size(), "Page has 10 phones, not fewer due to join row inflation");
        result.forEach(p -> assertTrue(p.getOwner().getPhones().contains(p), "Owner phones are populated after detachment"));
    }

    @Test
    void testPhonePageFilteredByTransientEmail() {
        var expectedEmail = "name0@example.com";
        var result = phoneService().getPageWithOwners(Page.with().allMatch(Map.of("email", expectedEmail)).build(), false);
        assertEquals(TOTAL_PHONES_PER_PERSON_0, result.size(), "Only phones of person 0 match the owner email filter");
        result.forEach(p -> assertEquals(expectedEmail, p.getEmail(), "All matching phones belong to the expected owner"));
    }

    // Page with combined criteria and ordering -----------------------------------------------------------------------

    @Test
    void testPageWithLikeOnLongId() {
        assertPage(personService().list(), person -> likes("2", person.getId()), Map.of("id", Like.contains("2")), Map.of(), personService()::getPage, true);
    }

    @Test
    void testPageWithLikeOnOrdinalEnum() {
        assertPage(
            personService().list(), person -> likes("fem", person.getGender()), Map.of("gender", Like.contains("fem")), Map.of(), personService()::getPage, true
        );
    }

    @Test
    void testPageWithLikeOnLocalDate() {
        assertPage(
            personService().list(), person -> likes("-01-", person.getDateOfBirth()), Map.of("dateOfBirth", Like.contains("-01-")), Map.of(),
            personService()::getPage, true
        );
    }

    @Test
    void testPageWithLikeOnStringEnum() {
        assertPage(
            phoneService().getAllWithOwners(), phone -> likes("mob", phone.getType()), Map.of("type", Like.contains("mob")), Map.of(),
            phoneService()::getPageWithOwners, true
        );
    }

    @Test
    void testPageWithLikeOnOrdinalEnumOfManyToOne() {
        assertPage(
            phoneService().getAllWithOwners(), phone -> likes("fem", phone.getOwner().getGender()), Map.of("owner.gender", Like.contains("fem")), Map.of(),
            phoneService()::getPageWithOwners, true
        );
    }

    @Test
    void testPageWithLikeOnTransientField() {
        assertPage(
            phoneService().getAllWithOwners(), phone -> likes("name1", phone.getEmail()), Map.of("email", Like.contains("name1")), Map.of(),
            phoneService()::getPageWithOwners, true
        );
    }

    @Test
    void testPageWithLikeOnStringEnumOfOneToMany() {
        assertPage(
            personService().getAllWithPhones(), person -> phoneTypes(person).stream().anyMatch(type -> likes("mob", type)),
            Map.of("phones.type", Like.contains("mob")), Map.of(), personService()::getPageWithPhones, true
        );
    }

    @Test
    void testPageWithLikeMatchesWildcardCharactersLiterally() {
        for (var term : List.of("name1_@", "name1%@", "[n]ame1@", "\\name1")) { // The [ only differs on SQL Server.
            var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of("email", Like.contains(term))).build(), true);
            assertEquals(List.of(), page, "No email contains " + term + " literally");
            assertEquals(0, page.getEstimatedTotalNumberOfResults(), "Count of emails containing " + term + " literally");
        }
    }

    @Test
    void testPageWithLikePattern() {
        assertPage(
            personService().list(), person -> Like.pattern("name1_@%").applies(person.getEmail()), Map.of("email", Like.pattern("name1_@%")), Map.of(),
            personService()::getPage, true
        );

        for (var pattern : List.of("[n]ame1_@%", "name1\\")) { // The [ only differs on SQL Server.
            var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of("email", Like.pattern(pattern))).build(), true);
            assertEquals(List.of(), page, "No email matches " + pattern);
            assertEquals(0, page.getEstimatedTotalNumberOfResults(), "Count of emails matching " + pattern);
        }
    }

    @Test
    void testPageWithNullCriteria() {
        assertPage(personService().list(), person -> person.nickname() == null, singletonMap("nickname", null), Map.of(), personService()::getPage, true);
        assertPage(
            personService().list(), person -> person.nickname() != null, singletonMap("nickname", Not.value(null)), Map.of(), personService()::getPage, true
        );
        for (var not : List
            .of(
                Not.value(Like.contains("nick1")), Not.value("nick1"), Not.value(List.of("nick1", "nick3")), Not.value(Arrays.asList("nick1", null)),
                Not.value(List.of(Arrays.asList((Object) null))), Not.value(List.of(Not.value("nick1"))), Not.value(List.of(Not.value(null))),
                Not.value(List.of("nick1", Not.value("nick3"))), Not.value(Arrays.asList(Not.value(null), "nick1"))
            )) {
            assertPage(personService().list(), person -> not.applies(person.nickname()), Map.of("nickname", not), Map.of(), personService()::getPage, true);
        }
    }

    @Test
    void testPageWithStringValuesOfEnum() {
        var all = personService().getAllWithGroups();
        assertPage(all, person -> person.getGender() == Gender.FEMALE, Map.of("gender", "FEMALE"), Map.of(), personService()::getPageWithGroups, true);
        assertPage(
            all, person -> person.getGender() != Gender.TRANS && person.getGender() != Gender.OTHER, Map.of("gender", Set.of("MALE", "FEMALE")), Map.of(),
            personService()::getPageWithGroups, true
        );
        assertPage(
            all, person -> person.getGroups().containsAll(Set.of(Group.USER, Group.DEVELOPER)), Map.of("groups", Set.of("USER", "DEVELOPER")), Map.of(),
            personService()::getPageWithGroups, true
        );
    }

    @Test
    void testPageWithRequiredAndOptionalCriteria() {
        assertPage(
            personService().list(),
            person -> likes("2", person.getId())
                && Stream.of(person.getId(), person.getEmail(), person.getGender(), person.getDateOfBirth()).anyMatch(value -> likes("1", value)),
            Map.of("id", Like.contains("2")), likeOnAll("1", "id", "email", "gender", "dateOfBirth"), personService()::getPage, true
        );
    }

    @Test
    void testPageWithRequiredAndOptionalCriteriaOnOneToMany() {
        assertPage(
            personService().getAllWithPhones(),
            person -> likes("1", person.getId())
                && (likes("name2", person.getEmail())
                    || person.getPhones().stream().anyMatch(phone -> likes("11", phone.getNumber()) || likes("mob", phone.getType()))),
            Map.of("id", Like.contains("1")),
            Map.of("email", Like.contains("name2"), "phones.number", Like.contains("11"), "phones.type", Like.contains("mob")),
            personService()::getPageWithPhones, true
        );
    }

    @Test
    void testPageWithRequiredAndOptionalCriteriaOnElementCollection() {
        assertRequiredAndOptionalCriteriaOnElementCollection(false);
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testPageWithRequiredAndOptionalCriteriaOnElementCollectionCount() {
        assertRequiredAndOptionalCriteriaOnElementCollection(true);
    }

    private void assertRequiredAndOptionalCriteriaOnElementCollection(boolean count) {
        assertPage(
            personService().getAllWithGroups(),
            person -> likes("1", person.getId()) && (likes("name2", person.getEmail()) || person.getGroups().stream().anyMatch(group -> likes("dev", group))),
            Map.of("id", Like.contains("1")), Map.of("email", Like.contains("name2"), "groups", Like.contains("dev")), personService()::getPageWithGroups, count
        );
    }

    @Test
    void testPageWithRequiredAndOptionalCriteriaOnManyToOne() {
        assertPage(
            phoneService().getAllWithOwners(),
            phone -> likes("1", phone.getNumber())
                && (likes("mob", phone.getType()) || likes("name1", phone.getEmail()) || likes("fem", phone.getOwner().getGender())),
            Map.of("number", Like.contains("1")), Map.of("type", Like.contains("mob"), "email", Like.contains("name1"), "owner.gender", Like.contains("fem")),
            phoneService()::getPageWithOwners, true
        );
    }

    @Test
    void testPageWithRequiredCriteriaOfDifferentTypes() {
        var emailStart = Like.startsWith("name1");
        assertPage(
            personService().list(),
            person -> person.getId() >= MIN_ID && person.getId() <= MAX_ID && emailStart.applies(person.getEmail()) && person.getGender() != Gender.OTHER
                && person.getDateOfBirth().isBefore(MAX_DATE_OF_BIRTH),
            Map.of(
                "id", Between.range(MIN_ID, MAX_ID), "email", emailStart, "gender", Not.value(Gender.OTHER), "dateOfBirth", Order.lessThan(MAX_DATE_OF_BIRTH)
            ),
            Map.of(), personService()::getPage, true
        );
    }

    @Test
    void testPageWithIdCriteriaAsString() {
        var ids = personService().list().stream().map(Person::getId).sorted().limit(STRING_IDS).toList();
        var stringIds = ids.stream().map(String::valueOf).toList();
        var page = personService().getPage(Page.with().range(0, stringIds.size()).allMatch(Map.of(Page.ID, stringIds)).build(), false);
        assertEquals(Set.copyOf(ids), page.stream().map(Person::getId).collect(toSet()), "Persons by IDs as strings");
        var single = personService().getPage(Page.with().range(0, 1).allMatch(Map.of(Page.ID, List.of(stringIds.get(0)))).build(), false);
        assertEquals(List.of(ids.get(0)), single.stream().map(Person::getId).toList(), "Person by ID as string");
    }

    @Test
    void testPageWithUnparseableIdCriteriaMatchesNothing() {
        var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of(Page.ID, List.of("abc"))).build(), true);
        assertEquals(List.of(), page, "No person has ID abc");
        assertEquals(0, page.getEstimatedTotalNumberOfResults(), "Count of persons having ID abc");
    }

    @Test
    void testPageWithNegatedUnparseableIdCriteriaMatchesEverything() {
        var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of(Page.ID, Not.value(List.of("abc")))).build(), true);
        assertEquals(TOTAL_RECORDS, page.getEstimatedTotalNumberOfResults(), "Count of persons not having ID abc");
    }

    @Test
    void testPageWithEmptyMultiValuedCriteriaImposesNoRestriction() {
        var allPhones = personService().getAllWithPhones().stream().collect(toMap(Person::getId, person -> Set.copyOf(person.getPhones())));

        for (var field : List.of("gender", "phones.type", "groups")) {
            for (var value : List.of(Set.of(), Not.value(Set.of()), List.of(Set.of()))) {
                var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of(field, value)).build(), true);
                assertEquals(TOTAL_RECORDS, page.getEstimatedTotalNumberOfResults(), "Count of persons matching " + value + " on " + field);
                assertEquals(ROWS_PER_PAGE, page.size(), "Page of persons matching " + value + " on " + field);
                var pageWithPhones = personService().getPageWithPhones(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of(field, value)).build(), false);
                assertEquals(ROWS_PER_PAGE, pageWithPhones.size(), "Page with phones of persons matching " + value + " on " + field);
                pageWithPhones.forEach(
                    person -> assertEquals(
                        allPhones.get(person.getId()), Set.copyOf(person.getPhones()), "All fetched phones of " + person + " matching " + value + " on " + field
                    )
                );
            }
        }
    }

    @Test
    void testPageWithUnparseableItemOfMultiValuedCriteriaOnCollectionMatchesNothing() {
        for (var criteria : unparseableItemsOfMultiValuedCriteriaOnCollection()) {
            var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(criteria).build(), false);
            assertEquals(List.of(), page, "No person matches " + criteria);
        }
    }

    @Test
    void testPageWithUnparseableItemOfMultiValuedCriteriaOnCollectionCountsNothing() {
        for (var criteria : unparseableItemsOfMultiValuedCriteriaOnCollection()) {
            if (!isOpenJPA() || criteria.equals(UNPARSEABLE_PLAIN_ITEM_OF_ELEMENT_COLLECTION)) { // See OPENJPA_COUNT.
                var page = personService().getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(criteria).build(), true);
                assertEquals(0, page.getEstimatedTotalNumberOfResults(), "Count of persons matching " + criteria);
            }
        }
    }

    private static List<Map<String, Object>> unparseableItemsOfMultiValuedCriteriaOnCollection() {
        return List.of(
            UNPARSEABLE_PLAIN_ITEM_OF_ELEMENT_COLLECTION, Map.of("phones.type", List.of("MOBILE", "abc")), Map.of("groups", List.of("USER", Not.value("abc"))),
            Map.of("phones.type", List.of("MOBILE", Not.value("abc")))
        );
    }

    @Test
    void testPageOrderedByCollectionWithUnparseableCriteriaOnItMatchesNothing() {
        var page = personService()
            .getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of("phones.type", "abc")).orderBy("phones.number", true).build(), true);
        assertEquals(List.of(), page, "No person has a phone of type abc");
        assertEquals(0, page.getEstimatedTotalNumberOfResults(), "Count of persons having a phone of type abc");
    }

    @Test
    void testPageWithMultiValuedCriteriaOnOneToMany() {
        assertMultiValuedCriteriaOnOneToMany(false);
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testPageWithMultiValuedCriteriaOnOneToManyCount() {
        assertMultiValuedCriteriaOnOneToMany(true);
    }

    private void assertMultiValuedCriteriaOnOneToMany(boolean count) {
        var all = personService().getAllWithPhones();

        for (var size = 1; size <= Phone.Type.values().length; size++) {
            var types = Set.of(Arrays.copyOf(Phone.Type.values(), size));
            assertPage(all, person -> phoneTypes(person).containsAll(types), Map.of("phones.type", types), Map.of(), personService()::getPageWithPhones, count);
        }
    }

    @Test
    void testPageWithPhonesFilteredByTypeListKeepsOnlyListedPhones() {
        var types = List.of(Phone.Type.MOBILE, Phone.Type.HOME);
        var result = personService().getPageWithPhones(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of("phones.type", types)).build(), false);
        assertFalse(result.isEmpty(), "Some persons have MOBILE and HOME phones");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Fetched phones of " + person));
        result.forEach(person -> assertTrue(types.containsAll(phoneTypes(person)), "Only listed phone types of " + person));
    }

    @Test
    void testPageWithPhonesFilteredByNegatedTypeSetKeepsAllPhones() {
        var allPhones = personService().getAllWithPhones().stream().collect(toMap(Person::getId, person -> Set.copyOf(person.getPhones())));
        var page = Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of("phones.type", Not.value(Set.of(Phone.Type.MOBILE, Phone.Type.HOME)))).build();
        var result = personService().getPageWithPhones(page, false);
        assertFalse(result.isEmpty(), "Some persons do not have both MOBILE and HOME phones");
        result.forEach(person -> assertEquals(allPhones.get(person.getId()), Set.copyOf(person.getPhones()), "All fetched phones of " + person));
    }

    @Test
    void testPageWithMultiValuedCriteriaOnElementCollection() {
        var all = personService().getAllWithGroups();

        for (var size = 1; size <= Group.values().length; size++) {
            var groups = Set.of(Arrays.copyOf(Group.values(), size));
            assertPage(all, person -> person.getGroups().containsAll(groups), Map.of("groups", groups), Map.of(), personService()::getPageWithGroups, true);
        }
    }

    @Test
    void testPageOrderedByFieldOfEachTypeWithCriteria() {
        var all = personService().list();
        Predicate<Person> expected = person -> likes("1", person.getEmail());
        var required = Map.<String, Object>of("email", Like.contains("1"));

        for (var ascending : List.of(true, false)) {
            assertOrderedPages(
                all, expected, required, ordering("email", ascending), byKey(Person::getEmail, ascending),
                person -> orderingValues("email", person.getEmail(), person), personService()::getPage
            );
            assertOrderedPages(
                all, expected, required, ordering("gender", ascending), byKey(Person::getGender, ascending),
                person -> orderingValues("gender", person.getGender(), person), personService()::getPage
            );
            assertOrderedPages(
                all, expected, required, ordering("dateOfBirth", ascending), byKey(Person::getDateOfBirth, ascending),
                person -> orderingValues("dateOfBirth", person.getDateOfBirth(), person), personService()::getPage
            );
        }
    }

    @Test
    void testPageOrderedByMultipleFields() {
        var ordering = new LinkedHashMap<String, Boolean>();
        ordering.put("gender", true);
        ordering.put("email", false);
        assertOrderedPages(
            personService().list(), person -> true, Map.of(), ordering, byKey(Person::getGender, true).thenComparing(byKey(Person::getEmail, false)),
            person -> Map.of("gender", person.getGender(), "email", person.getEmail(), Page.ID, person.getId()), personService()::getPage
        );
    }

    @Test
    void testPageOrderedByFieldOfManyToOneAndTransientField() {
        var all = phoneService().getAllWithOwners();
        Predicate<Phone> expected = phone -> likes("1", phone.getNumber());
        var required = Map.<String, Object>of("number", Like.contains("1"));

        for (var ascending : List.of(true, false)) {
            assertOrderedPages(
                all, expected, required, ordering("owner.email", ascending), byKey(Phone::getEmail, ascending),
                phone -> orderingValues("owner.email", phone.getEmail(), phone), phoneService()::getPageWithOwners
            );
            assertOrderedPages(
                all, expected, required, ordering("email", ascending), byKey(Phone::getEmail, ascending),
                phone -> orderingValues("email", phone.getEmail(), phone), phoneService()::getPageWithOwners
            );
        }
    }

    @Test
    void testPageOrderedByNullableFieldPagesByOrderingValuesAsByOffset() {
        for (var ascending : List.of(true, false)) {
            var direction = ascending ? "ascending" : "descending";
            BiFunction<Page.Builder, Boolean, PartialResultList<Person>> getPage = (builder, count) -> personService()
                .getPage(orderingOf(builder, ordering("nickname", ascending)).build(), count);
            var offsetPages = Stream.iterate(0, offset -> offset + ROWS_PER_PAGE).limit(TOTAL_RECORDS / ROWS_PER_PAGE)
                .flatMap(offset -> getPage.apply(Page.with().range(offset, ROWS_PER_PAGE), false).stream()).toList();
            assertEquals(TOTAL_RECORDS, offsetPages.size(), "All persons on offset pages ordered by nickname " + direction);

            for (var offset = ROWS_PER_PAGE; offset < TOTAL_RECORDS; offset += ROWS_PER_PAGE) {
                var expected = ids(offsetPages.subList(offset, offset + ROWS_PER_PAGE));
                var last = offsetPages.get(offset - 1);
                assertEquals(
                    expected, ids(getPage.apply(Page.with().range(0, ROWS_PER_PAGE, nicknameOrderingValues(last), false), false)),
                    "Page at " + offset + " after nickname " + last.nickname() + ", " + direction
                );

                if (offset >= 2 * ROWS_PER_PAGE) {
                    var lastOfPreviousPage = offsetPages.get(offset - ROWS_PER_PAGE - 1);
                    assertEquals(
                        expected,
                        ids(getPage.apply(Page.with().range(0, ROWS_PER_PAGE, nicknameOrderingValues(lastOfPreviousPage), ROWS_PER_PAGE, false), false)),
                        "Page at " + offset + " after nickname " + lastOfPreviousPage.nickname() + " plus a residual offset, " + direction
                    );
                }

                if (offset + ROWS_PER_PAGE < TOTAL_RECORDS) {
                    var firstOfNextPage = offsetPages.get(offset + ROWS_PER_PAGE);
                    assertEquals(
                        expected, ids(getPage.apply(Page.with().range(0, ROWS_PER_PAGE, nicknameOrderingValues(firstOfNextPage), true), false)),
                        "Page at " + offset + " reversed before nickname " + firstOfNextPage.nickname() + ", " + direction
                    );
                }
            }
        }
    }

    private static Map<String, Object> nicknameOrderingValues(Person person) {
        return orderingValues("nickname", person.nickname(), person);
    }

    @Test
    void testPageWithCriteriaAndOrderingOnOneToOne() {
        var all = personService().getAllWithAddress();
        assertPage(
            all, person -> likes("1", person.getId()) && (likes("name2", person.getEmail()) || likes("3", person.getAddress().getHouseNumber())),
            Map.of("id", Like.contains("1")), Map.of("email", Like.contains("name2"), "address.houseNumber", Like.contains("3")),
            personService()::getPageWithAddress, true
        );

        for (var ascending : List.of(true, false)) {
            assertOrderedPages(
                all, person -> likes("1", person.getAddress().getHouseNumber()), Map.of("address.houseNumber", Like.contains("1")),
                ordering("address.houseNumber", ascending),
                byKey(person -> person.getAddress().getHouseNumber(), ascending),
                person -> orderingValues("address.houseNumber", person.getAddress().getHouseNumber(), person),
                personService()::getPageWithAddress
            );
        }
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK_OR_OPENJPA, disabledReason = HIBERNATE_FORMULA)
    void testPageWithCriteriaAndOrderingOnFormula() {
        var all = personService().getAllWithAddress();
        Function<Person, String> addressString = person -> addressStringOf(person.getAddress());
        assertPage(
            all, person -> likes("street1 ", addressString.apply(person)), Map.of("address.string", Like.contains("street1 ")), Map.of(),
            personService()::getPageWithAddress, true
        );

        for (var ascending : List.of(true, false)) {
            assertOrderedPages(
                all, person -> likes("1", person.getId()), Map.of("id", Like.contains("1")), ordering("address.string", ascending),
                byKey(addressString, ascending),
                person -> orderingValues("address.string", addressString.apply(person), person), personService()::getPageWithAddress
            );
        }
    }

    private static String addressStringOf(Address address) {
        return address.getStreet() + " " + address.getHouseNumber() + ", " + address.getPostcode() + " " + address.getCity() + ", " + address.getCountry();
    }

    private static boolean likes(String term, Object value) {
        return Like.contains(term).applies(value);
    }

    private static Map<String, Object> likeOnAll(String term, String... fields) {
        return Stream.of(fields).collect(toMap(Function.identity(), field -> Like.contains(term)));
    }

    private static Map<String, Boolean> ordering(String field, boolean ascending) {
        return Map.of(field, ascending);
    }

    /**
     * Returns the ordering values of the given entity for the given field followed by its ID. The value may be <code>null</code>.
     */
    private static Map<String, Object> orderingValues(String field, Object value, BaseEntity<Long> entity) {
        var orderingValues = new LinkedHashMap<String, Object>();
        orderingValues.put(field, value);
        orderingValues.put(Page.ID, entity.getId());
        return orderingValues;
    }

    private static <E, K extends Comparable<? super K>> Comparator<E> byKey(Function<E, K> key, boolean ascending) {
        return ascending ? Comparator.comparing(key) : Comparator.comparing(key, Comparator.reverseOrder());
    }

    private static List<Long> ids(List<? extends BaseEntity<Long>> entities) {
        return entities.stream().map(BaseEntity::getId).toList();
    }

    /**
     * Asserts that the given page loader returns exactly the entities of <code>all</code> matching <code>expected</code>, each once, for the given required and
     * optional criteria, and that a limited first page returns only matching entities, each once. When <code>count</code> is true, also asserts that the count
     * of the limited first page equals the amount of matching entities.
     */
    private static <E extends BaseEntity<Long>> void assertPage(
        List<E> all, Predicate<E> expected, Map<String, Object> required, Map<String, Object> optional, BiFunction<Page, Boolean, PartialResultList<E>> getPage,
        boolean count
    )
    {
        var expectedIds = all.stream().filter(expected).map(E::getId).collect(toSet());
        assertFalse(expectedIds.isEmpty(), "Some entities match");
        assertTrue(expectedIds.size() < all.size(), "Not all entities match");
        var description = "entities matching " + required + " and any of " + optional;
        var ids = ids(getPage.apply(criteriaOf(Page.with(), required, optional).build(), false));
        assertEquals(ids.size(), ids.stream().distinct().count(), "Each of " + description + " is returned once");
        assertEquals(expectedIds, Set.copyOf(ids), description);

        var firstPage = getPage.apply(criteriaOf(Page.with().range(0, ROWS_PER_PAGE), required, optional).build(), count);
        var firstPageIds = ids(firstPage);
        assertEquals(
            Math.min(ROWS_PER_PAGE, expectedIds.size()), Set.copyOf(firstPageIds).size(), "First page of " + description + " is full and has each once"
        );
        assertTrue(expectedIds.containsAll(firstPageIds), "First page has only " + description);

        if (count) {
            assertEquals(expectedIds.size(), firstPage.getEstimatedTotalNumberOfResults(), "Count of " + description);
        }
    }

    /**
     * Asserts that the first pages of the entities of <code>all</code> matching <code>expected</code> are in the given order, followed by descending ID as tie
     * breaker: with offset paging, from the ordering values of the last entity of the previous page, from those ordering values plus a residual offset, and
     * reversed from the ordering values of the first entity of the next page. Also asserts the count of the first page.
     */
    private static <E extends BaseEntity<Long>> void assertOrderedPages(
        List<E> all, Predicate<E> expected, Map<String, Object> required, Map<String, Boolean> ordering, Comparator<E> order,
        Function<E, Map<String, Object>> orderingValues, BiFunction<Page, Boolean, PartialResultList<E>> getPage
    )
    {
        var expectedEntities = all.stream().filter(expected).sorted(order.thenComparing(E::getId, Comparator.reverseOrder())).toList();
        assertTrue(expectedEntities.size() > ORDERED_PAGES * ROWS_PER_PAGE, "More than " + ORDERED_PAGES + " pages of entities match");
        var expectedIds = ids(expectedEntities);
        var description = "entities matching " + required + " ordered by " + ordering;
        BiFunction<Page.Builder, Boolean, PartialResultList<E>> getOrderedPage = (builder, count) -> getPage
            .apply(orderingOf(criteriaOf(builder, required, Map.of()), ordering).build(), count);
        var offsetIds = new ArrayList<Long>();

        for (var offset = 0; offset < ORDERED_PAGES * ROWS_PER_PAGE; offset += ROWS_PER_PAGE) {
            var page = getOrderedPage.apply(Page.with().range(offset, ROWS_PER_PAGE), offset == 0);

            if (offset == 0) {
                assertEquals(expectedIds.size(), page.getEstimatedTotalNumberOfResults(), "Count of " + description);
            }

            offsetIds.addAll(ids(page));
        }

        assertEquals(expectedIds.subList(0, ORDERED_PAGES * ROWS_PER_PAGE), offsetIds, "Offset pages of " + description);

        var secondPage = expectedIds.subList(ROWS_PER_PAGE, 2 * ROWS_PER_PAGE);
        var lastOfFirstPage = orderingValues.apply(expectedEntities.get(ROWS_PER_PAGE - 1));
        assertEquals(
            secondPage, ids(getOrderedPage.apply(Page.with().range(ROWS_PER_PAGE, ROWS_PER_PAGE, lastOfFirstPage, false), false)),
            "Second page from the ordering values of the last of the first page of " + description
        );
        assertEquals(
            expectedIds.subList(2 * ROWS_PER_PAGE, 3 * ROWS_PER_PAGE),
            ids(getOrderedPage.apply(Page.with().range(2 * ROWS_PER_PAGE, ROWS_PER_PAGE, lastOfFirstPage, ROWS_PER_PAGE, false), false)),
            "Third page from the ordering values of the last of the first page plus a residual offset of " + description
        );
        var firstOfThirdPage = orderingValues.apply(expectedEntities.get(2 * ROWS_PER_PAGE));
        assertEquals(
            secondPage, ids(getOrderedPage.apply(Page.with().range(ROWS_PER_PAGE, ROWS_PER_PAGE, firstOfThirdPage, true), false)),
            "Second page reversed from the ordering values of the first of the third page of " + description
        );
    }

    private static Page.Builder criteriaOf(Page.Builder builder, Map<String, Object> required, Map<String, Object> optional) {
        return builder.allMatch(required).anyMatch(optional);
    }

    private static Page.Builder orderingOf(Page.Builder builder, Map<String, Boolean> ordering) {
        ordering.forEach(builder::orderBy);
        return builder.orderBy(Page.ID, false);
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

    @Test
    void testPageOfPersonCardsWithCriteriaAndOrderingOnExpression() {
        var all = personService().getAllPersonCards();

        for (var ascending : List.of(true, false)) {
            assertOrderedPages(
                all, card -> likes("street1", card.getAddressString()), Map.of("addressString", Like.contains("street1")), ordering("addressString", ascending),
                byKey(PersonCard::getAddressString, ascending), card -> orderingValues("addressString", card.getAddressString(), card),
                personService()::getPageOfPersonCards
            );
        }
    }

    @Test
    void testPageOfPersonCardsWithNegatedCriteriaOnExpressionAndAggregate() {
        var all = personService().getAllPersonCards();
        var notStreet1 = Not.value(Like.contains("street1"));
        assertPage(
            all, card -> notStreet1.applies(card.getAddressString()), Map.of("addressString", notStreet1), Map.of(), personService()::getPageOfPersonCards, true
        );
        var notTotalPhones = Not.value(TOTAL_PHONES);
        assertPage(
            all, card -> notTotalPhones.applies(card.getTotalPhones()), Map.of("totalPhones", notTotalPhones), Map.of(), personService()::getPageOfPersonCards,
            true
        );
    }

    @Test
    void testPageOfPersonCardsWithCriteriaAndOrderingOnAggregate() {
        var all = personService().getAllPersonCards();
        assertPage(
            all, card -> card.getTotalPhones() == TOTAL_PHONES, Map.of("totalPhones", String.valueOf(TOTAL_PHONES)), Map.of(),
            personService()::getPageOfPersonCards, true
        );

        for (var ascending : List.of(true, false)) {
            assertOrderedPages(
                all, card -> likes("1", card.getEmail()), Map.of("email", Like.contains("1")), ordering("totalPhones", ascending),
                byKey(PersonCard::getTotalPhones, ascending),
                card -> orderingValues("totalPhones", card.getTotalPhones(), card), personService()::getPageOfPersonCards
            );
        }
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

    // Overridden service methods -------------------------------------------------------------------------------------

    private List<Text> persistTrackedTexts(int amount) {
        var texts = new ArrayList<Text>(amount);

        for (var i = 0; i < amount; i++) {
            var text = new Text();
            trackingTextService().persist(text);
            texts.add(text);
        }

        trackingTextService().clearInvocations();
        return texts;
    }

    private void deleteTrackedTexts(List<Text> texts) {
        trackingTextService().delete(texts.stream().filter(text -> text.getId() != null && trackingTextService().exists(text)).toList());
    }

    private void assertTrackedInvocations(List<String> expected) {
        var actual = trackingTextService().getInvocations().stream().filter(name -> !TrackingTextService.ENTITY_MANAGER.equals(name)).toList();
        assertEquals(expected, actual, "Invoked overridden methods");
    }

    @Test
    void testSaveNewEntityInvokesOverriddenPersist() {
        trackingTextService().clearInvocations();
        var text = new Text();

        try {
            trackingTextService().save(text);
            assertTrackedInvocations(List.of("save", "persist"));
        }
        finally {
            deleteTrackedTexts(List.of(text));
        }
    }

    @Test
    void testSaveExistingEntityInvokesOverriddenUpdate() {
        var texts = persistTrackedTexts(1);

        try {
            trackingTextService().save(texts.get(0));
            assertTrackedInvocations(List.of("save", "update"));
        }
        finally {
            deleteTrackedTexts(texts);
        }
    }

    @Test
    void testSaveAndFlushInvokesOverriddenSave() {
        trackingTextService().clearInvocations();
        var text = new Text();

        try {
            trackingTextService().saveAndFlush(text);
            assertTrackedInvocations(List.of("save", "persist"));
        }
        finally {
            deleteTrackedTexts(List.of(text));
        }
    }

    @Test
    void testUpdateAndFlushInvokesOverriddenUpdate() {
        var texts = persistTrackedTexts(1);

        try {
            trackingTextService().updateAndFlush(texts.get(0));
            assertTrackedInvocations(List.of("update"));
        }
        finally {
            deleteTrackedTexts(texts);
        }
    }

    @Test
    void testUpdateEntitiesInvokesOverriddenUpdatePerEntity() {
        var texts = persistTrackedTexts(2);

        try {
            trackingTextService().update(texts);
            assertTrackedInvocations(List.of("update", "update"));
        }
        finally {
            deleteTrackedTexts(texts);
        }
    }

    @Test
    void testDeleteEntitiesInvokesOverriddenDeletePerEntity() {
        var texts = persistTrackedTexts(2);

        try {
            trackingTextService().delete(texts);
            assertTrackedInvocations(List.of("delete", "delete"));
        }
        finally {
            deleteTrackedTexts(texts);
        }
    }

    @Test
    void testSoftDeleteAndUndeleteEntitiesInvokeOverriddenMethodsPerEntity() {
        var texts = persistTrackedTexts(2);

        try {
            trackingTextService().softDelete(texts);
            trackingTextService().softUndelete(texts);
            assertTrackedInvocations(List.of("softDelete", "softDelete", "softUndelete", "softUndelete"));
        }
        finally {
            deleteTrackedTexts(texts);
        }
    }

    @Test
    void testFindByIdInvokesOverriddenGetById() {
        var texts = persistTrackedTexts(1);

        try {
            assertTrue(trackingTextService().findById(texts.get(0).getId()).isPresent(), "Text found");
            assertTrackedInvocations(List.of("getById"));
        }
        finally {
            deleteTrackedTexts(texts);
        }
    }

    @Test
    void testListInvokesOverriddenList() {
        trackingTextService().clearInvocations();
        trackingTextService().list();
        assertTrackedInvocations(List.of("list"));
    }

    @Test
    void testGetPageInvokesOverriddenPageCallbacks() {
        trackingTextService().clearInvocations();
        trackingTextService().getPage(Page.of(0, 10), true);
        assertTrackedInvocations(List.of("beforePage", "onPage", "onPage", "afterPage"));
    }

    @Test
    void testActionsUseOverriddenEntityManager() {
        trackingTextService().clearInvocations();
        trackingTextService().getById(1L);
        assertTrue(trackingTextService().getInvocations().contains(TrackingTextService.ENTITY_MANAGER), "Overridden getEntityManager() invoked");
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
        assertEquals(getTestDatabase(), configService().getDatabase(), "Test database");
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
            .map(change -> change.getEntityId() + ": " + change.getOldValue() + " -> " + change.getNewValue())
            .toList();
    }

    @Test
    void testAuditTracksChangesOfSameEntityInConcurrentPersistenceContexts() {
        var config = persistAuditedConfig("audit.concurrent", "original");
        configService().updateValueWhileConcurrentlyUpdated(config.getId(), "outer", () -> plainConfigUpdater.updateValue(config.getId(), "inner"));
        assertEquals(
            List.of(config.getId() + ": original -> inner", config.getId() + ": original -> outer"), auditedValueChanges(),
            "Both changes were audited against their own snapshot"
        );
    }

    @Test
    void testAuditTracksEachChangeOfSameEntityInSamePersistenceContext() {
        var config = persistAuditedConfig("audit.twice", "original");
        configService().updateValueTwice(config.getId(), "first", "second");
        assertEquals(
            List.of(config.getId() + ": original -> first", config.getId() + ": first -> second"), auditedValueChanges(), "Each flushed change was audited"
        );
    }

    @Test
    void testAuditTracksChangeOutsideService() {
        var config = persistAuditedConfig("audit.plain", "original");
        plainConfigUpdater.updateValue(config.getId(), "plain");
        assertEquals(List.of(config.getId() + ": original -> plain"), auditedValueChanges(), "Change via plain entity manager was audited");
    }

    @Test
    void testAuditTracksValueChange() {
        var config = persistAuditedConfig("audit.key", "original");
        configService().updateValue(config.getId(), "modified");
        assertEquals(List.of(config.getId() + ": original -> modified"), auditedValueChanges(), "Value change was audited");
    }

    @Test
    void testAuditTracksValueChangeOfDetachedEntity() {
        var config = persistAuditedConfig("audit.detached", "original");
        var detachedConfig = configService().getById(config.getId());
        detachedConfig.setValue("modified");
        configService().update(detachedConfig);
        assertEquals(List.of(config.getId() + ": original -> modified"), auditedValueChanges(), "Value change of detached entity was audited");
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
