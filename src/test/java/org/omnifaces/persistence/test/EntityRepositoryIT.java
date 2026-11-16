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

import static jakarta.persistence.criteria.JoinType.LEFT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.OmniPersistenceIT.ECLIPSELINK;
import static org.omnifaces.persistence.test.OmniPersistenceIT.ECLIPSELINK_OR_OPENJPA;
import static org.omnifaces.persistence.test.OmniPersistenceIT.OPENJPA;
import static org.omnifaces.persistence.test.OmniPersistenceIT.OPENJPA_COUNT;
import static org.omnifaces.persistence.test.OmniPersistenceIT.PROFILE_ID;
import static org.omnifaces.persistence.test.Serializations.serializeAndDeserialize;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.ROWS_PER_PAGE;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.TOTAL_RECORDS;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.persistence.TransactionRequiredException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.VersionedBaseEntity;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService.CriteriaQueryBuilder;
import org.omnifaces.persistence.service.BaseEntityService.QueryBuilder;
import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.persistence.test.model.Article;
import org.omnifaces.persistence.test.model.Attachment;
import org.omnifaces.persistence.test.model.Category;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Membership;
import org.omnifaces.persistence.test.model.MembershipId;
import org.omnifaces.persistence.test.model.Note;
import org.omnifaces.persistence.test.model.Period;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Remark;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.EntityEvents;
import org.omnifaces.persistence.test.service.StartupServiceEJB;
import org.omnifaces.persistence.test.service.TestAuditListener;
import org.omnifaces.persistence.test.service.TransactionService;
import org.omnifaces.persistence.test.service.TransactionServiceCDI;
import org.omnifaces.persistence.test.service.TransactionServiceEJB;
import org.omnifaces.utils.collection.PartialResultList;

/**
 * Integration tests for an injected {@link EntityRepository}. Reading actions run without a transaction, modifying actions run within a transaction of
 * {@link TransactionService}. {@link Article} and {@link Category} do not extend from {@link BaseEntity} and their ID is not named <code>id</code>.
 */
@ExtendWith(ArquillianExtension.class)
public class EntityRepositoryIT {

    private static final String TITLE_PREFIX = "plain-";
    private static final Instant PAST_LAST_MODIFIED = Instant.parse("2000-01-01T00:00:00Z");
    private static final String ECLIPSELINK_ENUM_COLLECTION = "EclipseLink maps an enum element collection without @Enumerated to a VARCHAR column, which PostgreSQL does not compare to the bound ordinal";
    private static final Map<String, Object> TITLED = Map.of("title", Like.startsWith(TITLE_PREFIX));
    private static final String MANAGED_ID_IMMUTABLE = "EclipseLink and OpenJPA reject modifying the ID of a managed entity, so delete() does not unset it";
    private static final String LOADED_REFERENCE = "EclipseLink returns an already loaded instance from EntityManager#getReference()";

    @Deployment
    public static WebArchive createDeployment() {
        return OmniPersistenceIT.createDeployment(EntityRepositoryIT.class, StartupServiceEJB.class);
    }

    @Inject
    private EntityRepository<Long, Person> personRepository;
    @Inject
    private EntityRepository<Long, Text> textRepository;
    @Inject
    private EntityRepository<Long, Article> articleRepository;
    @Inject
    private EntityRepository<String, Category> categoryRepository;
    @Inject
    private EntityRepository<Long, Phone> phoneRepository;
    @Inject
    private EntityRepository<Long, Remark> remarkRepository;
    @Inject
    private EntityRepository<Long, Attachment> attachmentRepository;
    @Inject
    private EntityRepository<MembershipId, Membership> membershipRepository;
    @Inject
    private EntityRepository<String, Note> noteRepository;
    @Inject
    private TransactionServiceCDI transactionServiceCDI;
    @EJB
    private TransactionServiceEJB transactionServiceEJB;
    @Inject
    private EntityEvents entityEvents;

    // BaseEntity -----------------------------------------------------------------------------------------------------

    @Test
    void testInjectedRepositoryHasTypeArgumentsOfInjectionPoint() {
        assertEquals(Long.class, personRepository.getIdentifierType(), "Identifier type");
        assertEquals(Person.class, personRepository.getEntityType(), "Entity type");
        assertTrue(personRepository.isGeneratedId(), "Generated ID");
    }

    @Test
    void testGetByIdAndList() {
        assertNotNull(personRepository.getById(1L), "Existing person");
        assertNull(personRepository.getById(0L), "Non-existing person");
        assertEquals(TOTAL_RECORDS, personRepository.list().size(), "All persons");
    }

    @Test
    void testGetPageWithLikeCriteria() {
        var result = personRepository.getPage(Page.with().allMatch(Map.of("email", Like.contains("e99@e"))).build(), true);
        assertEquals(1, result.size(), "LIKE contains matches e99@e");
        assertEquals(1, result.getEstimatedTotalNumberOfResults(), "Count of LIKE contains matches e99@e");
    }

    @Test
    void testGetPageOrderedByIdOfOptionalManyToOnePagesByOrderingValuesAsByOffset() {
        withArticlesInOptionalCategories(articles -> {
            for (var fetchFields : List.of(new String[] { "category" }, new String[0])) {
                for (var ascending : List.of(true, false)) {
                    var description = (ascending ? "ascending" : "descending") + " with fetch fields " + List.of(fetchFields);
                    Function<Page.Builder, List<Article>> getPage = builder -> articleRepository
                        .getPage(builder.allMatch(TITLED).orderBy("category.slug", ascending).orderBy("id", false).build(), false, fetchFields);
                    var all = getPage.apply(Page.with());
                    assertEquals(articles.size(), all.size(), "Articles with and without category " + description);

                    for (var index = 1; index < all.size(); index++) {
                        var last = all.get(index - 1);
                        var orderingValues = new LinkedHashMap<String, Object>();
                        orderingValues.put("category.slug", last.getCategory() == null ? null : last.getCategory().getSlug());
                        orderingValues.put("id", last.getCode());
                        assertEquals(
                            codesOf(all.subList(index, index + 1)), codesOf(getPage.apply(Page.with().range(0, 1, orderingValues, false))),
                            "Article after " + orderingValues + " " + description
                        );
                    }
                }
            }
        });
    }

    /**
     * An ordering on an <code>@ElementCollection</code> places the entities without elements where the database sorts <code>NULL</code>, see
     * {@link Database#sortsNullsLow()}, also with a fetch of the collection.
     */
    @Test
    void testGetPageOrderedByElementCollectionPlacesArticlesWithoutTagsAsNulls() {
        var articles = new ArrayList<Article>();

        try {
            articles.add(persistArticle("b", null, "tag-b"));
            articles.add(persistArticle("a", null, "tag-a"));
            articles.add(persistArticle("untagged", null));
            var tagged = List.of(articles.get(1).getCode(), articles.get(0).getCode());
            var untagged = List.of(articles.get(2).getCode());
            var nullsLow = articleRepository.getDatabase().sortsNullsLow();
            var ascending = new ArrayList<>(nullsLow ? untagged : tagged);
            ascending.addAll(nullsLow ? tagged : untagged);
            var descending = new ArrayList<>(ascending);
            Collections.reverse(descending);

            for (var fetchFields : List.of(new String[] { "tags" }, new String[0])) {
                assertEquals(ascending, codesOf(articlesOrderedByTags(true, fetchFields)), "Ascending with fetch fields " + List.of(fetchFields));
                assertEquals(descending, codesOf(articlesOrderedByTags(false, fetchFields)), "Descending with fetch fields " + List.of(fetchFields));
            }
        }
        finally {
            deleteArticles(articles);
        }
    }

    private PartialResultList<Article> articlesOrderedByTags(boolean ascending, String... fetchFields) {
        return articleRepository.getPage(Page.with().allMatch(TITLED).orderBy("tags", ascending).build(), false, fetchFields);
    }

    @Test
    void testGetPageWithCriteriaOnPropertyOfOptionalManyToOne() {
        withArticlesInOptionalCategories(articles -> {
            var withoutCategory = new HashMap<String, Object>(TITLED);
            withoutCategory.put("category.name", null);
            var pageWithoutCategory = articleRepository.getPage(Page.with().allMatch(withoutCategory).build(), true);
            assertEquals(Set.of(articles.get(1).getCode(), articles.get(3).getCode()), Set.copyOf(codesOf(pageWithoutCategory)), "Articles without category");
            assertEquals(2, pageWithoutCategory.getEstimatedTotalNumberOfResults(), "Count of articles without category");

            var notFirst = new HashMap<String, Object>(TITLED);
            notFirst.put("category.name", Not.value("First"));
            var pageNotFirst = articleRepository.getPage(Page.with().allMatch(notFirst).build(), true);
            assertEquals(Set.copyOf(codesOf(articles.subList(1, articles.size()))), Set.copyOf(codesOf(pageNotFirst)), "Articles not in category First");
            assertEquals(articles.size() - 1, pageNotFirst.getEstimatedTotalNumberOfResults(), "Count of articles not in category First");

            var notFirstOrTitledA = Map.<String, Object>of("category.name", Not.value("First"), "title", Like.endsWith("-a"));
            var pageNotFirstOrTitledA = articleRepository.getPage(Page.with().allMatch(TITLED).anyMatch(notFirstOrTitledA).build(), true);
            assertEquals(Set.copyOf(codesOf(articles)), Set.copyOf(codesOf(pageNotFirstOrTitledA)), "Articles not in category First or titled a");
            assertEquals(articles.size(), pageNotFirstOrTitledA.getEstimatedTotalNumberOfResults(), "Count of articles not in category First or titled a");

            var firstOrTitledD = Map.<String, Object>of("category.name", "First", "title", Like.endsWith("-d"));
            var pageFirstOrTitledD = articleRepository.getPage(Page.with().allMatch(TITLED).anyMatch(firstOrTitledD).build(), true);
            assertEquals(
                Set.of(articles.get(0).getCode(), articles.get(3).getCode()), Set.copyOf(codesOf(pageFirstOrTitledD)), "Articles in category First or titled d"
            );
            assertEquals(2, pageFirstOrTitledD.getEstimatedTotalNumberOfResults(), "Count of articles in category First or titled d");
        });
    }

    /**
     * A fetch in the {@link QueryBuilder} of a page, with or without limit, does not restrict the entities of the page, so the page and its count include the
     * entities without the fetched association as well.
     */
    @Test
    void testGetPageWithQueryBuilderFetchListsAndCountsArticlesWithoutFetchedAssociation() {
        withArticlesInOptionalCategories(articles -> {
            for (var page : List.of(Page.with().range(0, ROWS_PER_PAGE).allMatch(TITLED).build(), Page.with().allMatch(TITLED).build())) {
                var result = articleRepository.getPage(page, true, (builder, query, article) -> article.fetch("category"));
                assertEquals(Set.copyOf(codesOf(articles)), Set.copyOf(codesOf(result)), "Articles with and without category on " + page);
                assertEquals(articles.size(), result.getEstimatedTotalNumberOfResults(), "Count of articles with and without category on " + page);
            }
        });
    }

    /**
     * {@link EntityRepository#countForeignKeyReferencesTo(Object)} counts the rows referencing given entity via a <code>@ManyToOne</code> as well as via a
     * <code>@OneToMany</code> list, the same as a JPQL count of them.
     */
    @Test
    void testCountForeignKeyReferencesToCountsEachReferencingRow() {
        var person = personRepository.getById(1L);
        var phonesOfPerson = selectLong("SELECT COUNT(phone) FROM Phone phone WHERE phone.owner.id = ?1", person.getId());
        assertTrue(phonesOfPerson > 0, "Person has phones");
        assertEquals(phonesOfPerson, personRepository.countForeignKeyReferencesTo(person), "References to person");

        var phone = phoneRepository.getById(selectLong("SELECT MIN(phone.id) FROM Phone phone WHERE phone.owner.id = ?1", person.getId()));
        var personsOfPhone = selectLong("SELECT COUNT(person) FROM Person person JOIN person.phones phone WHERE phone.id = ?1", phone.getId());
        assertEquals(1, personsOfPhone, "Phone is in phones of one person");
        assertEquals(personsOfPhone, phoneRepository.countForeignKeyReferencesTo(phone), "References to phone");

        withArticlesInOptionalCategories(articles -> {
            assertEquals(1, categoryRepository.countForeignKeyReferencesTo(categoryRepository.getById("first")), "References to category First");
            var unreferenced = newCategory("unreferenced", "Unreferenced");

            try {
                transactionServiceCDI.run(() -> categoryRepository.persist(unreferenced));
                assertEquals(0, categoryRepository.countForeignKeyReferencesTo(unreferenced), "References to unreferenced category");
            }
            finally {
                deleteCategories(List.of(unreferenced));
            }
        });
    }

    /**
     * Runs given test with four persisted articles titled a, b, c and d, of which a is in category First, c in category Second, and b and d in no category.
     */
    private void withArticlesInOptionalCategories(Consumer<List<Article>> test) {
        var first = newCategory("first", "First");
        var second = newCategory("second", "Second");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> List.of(first, second).forEach(categoryRepository::persist));
            articles.add(persistArticle("a", first));
            articles.add(persistArticle("b", null));
            articles.add(persistArticle("c", second));
            articles.add(persistArticle("d", null));
            test.accept(articles);
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(first, second));
        }
    }

    @Test
    void testGetPageWithLikePatternMatchesEscapedWildcardLiterally() {
        var articles = new ArrayList<Article>();

        try {
            articles.add(persistArticle("100%", null));
            articles.add(persistArticle("1000", null));
            var page = articleRepository.getPage(Page.with().allMatch(Map.of("title", Like.pattern(TITLE_PREFIX + "100\\%"))).build(), true);
            assertEquals(List.of(articles.get(0).getCode()), codesOf(page), "Article titled 100%");
            assertEquals(1, page.getEstimatedTotalNumberOfResults(), "Count of articles titled 100%");
        }
        finally {
            deleteArticles(articles);
        }
    }

    /**
     * {@link Like#contains(String)} matches the LIKE wildcards, the SQL Server bracket and the escape character literally, and {@link Like#pattern(String)}
     * matches an escaped wildcard and an escaped escape character literally, on every database.
     */
    @Test
    void testLikeMatchesSpecialCharactersLiterally() {
        var specialTitles = List.of("a_b", "a%b", "a[b]", "a\\b");
        var articles = new ArrayList<Article>();

        try {
            specialTitles.forEach(title -> articles.add(persistArticle(title, null)));
            articles.add(persistArticle("axb", null));

            for (var i = 0; i < specialTitles.size(); i++) {
                var title = specialTitles.get(i);
                assertEquals(List.of(articles.get(i).getCode()), codesOf(articlesTitled(Like.contains(title))), "Articles containing " + title);
            }

            assertEquals(List.of(articles.get(0).getCode()), codesOf(articlesTitled(Like.pattern(TITLE_PREFIX + "a\\_b"))), "Articles titled a_b");
            assertEquals(List.of(articles.get(3).getCode()), codesOf(articlesTitled(Like.pattern(TITLE_PREFIX + "a\\\\b"))), "Articles titled a\\b");
        }
        finally {
            deleteArticles(articles);
        }
    }

    private PartialResultList<Article> articlesTitled(Like like) {
        return articleRepository.getPage(Page.with().allMatch(Map.of("title", like)).build(), true);
    }

    @Test
    void testGetPageWithFetchFieldsReturnsEntitiesWithoutFetchedElements() {
        var articles = new ArrayList<Article>();

        try {
            articles.add(persistArticle("a", null, "x"));
            articles.add(persistArticle("b", null));
            var page = articleRepository.getPage(Page.with().allMatch(TITLED).build(), false, "tags");
            assertEquals(Set.copyOf(codesOf(articles)), Set.copyOf(codesOf(page)), "Articles with and without tags");
        }
        finally {
            deleteArticles(articles);
        }
    }

    @Test
    void testGetPageWithFetchFieldsAndCriteriaOnThem() {
        var types = Set.of(Phone.Type.MOBILE, Phone.Type.HOME);
        var expectedIds = personRepository.getPage(Page.ALL, false, "phones").stream()
            .filter(person -> person.getPhones().stream().map(Phone::getType).collect(toSet()).containsAll(types)).map(Person::getId).collect(toSet());
        var result = personRepository.getPage(Page.with().range(0, ROWS_PER_PAGE).allMatch(Map.of("phones.type", types)).build(), false, "phones");
        var ids = result.stream().map(Person::getId).collect(toSet());
        assertFalse(expectedIds.isEmpty(), "Some persons have MOBILE and HOME phones");
        assertEquals(Math.min(ROWS_PER_PAGE, expectedIds.size()), ids.size(), "Page is full and has each person once");
        assertTrue(expectedIds.containsAll(ids), "Page has only persons having MOBILE and HOME phones");
        result.forEach(person -> assertFalse(person.getPhones().isEmpty(), "Phones are fetched without transaction"));
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = "OpenJPA does not support EntityManager#getEntityGraph()")
    void testGetByIdWithLoadGraph() {
        var person = personRepository.getByIdWithLoadGraph(1L, Person.WITH_PHONES);
        assertTrue(personRepository.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(person, "phones"), "Phones are loaded");
        assertEquals(TOTAL_PHONES_PER_PERSON_0, person.getPhones().size(), "Phones loaded by entity graph");
    }

    /**
     * A fetch in a {@link CriteriaQueryBuilder} of <code>list()</code>, <code>find()</code> and <code>findFirst()</code> loads the fetched association, on
     * every provider.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = "OpenJPA reports a fetched collection of a detached entity as not loaded")
    void testListAndFindByCriteriaQueryBuilderLoadFetchedPhones() {
        CriteriaQueryBuilder<Person> withPhonesOfFirstPerson = (builder, query, person) -> {
            person.fetch("phones", LEFT);
            query.distinct(true).where(builder.equal(person.get("id"), 1L));
        };
        var persons = List.of(
            personRepository.list(withPhonesOfFirstPerson, Map.of()).get(0),
            personRepository.find(withPhonesOfFirstPerson, Map.of()).orElseThrow(),
            personRepository.findFirst(withPhonesOfFirstPerson, Map.of()).orElseThrow()
        );
        var persistenceUnitUtil = personRepository.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();

        for (var person : persons) {
            assertTrue(persistenceUnitUtil.isLoaded(person, "phones"), "Phones are loaded");
            assertEquals(TOTAL_PHONES_PER_PERSON_0, person.getPhones().size(), "Phones fetched by criteria query builder");
        }
    }

    @Test
    void testSerializedRepositoryObtainsEntityManagerAgain() throws IOException, ClassNotFoundException {
        var deserializedRepository = serializeAndDeserialize(personRepository);
        assertNotNull(deserializedRepository.getById(1L), "Existing person via deserialized repository");
    }

    @Test
    void testModifyingActionOutsideTransactionFails() {
        var text = new Text();
        assertThrows(TransactionRequiredException.class, () -> textRepository.persist(text));
    }

    @Test
    void testModifyingActionsInCDITransaction() {
        assertModifyingActions(transactionServiceCDI);
    }

    @Test
    void testModifyingActionsInEJBTransaction() {
        assertModifyingActions(transactionServiceEJB);
    }

    private void assertModifyingActions(TransactionService transaction) {
        var text = new Text();
        var id = transaction.call(() -> textRepository.persist(text));
        assertNotNull(textRepository.getById(id), "Persisted text");

        transaction.run(() -> textRepository.softDelete(textRepository.getById(id)));
        assertNull(textRepository.getById(id), "Soft deleted text is excluded");
        assertNotNull(textRepository.getSoftDeletedById(id), "Soft deleted text");

        transaction.run(() -> textRepository.softUndelete(textRepository.getSoftDeletedById(id)));
        assertNotNull(textRepository.getById(id), "Soft undeleted text");

        var savedText = transaction.call(() -> textRepository.save(text));
        assertEquals(id, savedText.getId(), "Saved existing text keeps its ID");

        transaction.run(() -> textRepository.delete(textRepository.getById(id)));
        assertNull(textRepository.getById(id), "Deleted text");
    }

    /**
     * A {@link VersionedBaseEntity} gets the same created and last modified timestamp on persist. On update its last modified timestamp advances, unless it is
     * skipped, and its version advances, while its created timestamp stays.
     */
    @Test
    void testVersionedBaseEntityTimestampsAndVersion() {
        var remark = new Remark();
        remark.setText("persisted");
        var id = transactionServiceCDI.call(() -> remarkRepository.persist(remark));

        try {
            var persisted = reloadRemark(id);
            assertNotNull(persisted.getCreated(), "Created");
            assertEquals(persisted.getCreated(), persisted.getLastModified(), "Last modified of persisted remark");
            assertNotNull(persisted.getVersion(), "Version");

            var skipped = updateRemark(id, remarkToUpdate -> {
                remarkToUpdate.setLastModified(PAST_LAST_MODIFIED);
                remarkToUpdate.skipAdjustLastModified();
            });
            assertEquals(PAST_LAST_MODIFIED, skipped.getLastModified(), "Last modified of remark updated with skipAdjustLastModified()");
            assertTrue(skipped.getVersion() > persisted.getVersion(), "Version of remark updated with skipAdjustLastModified()");

            var updated = updateRemark(id, remarkToUpdate -> remarkToUpdate.setText("updated"));
            assertEquals(skipped.getCreated(), updated.getCreated(), "Created of updated remark");
            assertTrue(updated.getLastModified().isAfter(PAST_LAST_MODIFIED), "Last modified of updated remark advances");
            assertFalse(updated.getLastModified().isBefore(persisted.getLastModified()), "Last modified of updated remark is not before persisted one");
            assertTrue(updated.getVersion() > skipped.getVersion(), "Version of updated remark advances");
        }
        finally {
            transactionServiceCDI.run(() -> remarkRepository.delete(remarkRepository.getById(id)));
        }
    }

    /**
     * Returns the remark with given ID as stored after given change was updated in a transaction.
     */
    private Remark updateRemark(Long id, Consumer<Remark> change) {
        transactionServiceCDI.run(() -> {
            var remark = remarkRepository.getById(id);
            change.accept(remark);
            remarkRepository.update(remark);
        });
        return reloadRemark(id);
    }

    /**
     * Returns the remark with given ID as stored, bypassing the second level cache.
     */
    private Remark reloadRemark(Long id) {
        remarkRepository.getEntityManager().getEntityManagerFactory().getCache().evict(Remark.class);
        return remarkRepository.getById(id);
    }

    /**
     * {@link EntityRepository#fetchLazyCollections(Object, Function...)} loads the given lazy collections, and without getters every collection which is not a
     * map, so that they are available after the transaction.
     */
    @Test
    void testFetchLazyCollectionsInitializesPhonesAndGroups() {
        var id = 1L;
        var totalGroups = transactionServiceCDI.call(() -> personRepository.getById(id).getGroups().size());
        var withPhones = transactionServiceCDI.call(() -> personRepository.fetchLazyCollections(personRepository.getById(id), Person::getPhones));
        assertEquals(TOTAL_PHONES_PER_PERSON_0, withPhones.getPhones().size(), "Phones fetched via getter");

        var withAll = transactionServiceCDI.call(() -> personRepository.fetchLazyCollections(personRepository.getById(id)));
        assertEquals(TOTAL_PHONES_PER_PERSON_0, withAll.getPhones().size(), "Phones fetched without getters");
        assertEquals(totalGroups, withAll.getGroups().size(), "Groups fetched without getters");
    }

    /**
     * {@link EntityRepository#fetchLazyMaps(Object, Function...)} loads the lazy maps, and {@link EntityRepository#fetchLazyBlobs(Object)} sets the lazy blobs
     * of the stored entity on the given entity.
     */
    @Test
    void testFetchLazyMapsAndBlobs() {
        var attachment = new Attachment();
        attachment.getProperties().put("type", "text/plain");
        attachment.setContent("content".getBytes(UTF_8));
        var id = transactionServiceCDI.call(() -> attachmentRepository.persist(attachment));

        try {
            var withProperties = transactionServiceCDI.call(() -> attachmentRepository.fetchLazyMaps(attachmentRepository.getById(id)));
            assertEquals(Map.of("type", "text/plain"), withProperties.getProperties(), "Properties");

            var withContent = transactionServiceCDI.call(() -> attachmentRepository.fetchLazyBlobs(attachmentRepository.getById(id)));
            assertArrayEquals("content".getBytes(UTF_8), withContent.getContent(), "Content");
        }
        finally {
            transactionServiceCDI.run(() -> attachmentRepository.delete(attachmentRepository.getById(id)));
        }
    }

    /**
     * An entity with an <code>@EmbeddedId</code> and an <code>@Embedded</code> value is persisted, got by its ID or IDs, updated and listed. It is paged with
     * criteria and ordering on properties of both, counted and deleted. It is paged with a fetch of its element collection and an ordering on it.
     */
    @Test
    void testEntityWithEmbeddedIdPersistsGetsListsAndPages() {
        var start = LocalDate.of(2026, 1, 1);
        var memberships = List.of(
            new Membership(new MembershipId("embedded-a", "x"), new Period(start, start.plusMonths(1))),
            new Membership(new MembershipId("embedded-a", "y"), new Period(start.plusMonths(2), null)),
            new Membership(new MembershipId("embedded-b", "x"), new Period(start.plusMonths(1), null))
        );
        memberships.get(0).getRoles().add("treasurer");
        memberships.get(1).getRoles().add("chair");
        memberships.get(2).getRoles().add("secretary");
        memberships.forEach(membership -> transactionServiceCDI.run(() -> membershipRepository.persist(membership)));

        try {
            var ay = membershipRepository.getById(memberships.get(1).getId());
            assertEquals(start.plusMonths(2), ay.getPeriod().getStartDate(), "Start date of membership got by ID");
            assertNull(ay.getPeriod().getEndDate(), "End date of membership got by ID");
            assertNull(membershipRepository.getById(new MembershipId("embedded-a", "z")), "Membership got by absent ID");

            var byIds = membershipRepository.getByIds(List.of(memberships.get(0).getId(), memberships.get(2).getId()));
            assertEquals(List.of(memberships.get(0).getId(), memberships.get(2).getId()), membershipIds(byIds).stream().sorted().toList(), "Got by IDs");

            var byRole = membershipRepository
                .getPage(Page.with().allMatch(Map.of("id.memberName", Like.startsWith("embedded-"))).orderBy("roles", true).build(), false, "roles");
            assertEquals(List.of(memberships.get(1).getId(), memberships.get(2).getId(), memberships.get(0).getId()), membershipIds(byRole), "Ordered by role");
            assertEquals(Set.of("chair"), byRole.get(0).getRoles(), "Roles fetched");

            var end = start.plusMonths(3);
            transactionServiceCDI.run(() -> membershipRepository.update(new Membership(ay.getId(), new Period(ay.getPeriod().getStartDate(), end))));
            assertEquals(end, membershipRepository.getById(ay.getId()).getPeriod().getEndDate(), "End date of updated membership");
            assertEquals(
                membershipIds(memberships), membershipIds(membershipRepository.list()).stream().filter(membershipIds(memberships)::contains).sorted().toList(),
                "Listed memberships"
            );

            var ofA = membershipRepository
                .getPage(Page.with().allMatch(Map.of("id.memberName", "embedded-a")).orderBy("id.clubName", false).build(), true);
            assertEquals(List.of(memberships.get(1).getId(), memberships.get(0).getId()), membershipIds(ofA), "Memberships of a by club descending");
            assertEquals(2, ofA.getEstimatedTotalNumberOfResults(), "Count of memberships of a");

            var later = membershipRepository.getPage(
                Page.with().allMatch(Map.of("id.memberName", Like.startsWith("embedded-"), "period.startDate", Order.greaterThan(start)))
                    .orderBy("period.startDate", true).build(),
                false
            );
            assertEquals(List.of(memberships.get(2).getId(), memberships.get(1).getId()), membershipIds(later), "Memberships starting later by start date");
        }
        finally {
            memberships.forEach(
                membership -> transactionServiceCDI.run(() -> membershipRepository.findById(membership.getId()).ifPresent(membershipRepository::delete))
            );
        }
    }

    private static List<MembershipId> membershipIds(List<Membership> memberships) {
        return memberships.stream().map(Membership::getId).toList();
    }

    // Entity without BaseEntity --------------------------------------------------------------------------------------

    @Test
    void testEntityWithGeneratedIdNotNamedId() {
        var article = newArticle("crud", null);

        try {
            var code = transactionServiceCDI.call(() -> articleRepository.persist(article));
            assertNotNull(code, "Generated code");
            assertEquals(code, article.getCode(), "Generated code is set on entity");
            assertTrue(articleRepository.findById(code).isPresent(), "Persisted article");
            assertThrows(IllegalEntityStateException.class, () -> transactionServiceCDI.call(() -> articleRepository.persist(article)), "Persist existing");

            article.setTitle(TITLE_PREFIX + "updated");
            transactionServiceCDI.call(() -> articleRepository.save(article));
            assertEquals(TITLE_PREFIX + "updated", articleRepository.getById(code).getTitle(), "Saved existing article");

            transactionServiceCDI.run(() -> articleRepository.delete(article));
            assertNull(article.getCode(), "Code is unset after delete");
            assertNull(articleRepository.getById(code), "Deleted article");
        }
        finally {
            deleteArticles(List.of(article));
        }
    }

    @Test
    void testEntityWithCustomEntityName() {
        var note = new Note();
        note.setSlug("memo");
        note.setText("before");

        try {
            assertEquals("Memo", noteRepository.getEntityName(), "Entity name");
            transactionServiceCDI.run(() -> noteRepository.persist(note));
            assertTrue(noteRepository.exists(note), "Exists");
            assertThrows(IllegalEntityStateException.class, () -> transactionServiceCDI.call(() -> noteRepository.persist(note)), "Persist existing");
            assertEquals(1, noteRepository.list("WHERE e.text = ?1", "before").size(), "JPQL select");
            assertEquals(1, (int) transactionServiceCDI.call(() -> noteRepository.update("SET e.text = ?1 WHERE e.slug = ?2", "after", "memo")), "JPQL update");
            assertEquals("after", noteRepository.getById("memo").getText(), "Updated text");
        }
        finally {
            transactionServiceCDI.run(() -> noteRepository.findById("memo").ifPresent(noteRepository::delete));
        }
    }

    @Test
    void testEntityWithAssignedIdNotNamedId() {
        var category = newCategory("assigned", "Assigned");

        try {
            var uncoded = new Category();
            assertThrows(IllegalEntityStateException.class, () -> transactionServiceCDI.call(() -> categoryRepository.persist(uncoded)), "No assigned ID");

            assertEquals("assigned", transactionServiceCDI.call(() -> categoryRepository.persist(category)), "Assigned ID");
            assertFalse(categoryRepository.isGeneratedId(), "Assigned ID is not generated");
            assertThrows(IllegalEntityStateException.class, () -> transactionServiceCDI.call(() -> categoryRepository.persist(category)), "Persist existing");

            category.setName("Renamed");
            transactionServiceCDI.call(() -> categoryRepository.save(category));
            assertEquals("Renamed", categoryRepository.getById("assigned").getName(), "Saved existing category");
        }
        finally {
            deleteCategories(List.of(category));
        }
    }

    @Test
    void testEntityListenerFiresEventsForEntityWithoutBaseEntity() {
        var article = newArticle("events", null);

        try {
            entityEvents.clear();
            transactionServiceCDI.run(() -> articleRepository.persist(article));
            article.setTitle(TITLE_PREFIX + "events updated");
            transactionServiceCDI.run(() -> articleRepository.update(article));
            transactionServiceCDI.run(() -> articleRepository.delete(article));
            assertEquals(List.of("Created Article", "Updated Article", "Deleted Article"), entityEvents.get(), "Fired events");
        }
        finally {
            deleteArticles(List.of(article));
        }
    }

    @Test
    void testEntityListenerFiresEventsForBaseEntity() {
        entityEvents.clear();
        var id = transactionServiceCDI.call(() -> textRepository.persist(new Text()));
        transactionServiceCDI.run(() -> textRepository.delete(textRepository.getById(id)));
        assertEquals(List.of("Created Text", "Deleted Text"), entityEvents.get(), "Fired events");
    }

    @Test
    void testAuditTracksChangeOfDetachedEntityWithoutBaseEntity() {
        var article = newArticle("audit", null);

        try {
            var code = transactionServiceCDI.call(() -> articleRepository.persist(article));
            var detachedArticle = articleRepository.getById(code);
            detachedArticle.setTitle(TITLE_PREFIX + "audit updated");
            TestAuditListener.clearChanges();
            transactionServiceCDI.run(() -> articleRepository.update(detachedArticle));
            var expected = code + ".title: " + TITLE_PREFIX + "audit -> " + TITLE_PREFIX + "audit updated";
            assertEquals(List.of(expected), auditedArticleChanges(), "Audited changes");
        }
        finally {
            deleteArticles(List.of(article));
        }
    }

    @Test
    void testDeleteOfLazyAssociation() {
        deleteLazyCategory("lazy");
        assertNull(categoryRepository.getById("lazy"), "Deleted category");
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK_OR_OPENJPA, disabledReason = MANAGED_ID_IMMUTABLE)
    void testDeleteOfLazyAssociationUnsetsItsId() {
        assertNull(deleteLazyCategory("unset").getSlug(), "Slug is unset after delete");
    }

    private Category deleteLazyCategory(String slug) {
        var category = newCategory(slug, slug);
        transactionServiceCDI.run(() -> categoryRepository.persist(category));
        var article = persistArticle(slug, category);
        return transactionServiceCDI.call(() -> {
            var reference = articleRepository.getById(article.getCode()).getCategory();
            articleRepository.delete(article);
            categoryRepository.delete(reference);
            return reference;
        });
    }

    @Test
    void testGetIdOfEntityWithoutBaseEntity() {
        assertNull(articleRepository.getId(newArticle("getid", null)), "No generated code before persist");

        var category = newCategory("getid", "GetId");
        var article = newArticle("getid", null);

        try {
            transactionServiceCDI.run(() -> {
                categoryRepository.persist(category);
                article.setCategory(category);
                articleRepository.persist(article);
            });
            assertEquals(article.getCode(), articleRepository.getId(article), "Generated code after persist");
            var slug = transactionServiceCDI.call(() -> categoryRepository.getId(articleRepository.getById(article.getCode()).getCategory()));
            assertEquals("getid", slug, "Slug of lazy category");
        }
        finally {
            deleteArticles(List.of(article));
            deleteCategories(List.of(category));
        }
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK, disabledReason = LOADED_REFERENCE)
    void testGetIdOfUnloadedReferenceKeepsItUnloaded() {
        var category = newCategory("reference", "Reference");
        transactionServiceCDI.run(() -> categoryRepository.persist(category));

        try {
            var state = transactionServiceCDI.call(() -> {
                var persistenceUnitUtil = categoryRepository.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
                var reference = categoryRepository.getEntityManager().getReference(Category.class, "reference");
                var loadedBefore = persistenceUnitUtil.isLoaded(reference);
                var id = categoryRepository.getId(reference);
                return List.<Object>of(loadedBefore, id, persistenceUnitUtil.isLoaded(reference));
            });
            assertEquals(List.of(false, "reference", false), state, "Reference is unloaded before getId(), has the ID, and is still unloaded after getId()");
        }
        finally {
            deleteCategories(List.of(category));
        }
    }

    @Test
    void testGetIdOfBaseEntity() {
        var text = new Text();
        var id = transactionServiceCDI.call(() -> textRepository.persist(text));

        try {
            assertEquals(id, textRepository.getId(text), "ID of persisted text");
        }
        finally {
            transactionServiceCDI.run(() -> textRepository.delete(textRepository.getById(id)));
        }
    }

    /**
     * <code>list(true)</code> and <code>getByIds(ids, true)</code> return the active entities as well as the soft deleted ones, like
     * <code>getById(id, true)</code>, on an <code>ACTIVE</code> as well as on a <code>DELETED</code> soft deletable field.
     */
    @Test
    void testListAndGetByIdsIncludingSoftDeletedReturnActiveAndSoftDeleted() {
        var activeText = new Text();
        var deletedText = new Text();
        var activeTextId = transactionServiceCDI.call(() -> textRepository.persist(activeText));
        var deletedTextId = transactionServiceCDI.call(() -> textRepository.persist(deletedText));
        var activeArticle = persistArticle("active", null);
        var deletedArticle = persistArticle("deleted", null);

        try {
            transactionServiceCDI.run(() -> textRepository.softDelete(textRepository.getById(deletedTextId)));
            transactionServiceCDI.run(() -> articleRepository.softDelete(deletedArticle));

            var textIds = List.of(activeTextId, deletedTextId);
            assertEquals(List.of(deletedTextId, activeTextId), idsOf(textRepository.getByIds(textIds, true)), "Texts by IDs including soft deleted");
            assertEquals(List.of(activeTextId), idsOf(textRepository.getByIds(textIds, false)), "Texts by IDs excluding soft deleted");
            assertTrue(idsOf(textRepository.list(true)).containsAll(textIds), "Texts including soft deleted");
            assertFalse(idsOf(textRepository.list(false)).contains(deletedTextId), "Texts excluding soft deleted");

            var articleCodes = codesOf(List.of(activeArticle, deletedArticle));
            assertEquals(
                List.of(deletedArticle.getCode(), activeArticle.getCode()), codesOf(articleRepository.getByIds(articleCodes, true)),
                "Articles by IDs including soft deleted"
            );
            assertEquals(List.of(activeArticle.getCode()), codesOf(articleRepository.getByIds(articleCodes, false)), "Articles by IDs excluding soft deleted");
            assertTrue(codesOf(articleRepository.list(true)).containsAll(articleCodes), "Articles including soft deleted");
            assertFalse(codesOf(articleRepository.list(false)).contains(deletedArticle.getCode()), "Articles excluding soft deleted");
        }
        finally {
            transactionServiceCDI.run(() -> textRepository.getByIds(List.of(activeTextId, deletedTextId), true).forEach(textRepository::delete));
            deleteArticles(List.of(activeArticle, deletedArticle));
        }
    }

    /**
     * An enum field without <code>@Enumerated</code> is mapped by <code>ORDINAL</code>, the JPA default, so {@link Like} matches it by constant name.
     */
    @Test
    void testLikeOnEnumWithoutEnumeratedMatchesConstantName() {
        withFemaleAndMaleAudienceArticles(femaleArticle -> {
            var result = pageOfArticlesWithFemAudience("audience");
            assertEquals(List.of(femaleArticle.getCode()), codesOf(result), "Articles whose audience contains fem");
            assertEquals(1, result.getEstimatedTotalNumberOfResults(), "Count of articles whose audience contains fem");
        });
    }

    /**
     * An enum element collection without <code>@Enumerated</code> is mapped by <code>ORDINAL</code>, the JPA default, so {@link Like} matches it by constant
     * name.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK, disabledReason = ECLIPSELINK_ENUM_COLLECTION)
    void testLikeOnEnumElementCollectionWithoutEnumeratedMatchesConstantName() {
        withFemaleAndMaleAudienceArticles(
            femaleArticle -> assertEquals(List.of(femaleArticle.getCode()), codesOf(pageOfArticlesWithFemAudience("audiences")), "Articles")
        );
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK, disabledReason = ECLIPSELINK_ENUM_COLLECTION)
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testLikeOnEnumElementCollectionWithoutEnumeratedCountsMatchingArticles() {
        withFemaleAndMaleAudienceArticles(
            femaleArticle -> assertEquals(1, pageOfArticlesWithFemAudience("audiences").getEstimatedTotalNumberOfResults(), "Count of articles")
        );
    }

    private PartialResultList<Article> pageOfArticlesWithFemAudience(String field) {
        return articleRepository.getPage(Page.with().allMatch(Map.of(field, Like.contains("fem"))).build(), true);
    }

    /**
     * Runs given test with two persisted articles, one with a female audience, which is passed to the test, and one with a male audience.
     */
    private void withFemaleAndMaleAudienceArticles(Consumer<Article> test) {
        var femaleArticle = newArticle("female", null);
        femaleArticle.setAudience(Gender.FEMALE);
        femaleArticle.getAudiences().add(Gender.FEMALE);
        var maleArticle = newArticle("male", null);
        maleArticle.setAudience(Gender.MALE);
        maleArticle.getAudiences().add(Gender.MALE);
        transactionServiceCDI.run(() -> List.of(femaleArticle, maleArticle).forEach(articleRepository::persist));

        try {
            test.accept(femaleArticle);
        }
        finally {
            deleteArticles(List.of(femaleArticle, maleArticle));
        }
    }

    /**
     * {@link Like} on a decimal matches its fraction as well, the same as {@link Like#applies(Object)}.
     */
    @Test
    void testLikeOnDecimalMatchesFraction() {
        var expensiveArticle = newArticle("expensive", null);
        expensiveArticle.setPrice(new BigDecimal("12.50"));
        var cheapArticle = newArticle("cheap", null);
        cheapArticle.setPrice(new BigDecimal("3.00"));
        transactionServiceCDI.run(() -> List.of(expensiveArticle, cheapArticle).forEach(articleRepository::persist));

        try {
            var like = Like.contains("2.5");
            assertTrue(like.applies(expensiveArticle.getPrice()), "Price 12.50 contains 2.5");
            var result = articleRepository.getPage(Page.with().allMatch(Map.of("price", like)).build(), true);
            assertEquals(List.of(expensiveArticle.getCode()), codesOf(result), "Articles whose price contains 2.5");
            assertEquals(1, result.getEstimatedTotalNumberOfResults(), "Count of articles whose price contains 2.5");
        }
        finally {
            deleteArticles(List.of(expensiveArticle, cheapArticle));
        }
    }

    @Test
    void testEntityWithoutBaseEntitySoftDelete() {
        var article = persistArticle("soft", null);

        try {
            transactionServiceCDI.run(() -> articleRepository.softDelete(article));
            assertNull(articleRepository.getById(article.getCode()), "Soft deleted article is excluded");
            assertNotNull(articleRepository.getSoftDeletedById(article.getCode()), "Soft deleted article");

            transactionServiceCDI.run(() -> articleRepository.softUndelete(article));
            assertNotNull(articleRepository.getById(article.getCode()), "Soft undeleted article");
        }
        finally {
            deleteArticles(List.of(article));
        }
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testCountOfNegatedElementCollectionCriteria() {
        var category = newCategory("count", "Count");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> categoryRepository.persist(category));
            articles.add(persistArticle("count-a", category, "x", "y"));
            articles.add(persistArticle("count-b", category, "x", "xy"));
            articles.add(persistArticle("count-c", category));
            var notAllOf = articleRepository.getPage(Page.with().allMatch(Map.of("tags", Not.value(List.of("x", "y")))).build(), true);
            assertEquals(2, notAllOf.getEstimatedTotalNumberOfResults(), "Count of not all of element collection");
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(category));
        }
    }

    @Test
    void testEntityWithoutBaseEntityQueries() {
        var first = newCategory("first", "First");
        var second = newCategory("second", "Second");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> List.of(first, second).forEach(categoryRepository::persist));
            articles.add(persistArticle("a", first, Set.of(5, 10), Set.of(true), "x", "y"));
            articles.add(persistArticle("b", first, Set.of(5), Set.of(false), "x", "xy"));
            articles.add(persistArticle("c", second));
            var codes = articles.stream().map(Article::getCode).sorted().toList();
            var descendingCodes = new ArrayList<>(codes);
            Collections.reverse(descendingCodes);

            assertEquals(descendingCodes, codesOf(articleRepository.getByIds(codes)), "getByIds orders by code descending");
            assertEquals(descendingCodes, codesOf(articleRepository.list()).stream().filter(codes::contains).toList(), "list orders by code descending");
            assertEquals(descendingCodes, codesOf(articleRepository.getPage(Page.with().allMatch(TITLED).build(), true)), "Default ordering by id");
            assertEquals(codes, codesOf(articleRepository.getPage(Page.with().allMatch(TITLED).orderBy("id", true).build(), true)), "Ordering by id");
            assertEquals(
                List.of(codes.get(0)), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("id", codes.get(0))).build(), true)), "Criteria on id"
            );
            assertEquals(
                2, articleRepository.getPage(Page.with().allMatch(Map.of("category.name", Like.startsWith("Fir"))).build(), true).size(),
                "Criteria on association"
            );
            assertEquals(1, articleRepository.getPage(Page.with().allMatch(Map.of("category", second)).build(), true).size(), "Criteria on entity");

            assertEquals(2, articleRepository.getPage(Page.with().allMatch(Map.of("tags", "x")).build(), true).size(), "Criteria on element collection");
            var ignoreCase = articleRepository.getPage(Page.with().allMatch(Map.of("tags", "X")).build(), true);
            assertEquals(2, ignoreCase.size(), "Criteria on element collection ignore case");
            assertEquals(2, ignoreCase.getEstimatedTotalNumberOfResults(), "Count of criteria on element collection ignore case");
            assertEquals(
                List.of(codes.get(0)), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("tags", List.of("x", "y"))).build(), true)),
                "Criteria on all of element collection"
            );
            assertEquals(
                2, articleRepository.getPage(Page.with().allMatch(Map.of("tags", Like.contains("X"))).build(), true).size(), "Like on element collection"
            );
            assertEquals(3, countArticles(TITLED, "deleted", Like.contains("fals")), "Like on boolean matching false");
            assertEquals(0, countArticles(TITLED, "deleted", Like.contains("tru")), "Like on boolean matching true");
            assertEquals(3, countArticles(TITLED, "deleted", Like.contains("e")), "Like on boolean matching true and false");
            assertEquals(0, countArticles(TITLED, "deleted", Like.contains("foo")), "Like on boolean matching neither true nor false");
            assertEquals(1, countArticles(TITLED, "flags", Like.contains("tru")), "Like on boolean element collection");
            assertEquals(0, countArticles(TITLED, "flags", Like.contains("foo")), "Like on boolean element collection matching neither");
            assertEquals(
                List.of(articles.get(2).getCode()),
                codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("tags", Not.value(Like.contains("x")))).build(), true)),
                "Not like on element collection"
            );
            assertEquals(
                List.of(articles.get(0).getCode()),
                codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("tags", List.of(Like.contains("x"), "y"))).build(), true)),
                "Like and value on all of element collection"
            );
            assertEquals(2, articleRepository.getPage(Page.with().allMatch(Map.of("ratings", "05")).build(), true).size(), "Numeric element collection");
            assertEquals(
                List.of(articles.get(0).getCode()),
                codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("ratings", List.of(5, 10))).build(), true)),
                "Numeric values on all of element collection"
            );
            assertEquals(
                List.of(articles.get(2).getCode()),
                codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("ratings", Not.value(5))).build(), true)),
                "Not numeric value on element collection"
            );
            assertEquals(
                List.of(articles.get(0).getCode()), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("flags", "1")).build(), true)),
                "Boolean element collection"
            );
            assertEquals(
                List.of(articles.get(1).getCode()), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("flags", "0")).build(), true)),
                "False boolean element collection"
            );
            var notAllOf = articleRepository.getPage(Page.with().allMatch(Map.of("tags", Not.value(List.of("x", "y")))).build(), true);
            assertEquals(List.of(articles.get(2).getCode(), articles.get(1).getCode()), codesOf(notAllOf), "Not all of element collection");
            assertEquals(
                List.of(articles.get(2).getCode(), articles.get(1).getCode()),
                codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("tags", Not.value(List.of(Like.contains("x"), "y")))).build(), true)),
                "Not like and value on all of element collection"
            );
            assertEquals(
                List.of(articles.get(2).getCode()),
                codesOf(
                    articleRepository.getPage(
                        Page.with().allMatch(Map.of("code", Not.value(List.of(articles.get(0).getCode(), articles.get(1).getCode())))).build(), true
                    )
                ),
                "Not any of plain field"
            );

            var withQueryBuilder = articleRepository.getPage(Page.with().allMatch(TITLED).build(), true, (criteriaBuilder, query, root) -> {
            });
            assertEquals(3, withQueryBuilder.getEstimatedTotalNumberOfResults(), "Count via count subquery");

            var withCategories = articleRepository.getPage(Page.with().range(0, 10).allMatch(TITLED).build(), true, "category");
            assertEquals(3, withCategories.getEstimatedTotalNumberOfResults(), "Count with fetched category");
            assertTrue(withCategories.stream().allMatch(article -> article.getCategory().getName() != null), "Categories are fetched");

            var firstPage = articleRepository.getPage(Page.with().range(0, 2).allMatch(TITLED).orderBy("code", true).build(), false);
            assertEquals(codes.subList(0, 2), codesOf(firstPage), "First page by cursor");
            var nextPage = articleRepository.getPage(Page.with().range(firstPage.get(1), 2, false).allMatch(TITLED).orderBy("code", true).build(), false);
            assertEquals(codes.subList(2, 3), codesOf(nextPage), "Next page by cursor");
            var nextPageById = articleRepository.getPage(Page.with().range(0, 2, Map.of("id", codes.get(2)), false).allMatch(TITLED).build(), false);
            assertEquals(descendingCodes.subList(1, 3), codesOf(nextPageById), "Next page by cursor values on id");
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(first, second));
        }
    }

    private static List<String> auditedArticleChanges() {
        return TestAuditListener.getChanges().stream()
            .map(change -> change.getEntityId() + "." + change.getPropertyName() + ": " + change.getOldValue() + " -> " + change.getNewValue())
            .toList();
    }

    private static Article newArticle(String title, Category category) {
        var article = new Article();
        article.setTitle(TITLE_PREFIX + title);
        article.setCategory(category);
        return article;
    }

    private Article persistArticle(String title, Category category, String... tags) {
        return persistArticle(title, category, Set.of(), Set.of(), tags);
    }

    private Article persistArticle(String title, Category category, Set<Integer> ratings, Set<Boolean> flags, String... tags) {
        var article = newArticle(title, null);
        article.getTags().addAll(List.of(tags));
        article.getRatings().addAll(ratings);
        article.getFlags().addAll(flags);
        transactionServiceCDI.run(() -> {
            article.setCategory(category == null ? null : categoryRepository.getById(category.getSlug()));
            articleRepository.persist(article);
        });
        return article;
    }

    private static Category newCategory(String slug, String name) {
        var category = new Category();
        category.setSlug(slug);
        category.setName(name);
        return category;
    }

    private int countArticles(Map<String, Object> criteria, String field, Object value) {
        var allCriteria = new HashMap<>(criteria);
        allCriteria.put(field, value);
        return articleRepository.getPage(Page.with().allMatch(allCriteria).build(), true).size();
    }

    private void deleteArticles(List<Article> articles) {
        transactionServiceCDI.run(
            () -> articles.stream().filter(article -> article.getCode() != null).map(Article::getCode)
                .map(code -> articleRepository.getById(code, true)).filter(Objects::nonNull).forEach(articleRepository::delete)
        );
    }

    private void deleteCategories(List<Category> categories) {
        transactionServiceCDI.run(
            () -> categories.stream().map(category -> categoryRepository.getById(category.getSlug()))
                .filter(Objects::nonNull).forEach(categoryRepository::delete)
        );
    }

    private Long selectLong(String jpql, Object parameter) {
        return personRepository.getEntityManager().createQuery(jpql, Long.class).setParameter(1, parameter).getSingleResult();
    }

    private static List<Long> codesOf(List<Article> articles) {
        return articles.stream().map(Article::getCode).toList();
    }

    private static List<Long> idsOf(List<Text> texts) {
        return texts.stream().map(Text::getId).toList();
    }

}
