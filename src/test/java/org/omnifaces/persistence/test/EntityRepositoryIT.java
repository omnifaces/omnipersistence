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
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.JPA.LIKE_ESCAPE;
import static org.omnifaces.persistence.JPA.escapeLike;
import static org.omnifaces.persistence.test.OmniPersistenceIT.ECLIPSELINK;
import static org.omnifaces.persistence.test.OmniPersistenceIT.ECLIPSELINK_OR_OPENJPA;
import static org.omnifaces.persistence.test.OmniPersistenceIT.HIBERNATE;
import static org.omnifaces.persistence.test.OmniPersistenceIT.OPENJPA;
import static org.omnifaces.persistence.test.OmniPersistenceIT.OPENJPA_COUNT;
import static org.omnifaces.persistence.test.OmniPersistenceIT.PROFILE_ID;
import static org.omnifaces.persistence.test.OmniPersistenceIT.TEST_DATABASE;
import static org.omnifaces.persistence.test.Serializations.serializeAndDeserialize;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.ROWS_PER_PAGE;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.TOTAL_RECORDS;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.TransactionRequiredException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.criteria.Bool;
import org.omnifaces.persistence.criteria.Criteria;
import org.omnifaces.persistence.criteria.IgnoreCase;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.criteria.Not;
import org.omnifaces.persistence.criteria.Order;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.exception.NonDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.NonDeletable;
import org.omnifaces.persistence.model.VersionedBaseEntity;
import org.omnifaces.persistence.query.Page;
import org.omnifaces.persistence.query.Select;
import org.omnifaces.persistence.service.BaseEntityService.CriteriaQueryBuilder;
import org.omnifaces.persistence.service.BaseEntityService.QueryBuilder;
import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.persistence.test.model.Album;
import org.omnifaces.persistence.test.model.Article;
import org.omnifaces.persistence.test.model.Attachment;
import org.omnifaces.persistence.test.model.Badge;
import org.omnifaces.persistence.test.model.Category;
import org.omnifaces.persistence.test.model.Club;
import org.omnifaces.persistence.test.model.Config;
import org.omnifaces.persistence.test.model.Credit;
import org.omnifaces.persistence.test.model.Document;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Invoice;
import org.omnifaces.persistence.test.model.Letter;
import org.omnifaces.persistence.test.model.Locker;
import org.omnifaces.persistence.test.model.Membership;
import org.omnifaces.persistence.test.model.MembershipId;
import org.omnifaces.persistence.test.model.Note;
import org.omnifaces.persistence.test.model.Period;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Recipient;
import org.omnifaces.persistence.test.model.Remark;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.model.Track;
import org.omnifaces.persistence.test.model.dto.PersonCard;
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
    private static final int SQL_SERVER_MAX_PARAMETERS = 2100;
    private static final Instant PAST_LAST_MODIFIED = Instant.parse("2000-01-01T00:00:00Z");
    private static final String ECLIPSELINK_ENUM_COLLECTION = "EclipseLink maps an enum element collection without @Enumerated to a VARCHAR column, which PostgreSQL does not compare to the bound ordinal";
    private static final Map<String, Object> TITLED = Map.of("title", Like.startsWith(TITLE_PREFIX));
    private static final String MANAGED_ID_IMMUTABLE = "EclipseLink and OpenJPA reject modifying the ID of a managed entity, so delete() does not unset it";
    private static final String LOADED_REFERENCE = "EclipseLink returns an already loaded instance from EntityManager#getReference()";
    private static final String ECLIPSELINK_ORDER_BY_FETCH_JOIN = "EclipseLink ignores the @OrderBy of a collection which a query fetch joins";
    private static final String ORDER_BY_WHOLE_EMBEDDABLE = "EclipseLink expands an @OrderBy on a whole embeddable with the fields of the target entity,"
        + " so loading the collection fails, and OpenJPA ignores it";

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
    private EntityRepository<Long, Album> albumRepository;
    @Inject
    private EntityRepository<Long, Track> trackRepository;
    @Inject
    private EntityRepository<Long, Club> clubRepository;
    @Inject
    private EntityRepository<MembershipId, Membership> membershipRepository;
    @Inject
    private EntityRepository<String, Note> noteRepository;
    @Inject
    private EntityRepository<Long, Document> documentRepository;
    @Inject
    private EntityRepository<Long, Config> configRepository;
    @Inject
    private EntityRepository<Long, Badge> badgeRepository;
    @Inject
    private EntityRepository<Long, Locker> lockerRepository;
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
     * {@link EntityRepository#countForeignKeyReferencesTo(Object)} also counts the rows referencing given entity via an association to a supertype of it, once
     * per row of an entity hierarchy, via an association to the actual subclass of the entity when counted via the repository of its supertype, and via an
     * association in an <code>@Embedded</code> value.
     */
    @Test
    void testCountForeignKeyReferencesToCountsReferencesViaSupertypeAndEmbedded() {
        var person = personRepository.getById(1L);
        var phonesOfPerson = selectLong("SELECT COUNT(phone) FROM Phone phone WHERE phone.owner.id = ?1", person.getId());
        var letter = (Letter) newDocument(new Letter(), "letter", "Ann");
        var reply = (Letter) newDocument(new Letter(), "reply", "Bob");
        var invoice = (Invoice) newDocument(new Invoice(), "invoice", 10);

        try {
            reply.setInReplyTo(letter);
            reply.setRecipient(new Recipient(person));
            invoice.setInReplyTo(letter);
            invoice.setCoverLetter(letter);
            Stream.of(letter, reply, invoice).forEach(document -> transactionServiceCDI.run(() -> documentRepository.persist(document)));
            assertEquals(
                3, documentRepository.countForeignKeyReferencesTo(letter), "References to letter via Document#inReplyTo and Invoice#coverLetter"
            );
            assertEquals(phonesOfPerson + 1, personRepository.countForeignKeyReferencesTo(person), "References to person via phones and Letter#recipient");
        }
        finally {
            Stream.of(reply, invoice, letter).filter(document -> document.getId() != null)
                .forEach(document -> transactionServiceCDI.run(() -> documentRepository.delete(documentRepository.getById(document.getId()))));
        }
    }

    /**
     * {@link EntityRepository#countForeignKeyReferencesTo(Object)} counts the rows referencing given entity via an association declared in a mapped superclass,
     * once per entity extending it.
     */
    @Test
    void testCountForeignKeyReferencesToCountsReferencesViaMappedSuperclassAssociation() {
        var person = personRepository.getById(1L);
        var phonesOfPerson = selectLong("SELECT COUNT(phone) FROM Phone phone WHERE phone.owner.id = ?1", person.getId());
        var badge = new Badge();
        var locker = new Locker();
        badge.setHolder(person);
        locker.setHolder(person);

        try {
            transactionServiceCDI.run(() -> {
                badgeRepository.persist(badge);
                lockerRepository.persist(locker);
            });
            assertEquals(phonesOfPerson + 2, personRepository.countForeignKeyReferencesTo(person), "References to person via phones and PersonalItem#holder");
        }
        finally {
            if (badge.getId() != null) {
                transactionServiceCDI.run(() -> badgeRepository.delete(badgeRepository.getById(badge.getId())));
            }

            if (locker.getId() != null) {
                transactionServiceCDI.run(() -> lockerRepository.delete(lockerRepository.getById(locker.getId())));
            }
        }
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

    /**
     * {@link Like} and {@link IgnoreCase} ignore the case of non-ASCII letters, the same in SQL as {@link Criteria#applies(Object)} does in memory, on every
     * database.
     */
    @Test
    void testLikeAndIgnoreCaseMatchNonAsciiCase() {
        var titles = List.of("\u00c4rger", "\u00e4rger", "\u00c4pfel");
        var articles = new ArrayList<Article>();

        try {
            titles.forEach(title -> articles.add(persistArticle(title, null)));
            var umlauts = Set.of(articles.get(0).getCode(), articles.get(1).getCode());

            for (var criteria : List.<Criteria<?>>of(
                Like.contains("\u00c4RG"), Like.contains("\u00e4rg"), IgnoreCase.value(TITLE_PREFIX + "\u00c4RGER"),
                IgnoreCase.value(TITLE_PREFIX + "\u00e4rger")
            )) {
                assertEquals(umlauts, Set.copyOf(codesOf(articlesTitled(criteria))), "Articles titled " + criteria);
                assertEquals(
                    umlauts, Set.copyOf(codesOf(articles.stream().filter(article -> criteria.applies(article.getTitle())).toList())),
                    "Articles titled " + criteria + " in memory"
                );
            }
        }
        finally {
            deleteArticles(articles);
        }
    }

    /**
     * {@link Like#pattern(String)} matches a character after the escape character literally, also when it is an ordinary character, on every database.
     */
    @Test
    void testLikePatternMatchesEscapedOrdinaryCharacterLiterally() {
        var articles = new ArrayList<Article>();

        try {
            articles.add(persistArticle("ab", null));
            articles.add(persistArticle("xb", null));
            assertEquals(List.of(articles.get(0).getCode()), codesOf(articlesTitled(Like.pattern(TITLE_PREFIX + "\\ab"))), "Articles titled ab");
        }
        finally {
            deleteArticles(articles);
        }
    }

    private PartialResultList<Article> articlesTitled(Criteria<?> criteria) {
        return articleRepository.getPage(Page.with().allMatch(Map.of("title", criteria)).build(), true);
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
     * A page and a {@link CriteriaQueryBuilder} exclude soft deleted entities, of both soft deletable types. A page with criteria on the soft deletable field
     * returns the soft deleted ones as well.
     */
    @Test
    void testPageAndCriteriaQueryBuilderExcludeSoftDeleted() {
        var active = new Text();
        var softDeleted = new Text();
        var activeId = transactionServiceCDI.call(() -> textRepository.persist(active));
        var softDeletedId = transactionServiceCDI.call(() -> textRepository.persist(softDeleted));
        var articles = List.of(persistArticle("active", null, "b"), persistArticle("deleted", null, "a"));

        try {
            transactionServiceCDI.run(() -> textRepository.softDelete(textRepository.getById(softDeletedId)));
            transactionServiceCDI.run(() -> articleRepository.softDelete(articleRepository.getById(articles.get(1).getCode())));
            var ids = List.of(activeId, softDeletedId);

            var allTexts = textRepository.getPage(Page.ALL, true);
            assertEquals(textRepository.list().stream().map(Text::getId).toList(), allTexts.stream().map(Text::getId).toList(), "Page without criteria");
            assertEquals(textRepository.list().size(), allTexts.getEstimatedTotalNumberOfResults(), "Count without criteria");
            assertEquals(List.of(activeId), idsOfTexts(Map.of("id", ids)), "Page with criteria");
            assertEquals(List.of(softDeletedId), idsOfTexts(Map.of("id", ids, "active", false)), "Page with criteria on soft deletable field");
            assertEquals(
                List.of(activeId), textRepository.list((criteriaBuilder, query, root) -> query.where(root.get("id").in(ids)), Map.of()).stream()
                    .map(Text::getId).toList(),
                "List via CriteriaQueryBuilder"
            );

            assertEquals(codesOf(articles.subList(0, 1)), codesOf(articleRepository.getPage(Page.with().allMatch(TITLED).build(), true)), "Articles");
            assertEquals(
                codesOf(articles.subList(0, 1)),
                codesOf(articleRepository.getPage(Page.with().range(0, 1).allMatch(TITLED).orderBy("tags", true).build(), true)),
                "First article ordered by tags"
            );
            var emptySoftDeleteCriteria = new HashMap<>(TITLED);
            emptySoftDeleteCriteria.put("deleted", List.of());
            assertEquals(
                codesOf(articles.subList(0, 1)), codesOf(articleRepository.getPage(Page.with().allMatch(emptySoftDeleteCriteria).build(), true)),
                "Articles with empty criteria on soft deletable field"
            );
            var softDeletedArticles = new HashMap<>(TITLED);
            softDeletedArticles.put("deleted", true);
            assertEquals(
                codesOf(articles.subList(1, 2)), codesOf(articleRepository.getPage(Page.with().allMatch(softDeletedArticles).build(), true)),
                "Articles with criteria on soft deletable field"
            );
        }
        finally {
            transactionServiceCDI.run(() -> List.of(activeId, softDeletedId).forEach(id -> textRepository.delete(textRepository.getById(id, true))));
            deleteArticles(articles);
        }
    }

    private List<Long> idsOfTexts(Map<String, Object> criteria) {
        var page = textRepository.getPage(Page.with().allMatch(criteria).build(), true);
        assertEquals(page.size(), page.getEstimatedTotalNumberOfResults(), "Count of " + criteria);
        return page.stream().map(Text::getId).toList();
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = "OpenJPA does not support EntityManager#getEntityGraph()")
    void testGetByIdWithLoadGraphExcludesSoftDeleted() {
        var text = new Text();
        var id = transactionServiceCDI.call(() -> textRepository.persist(text));

        try {
            assertNotNull(textRepository.getByIdWithLoadGraph(id, Text.PLAIN), "Active text");
            transactionServiceCDI.run(() -> textRepository.softDelete(textRepository.getById(id)));
            assertNull(textRepository.getByIdWithLoadGraph(id, Text.PLAIN), "Soft deleted text");
        }
        finally {
            transactionServiceCDI.run(() -> textRepository.delete(textRepository.getById(id, true)));
        }
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
     * A soft deleted entity stays excluded from {@code getById()} when the persistence context already holds a reference to it.
     */
    @Test
    void testGetByIdOfSoftDeletedEntityReferencedInPersistenceContextReturnsNull() {
        var text = new Text();
        var id = transactionServiceCDI.call(() -> textRepository.persist(text));

        try {
            transactionServiceCDI.run(() -> textRepository.softDelete(textRepository.getById(id)));
            assertNull(transactionServiceCDI.call(() -> {
                textRepository.getEntityManager().getReference(Text.class, id);
                return textRepository.getById(id);
            }), "Soft deleted text referenced in persistence context");
            assertNotNull(transactionServiceCDI.call(() -> {
                textRepository.getEntityManager().getReference(Text.class, id);
                return textRepository.getSoftDeletedById(id);
            }), "Soft deleted text referenced in persistence context via getSoftDeletedById");
        }
        finally {
            transactionServiceCDI.run(() -> textRepository.delete(textRepository.getById(id, true)));
        }
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
     * An update of a detached {@link VersionedBaseEntity} without changes keeps its version and last modified timestamp, and fires no updated event.
     */
    @Test
    void testUpdateOfUnchangedVersionedEntityKeepsVersionAndLastModifiedAndFiresNoEvent() {
        var remark = new Remark();
        remark.setText("unchanged");
        var id = transactionServiceCDI.call(() -> remarkRepository.persist(remark));

        try {
            var persisted = reloadRemark(id);
            entityEvents.clear();
            transactionServiceCDI.run(() -> remarkRepository.update(persisted));
            var updated = reloadRemark(id);
            assertEquals(persisted.getVersion(), updated.getVersion(), "Version");
            assertEquals(persisted.getLastModified(), updated.getLastModified(), "Last modified");
            assertEquals(List.of(), entityEvents.get(), "Fired events");
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
        var persistenceUnitUtil = personRepository.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
        var withPhones = transactionServiceCDI.call(() -> {
            var person = personRepository.getById(id);
            assertFalse(persistenceUnitUtil.isLoaded(person, "phones"), "Phones not loaded before");
            var fetched = personRepository.fetchLazyCollections(person, Person::getPhones);
            assertTrue(persistenceUnitUtil.isLoaded(fetched, "phones"), "Phones loaded after");
            return fetched;
        });
        assertEquals(TOTAL_PHONES_PER_PERSON_0, withPhones.getPhones().size(), "Phones fetched via getter");

        var withOptionalPhones = transactionServiceCDI.call(() -> personRepository.fetchLazyCollections(personRepository.findById(id), Person::getPhones));
        assertEquals(TOTAL_PHONES_PER_PERSON_0, withOptionalPhones.orElseThrow().getPhones().size(), "Phones fetched via getter on optional");
        assertTrue(personRepository.fetchLazyCollections(Optional.<Person>empty(), Person::getPhones).isEmpty(), "Empty optional");

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

            var withOptionalProperties = transactionServiceCDI.call(() -> attachmentRepository.fetchLazyMaps(attachmentRepository.findById(id)));
            assertEquals(Map.of("type", "text/plain"), withOptionalProperties.orElseThrow().getProperties(), "Properties on optional");
            var withOptionalContent = transactionServiceCDI.call(() -> attachmentRepository.fetchLazyBlobs(attachmentRepository.findById(id)));
            assertArrayEquals("content".getBytes(UTF_8), withOptionalContent.orElseThrow().getContent(), "Content on optional");
            assertTrue(attachmentRepository.fetchLazyMaps(Optional.<Attachment>empty()).isEmpty(), "Empty optional of maps");
            assertTrue(attachmentRepository.fetchLazyBlobs(Optional.<Attachment>empty()).isEmpty(), "Empty optional of blobs");
        }
        finally {
            transactionServiceCDI.run(() -> attachmentRepository.delete(attachmentRepository.getById(id)));
        }
    }

    /**
     * {@link EntityRepository#fetchLazyBlobs(Object)} fetches the blobs of a soft deleted entity, and throws {@link EntityNotFoundException} for an entity
     * which has in meanwhile been deleted.
     */
    @Test
    void testFetchLazyBlobsOfSoftDeletedAndOfDeletedEntity() {
        var attachment = new Attachment();
        attachment.setContent("content".getBytes(UTF_8));
        var id = transactionServiceCDI.call(() -> attachmentRepository.persist(attachment));
        var deleted = transactionServiceCDI.call(() -> attachmentRepository.getById(id));

        try {
            transactionServiceCDI.run(() -> attachmentRepository.softDelete(attachmentRepository.getById(id)));
            var softDeleted = transactionServiceCDI.call(() -> attachmentRepository.fetchLazyBlobs(attachmentRepository.getSoftDeletedById(id)));
            assertArrayEquals("content".getBytes(UTF_8), softDeleted.getContent(), "Content of soft deleted attachment");
        }
        finally {
            transactionServiceCDI.run(() -> attachmentRepository.delete(attachmentRepository.getById(id, true)));
        }

        assertThrows(EntityNotFoundException.class, () -> transactionServiceCDI.call(() -> attachmentRepository.fetchLazyBlobs(deleted)), "Deleted attachment");
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

    /**
     * {@link EntityRepository#delete(Object)} unsets the ID of a detached entity on every provider.
     */
    @Test
    void testDeleteUnsetsIdOfDetachedEntity() {
        var id = transactionServiceCDI.call(() -> textRepository.persist(new Text()));
        var detached = textRepository.getById(id);
        transactionServiceCDI.run(() -> textRepository.delete(detached));
        assertNull(detached.getId(), "ID of deleted detached text");
        assertNull(textRepository.getById(id, true), "Deleted text");
    }

    /**
     * {@link EntityRepository#delete(Object)} unsets the ID of a managed entity on Hibernate.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK_OR_OPENJPA, disabledReason = MANAGED_ID_IMMUTABLE)
    void testDeleteUnsetsIdOfManagedEntity() {
        var id = transactionServiceCDI.call(() -> textRepository.persist(new Text()));
        assertNull(deleteManagedText(id).getId(), "ID of deleted managed text");
        assertNull(textRepository.getById(id, true), "Deleted text");
    }

    /**
     * {@link EntityRepository#delete(Object)} keeps the ID of a managed entity on EclipseLink and OpenJPA, because they reject modifying it.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = HIBERNATE, disabledReason = "Hibernate unsets the ID of a managed entity")
    void testDeleteKeepsIdOfManagedEntity() {
        var id = transactionServiceCDI.call(() -> textRepository.persist(new Text()));
        assertEquals(id, deleteManagedText(id).getId(), "ID of deleted managed text");
        assertNull(textRepository.getById(id, true), "Deleted text");
    }

    private Text deleteManagedText(Long id) {
        return transactionServiceCDI.call(() -> {
            var managed = textRepository.getById(id);
            textRepository.delete(managed);
            return managed;
        });
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

    /**
     * {@link NonDeletable} also applies to a reference to the entity, which is a proxy on Hibernate, and to a subclass of the entity.
     */
    @Test
    void testNonDeletableCannotBeDeletedViaReferenceOrSubclass() {
        var config = new Config();
        config.setKey("nodelete.reference");
        config.setValue("value");
        var id = transactionServiceCDI.call(() -> configRepository.persist(config));

        try {
            assertThrows(
                NonDeletableEntityException.class,
                () -> transactionServiceCDI.run(() -> configRepository.delete(configRepository.getEntityManager().getReference(Config.class, id))),
                "Reference"
            );
            assertNotNull(configRepository.getById(id), "Config still exists after failed delete via reference");
            assertThrows(NonDeletableEntityException.class, () -> configRepository.delete(new Config() {

                private static final long serialVersionUID = 1L;

            }), "Subclass");
        }
        finally {
            transactionServiceCDI.run(() -> {
                var entityManager = configRepository.getEntityManager();
                entityManager.remove(entityManager.find(Config.class, id));
            });
        }
    }

    /**
     * {@link EntityRepository#reset(Object)} discards the changes of a detached entity, also when it is an initialized proxy, as a lazy association is on
     * Hibernate, and also when the persistence context of the transaction already holds a proxy of it. An uninitialized proxy is left as is.
     */
    @Test
    void testResetOfLazyAssociationDiscardsItsChanges() {
        var phoneId = phoneRepository.getPage(Page.ONE, false).get(0).getId();
        var owner = transactionServiceCDI.call(() -> {
            var lazyOwner = phoneRepository.getById(phoneId).getOwner();
            lazyOwner.getEmail();
            return lazyOwner;
        });
        var email = owner.getEmail();
        owner.setEmail("reset@example.com");
        personRepository.reset(owner);
        assertEquals(email, owner.getEmail(), "Email of owner after reset");

        owner.setEmail("reset@example.com");
        transactionServiceCDI.run(() -> {
            phoneRepository.getById(phoneId).getOwner();
            personRepository.reset(owner);
        });
        assertEquals(email, owner.getEmail(), "Email of owner after reset in transaction holding a proxy of it");

        var reference = transactionServiceCDI.call(() -> personRepository.getEntityManager().getReference(Person.class, owner.getId()));
        assertDoesNotThrow(() -> personRepository.reset(reference), "Reset of reference");
        var provider = personRepository.getProvider();
        assertTrue(!provider.isProxy(reference) || provider.isProxyUninitialized(reference), "Reference is left as is");
    }

    /**
     * {@link Provider#isProxyUninitialized(Object)} is true for a lazy association until it is read, which is a proxy on Hibernate only.
     */
    @Test
    void testIsProxyUninitializedOfLazyOwnerUntilRead() {
        var phoneId = phoneRepository.getPage(Page.ONE, false).get(0).getId();
        var provider = phoneRepository.getProvider();
        var states = transactionServiceCDI.call(() -> {
            var owner = phoneRepository.getById(phoneId).getOwner();
            var uninitializedBeforeRead = provider.isProxyUninitialized(owner);
            owner.getEmail();
            return List.of(provider.isProxy(owner), uninitializedBeforeRead, provider.isProxyUninitialized(owner));
        });
        var hibernate = provider == Provider.HIBERNATE;
        assertEquals(List.of(hibernate, hibernate, false), states, "Lazy owner is proxy, is uninitialized before read, is uninitialized after read");
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
     * <code>getByIds(ids)</code> returns the matching entities ordered by ID, descending, when there are more IDs than the maximum number of parameters in a
     * SQL Server statement.
     */
    @Test
    void testGetByIdsWithIdsBeyondSqlServerParameterLimit() {
        var ids = LongStream.rangeClosed(1, SQL_SERVER_MAX_PARAMETERS + 1L).boxed().toList();
        var expectedIds = personRepository.list().stream().map(Person::getId).filter(ids::contains).toList();
        assertFalse(expectedIds.isEmpty(), "Persons by IDs exist");
        assertEquals(expectedIds, personRepository.getByIds(ids).stream().map(Person::getId).toList(), "Persons by IDs");
    }

    /**
     * <code>getPage(page, count, fetchFields)</code> with criteria on the ID returns the matching entities when there are more IDs than the maximum number of
     * parameters in a SQL Server statement. Negated criteria on IDs return the other entities. SQL Server cannot run one paged query with that many parameters.
     */
    @Test
    @DisabledIfSystemProperty(named = TEST_DATABASE, matches = "(?i)sqlserver", disabledReason = "SQL Server supports at most 2100 parameters per statement")
    void testPageWithIdsBeyondSqlServerParameterLimit() {
        var ids = LongStream.rangeClosed(1, SQL_SERVER_MAX_PARAMETERS + 1L).boxed().toList();
        var allIds = personRepository.list().stream().map(Person::getId).sorted().toList();
        var expectedIds = allIds.stream().filter(ids::contains).toList();
        assertFalse(expectedIds.isEmpty(), "Persons by IDs exist");

        var page = personRepository.getPage(Page.with().allMatch(Map.of("id", ids)).build(), true, "phones");
        assertEquals(expectedIds, page.stream().map(Person::getId).sorted().toList(), "Page of persons by IDs");
        assertEquals(expectedIds.size(), page.getEstimatedTotalNumberOfResults(), "Count of persons by IDs");

        var firstIds = allIds.subList(0, allIds.size() / 2);
        var notFirst = personRepository.getPage(Page.with().allMatch(Map.of("id", Not.value(firstIds))).build(), false);
        assertEquals(allIds.subList(firstIds.size(), allIds.size()), notFirst.stream().map(Person::getId).sorted().toList(), "Page of persons not by IDs");
    }

    /**
     * An all {@link Select} and a criteria {@link Select} exclude soft deleted entities, like <code>list()</code> and a {@link CriteriaQueryBuilder}. A JPQL
     * {@link Select} returns them, like a JPQL query.
     */
    @Test
    void testSelectExcludesSoftDeletedUnlessByJpql() {
        var activeId = transactionServiceCDI.call(() -> textRepository.persist(new Text()));
        var softDeletedId = transactionServiceCDI.call(() -> textRepository.persist(new Text()));

        try {
            transactionServiceCDI.run(() -> textRepository.softDelete(textRepository.getById(softDeletedId)));
            var ids = List.of(activeId, softDeletedId);

            assertEquals(idsOf(textRepository.list()), idsOf(textRepository.list(Select.all().build())), "All select");
            assertEquals(
                List.of(activeId), idsOf(
                    textRepository.list(
                        Select.<Text>criteria(
                            (criteriaBuilder, query, root) -> query.where(root.get("id").in(ids)),
                            Map.of()
                        ).build()
                    )
                ),
                "Criteria select"
            );
            assertEquals(
                List.of(softDeletedId, activeId),
                idsOf(textRepository.list(Select.jpql("WHERE e.id IN (?1, ?2) ORDER BY e.id DESC", activeId, softDeletedId).build())), "JPQL select"
            );
        }
        finally {
            transactionServiceCDI.run(() -> textRepository.getByIds(List.of(activeId, softDeletedId), true).forEach(textRepository::delete));
        }
    }

    /**
     * {@link EntityRepository#find(Select)} and {@link EntityRepository#findFirst(Select)} exclude a soft deleted entity when selecting by criteria or all, and
     * not when selecting by JPQL, also when the select is cacheable or not.
     */
    @Test
    void testFindAndFindFirstBySelectExcludeSoftDeletedUnlessByJpql() {
        var softDeletedId = transactionServiceCDI.call(() -> textRepository.persist(new Text()));

        try {
            transactionServiceCDI.run(() -> textRepository.softDelete(textRepository.getById(softDeletedId)));

            for (var cacheable : List.of(false, true)) {
                var byCriteria = Select
                    .<Text>criteria((criteriaBuilder, query, root) -> query.where(criteriaBuilder.equal(root.get("id"), softDeletedId)), Map.of())
                    .cacheable(cacheable)
                    .build();
                var byJpql = Select.jpql("WHERE e.id = ?1", softDeletedId).cacheable(cacheable).build();
                var all = Select.all().cacheable(cacheable).build();

                assertEquals(Optional.empty(), textRepository.find(byCriteria), "Find by criteria, cacheable " + cacheable);
                assertEquals(Optional.empty(), textRepository.findFirst(byCriteria), "Find first by criteria, cacheable " + cacheable);
                assertEquals(Optional.of(softDeletedId), textRepository.find(byJpql).map(Text::getId), "Find by JPQL, cacheable " + cacheable);
                assertEquals(Optional.of(softDeletedId), textRepository.findFirst(byJpql).map(Text::getId), "Find first by JPQL, cacheable " + cacheable);
                assertNotEquals(Optional.of(softDeletedId), textRepository.findFirst(all).map(Text::getId), "Find first of all, cacheable " + cacheable);
            }
        }
        finally {
            transactionServiceCDI.run(() -> textRepository.delete(textRepository.getById(softDeletedId, true)));
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
    void testCriteriaOnElementCollectionMatchesDuplicateAndCaseVariantItemsOnce() {
        var articles = List.of(persistArticle("tagged", null, "x", "y"));

        try {
            var expected = codesOf(articles);
            assertEquals(expected, codesOfTagged(List.of("x", "x")), "Duplicate items");
            assertEquals(expected, codesOfTagged(List.of("x", "X")), "Case variant items");
            assertEquals(expected, codesOfTagged(List.of("X", "y")), "Case variant item and other item");
        }
        finally {
            deleteArticles(articles);
        }
    }

    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testCriteriaOnElementCollectionMatchesNestedMultiValuedItem() {
        var articles = List.of(persistArticle("rated", null, Set.of(1, 3), Set.of()));

        try {
            var criteria = new HashMap<>(TITLED);
            criteria.put("ratings", List.of(List.of(1, 2), 3));
            var page = articleRepository.getPage(Page.with().allMatch(criteria).build(), true);
            assertEquals(codesOf(articles), codesOf(page), "Nested multi-valued item and other item");
            assertEquals(page.size(), page.getEstimatedTotalNumberOfResults(), "Count of nested multi-valued item and other item");
        }
        finally {
            deleteArticles(articles);
        }
    }

    /**
     * A nested multi-valued item of an element collection criteria matches when at least one of its values is an element, also when several of them are.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testCriteriaOnElementCollectionMatchesNestedMultiValuedItemWhenSeveralElementsMatchIt() {
        var articles = List.of(
            persistArticle("all rated", null, Set.of(1, 2, 3), Set.of()), persistArticle("two rated", null, Set.of(2, 3), Set.of()),
            persistArticle("unrated three", null, Set.of(1, 2), Set.of())
        );

        try {
            assertEquals(codesOf(articles.subList(0, 2)), codesOfRated(List.of(List.of(1, 2), 3)), "Nested multi-valued item and other item");
            assertEquals(codesOf(articles), codesOfRated(List.of(List.of(1, 2))), "Nested multi-valued item alone");
            assertEquals(
                codesOf(articles.subList(2, 3)), codesOfRated(Not.value(List.of(List.of(1, 2), 3))), "Negated nested multi-valued item and other item"
            );
        }
        finally {
            deleteArticles(articles);
        }
    }

    private List<Long> codesOfRated(Object ratings) {
        var criteria = new HashMap<>(TITLED);
        criteria.put("ratings", ratings);
        var page = articleRepository.getPage(Page.with().allMatch(criteria).orderBy("id", true).build(), true);
        assertEquals(page.size(), page.getEstimatedTotalNumberOfResults(), "Count of " + ratings);
        return codesOf(page);
    }

    /**
     * A criteria on an empty or an unknown field, and a fetch of an unknown field, are rejected with {@link IllegalArgumentException}.
     */
    @Test
    void testPageWithEmptyOrUnknownFieldThrowsIllegalArgumentException() {
        for (var field : List.of("", "nonexistent", "nonexistent.number", "phones.nonexistent", "phones.nonexistent.number")) {
            assertThrows(
                IllegalArgumentException.class, () -> personRepository.getPage(Page.with().allMatch(Map.of(field, "x")).build(), false), "Criteria on " + field
            );
        }

        assertThrows(IllegalArgumentException.class, () -> personRepository.getPage(Page.ONE, false, "nonexistent"), "Fetch of nonexistent");
        assertThrows(IllegalArgumentException.class, () -> personRepository.getPage(Page.ONE, false, "phones.nonexistent"), "Fetch of phones.nonexistent");
    }

    @Test
    void testPageOfDtoWithoutMappingThrowsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class, () -> personRepository.getPage(Page.ONE, false, PersonCard.class, (builder, query, person) -> new LinkedHashMap<>())
        );
    }

    /**
     * A plain criteria value which is not a String, on a field of a type for which no {@link Criteria} applies, such as a <code>byte[]</code>, throws
     * {@link UnsupportedOperationException}. A String value is matched by {@link IgnoreCase} instead.
     */
    @Test
    void testPageWithPlainCriteriaOnUnsupportedTypeThrowsUnsupportedOperationException() {
        assertThrows(
            UnsupportedOperationException.class, () -> attachmentRepository.getPage(Page.with().allMatch(Map.of("content", new Object())).build(), false)
        );
    }

    @Test
    @DisabledIfSystemProperty(
        named = TEST_DATABASE, matches = "(?i)mysql|sqlserver", disabledReason = "Case insensitive collation rejects case variant elements"
    )
    void testCriteriaOnElementCollectionMatchesCaseVariantElementsOnce() {
        var articles = List.of(persistArticle("java", null, "Java", "java", "y"));

        try {
            var expected = codesOf(articles);
            assertEquals(expected, codesOfTagged("java"), "Item matching two case variant elements");
            assertEquals(expected, codesOfTagged(List.of("JAVA")), "Single item list matching two case variant elements");
            assertEquals(expected, codesOfTagged(List.of("java", "y")), "Item matching two case variant elements and other item");
        }
        finally {
            deleteArticles(articles);
        }
    }

    private List<Long> codesOfTagged(Object tags) {
        var criteria = new HashMap<>(TITLED);
        criteria.put("tags", tags);
        var page = articleRepository.getPage(Page.with().allMatch(criteria).build(), true);
        assertEquals(page.size(), page.getEstimatedTotalNumberOfResults(), "Count of " + tags);
        return codesOf(page);
    }

    /**
     * The fetched elements of a page ordered by their nullable field are in the same order as the database sorts that field, including the position of
     * <code>NULL</code>, of mixed case values and of accented values in the collation of the label column, see <code>OmniPersistenceIT#ormOf</code>.
     */
    @Test
    void testPageWithPhonesOrderedByLabelSortsFetchedPhonesAsDatabase() {
        var phones = persistTemporaryPhones(Arrays.asList("anna", "Bob", "émile", "Zed", null));

        try {
            for (var ascending : List.of(true, false)) {
                var expected = phoneRepository
                    .list("SELECT f FROM Person p JOIN p.phones f WHERE p.id = ?1 ORDER BY f.label " + (ascending ? "ASC" : "DESC"), 1L).stream()
                    .map(Phone::getLabel)
                    .toList();
                var page = personRepository.getPage(Page.with().allMatch(Map.of("id", 1L)).orderBy("phones.label", ascending).build(), false, "phones");
                assertEquals(expected, page.get(0).getPhones().stream().map(Phone::getLabel).toList(), "Ascending " + ascending);
            }
        }
        finally {
            deleteTemporaryPhones(phones);
        }
    }

    /**
     * The fetched elements of a page filtered by an {@link Order} on their String field are those which the database selects by that criteria, also with mixed
     * case values in the binary collation of the test databases.
     */
    @Test
    void testPageWithPhonesFilteredByOrderOnMixedCaseNumberKeepsPhonesSelectedByDatabase() {
        var phones = persistTemporaryPhones(List.of("a1", "B1"));

        try {
            var expected = phoneRepository.list("SELECT f FROM Person p JOIN p.phones f WHERE p.id = ?1 AND f.number > ?2", 1L, "a").stream().map(Phone::getId)
                .collect(toSet());
            var page = personRepository.getPage(Page.with().allMatch(Map.of("id", 1L, "phones.number", Order.greaterThan("a"))).build(), false, "phones");
            assertEquals(List.of(1L), page.stream().map(Person::getId).toList(), "Person");
            assertEquals(expected, page.get(0).getPhones().stream().map(Phone::getId).collect(toSet()), "Fetched phones");
        }
        finally {
            deleteTemporaryPhones(phones);
        }
    }

    /**
     * The fetched elements of a page filtered by a criteria on their String field are those which the database selects by that criteria, in the collation of
     * the label column, see <code>OmniPersistenceIT#ormOf</code>. On MySQL and SQL Server this collation ignores case and accents.
     */
    @Test
    void testPageWithPhonesFilteredByLabelKeepsPhonesSelectedByDatabase() {
        var phones = persistTemporaryPhones(List.of("anna", "Bob", "émile", "Zed"));

        try {
            assertPhonesFilteredByLabel(Order.greaterThanOrEqualTo("b"), "f.label >= ?2", "b");
            assertPhonesFilteredByLabel("Emile", "LOWER(f.label) = LOWER(?2)", "Emile");
            assertPhonesFilteredByLabel(Like.contains("EMIL"), "LOWER(f.label) LIKE ?2", "%emil%");
        }
        finally {
            deleteTemporaryPhones(phones);
        }
    }

    /**
     * {@link Like} and {@link IgnoreCase} ignore accents in SQL on a column with an accent-insensitive collation, the same as {@link Criteria#applies(Object)}
     * does in memory. The label column has such a collation on MySQL and SQL Server, see <code>OmniPersistenceIT#ormOf</code>.
     */
    @Test
    @DisabledIfSystemProperty(
        named = TEST_DATABASE, matches = "(?i)h2|postgresql|db2", disabledReason = "Only MySQL and SQL Server have an accent-insensitive label"
    )
    void testLikeAndIgnoreCaseIgnoreAccentsOnAccentInsensitiveColumn() {
        var phones = persistTemporaryPhones(List.of("\u00c9mile", "anna"));

        try {
            for (var criteria : List.<Criteria<?>>of(Like.contains("emil"), IgnoreCase.value("EMILE"))) {
                var expected = phones.stream().filter(phone -> criteria.applies(phone.getLabel())).map(Phone::getId).collect(toSet());
                var page = personRepository.getPage(Page.with().allMatch(Map.of("id", 1L, "phones.label", criteria)).build(), false, "phones");
                assertEquals(1, expected.size(), "In memory by " + criteria);
                assertEquals(List.of(1L), page.stream().map(Person::getId).toList(), "Person by " + criteria);
                assertEquals(expected, page.get(0).getPhones().stream().map(Phone::getId).collect(toSet()), "Fetched phones by " + criteria);
            }
        }
        finally {
            deleteTemporaryPhones(phones);
        }
    }

    private void assertPhonesFilteredByLabel(Object criteria, String condition, String parameter) {
        var expected = phoneRepository.list("SELECT f FROM Person p JOIN p.phones f WHERE p.id = ?1 AND " + condition, 1L, parameter).stream()
            .map(Phone::getId)
            .collect(toSet());
        var page = personRepository.getPage(Page.with().allMatch(Map.of("id", 1L, "phones.label", criteria)).build(), false, "phones");
        assertEquals(expected.isEmpty() ? List.of() : List.of(1L), page.stream().map(Person::getId).toList(), "Person by " + criteria);

        if (!expected.isEmpty()) {
            assertEquals(expected, page.get(0).getPhones().stream().map(Phone::getId).collect(toSet()), "Fetched phones by " + criteria);
        }
    }

    /**
     * The fetched elements of a page which is not ordered by them are sorted by their natural order.
     */
    @Test
    void testPageWithTagsNotOrderedByThemSortsFetchedTagsByNaturalOrder() {
        var articles = new ArrayList<Article>();

        try {
            var tagged = persistArticle("tagged", null, "zulu", "alpha", "mike", "bravo", "x-ray", "charlie", "yankee", "delta");
            articles.add(tagged);
            var page = articleRepository.getPage(Page.with().allMatch(Map.of("id", tagged.getCode())).build(), false, "tags");
            var tags = List.copyOf(page.get(0).getTags());
            assertEquals(List.of("alpha", "bravo", "charlie", "delta", "mike", "x-ray", "yankee", "zulu"), tags, "Tags");
        }
        finally {
            deleteArticles(articles);
        }
    }

    /**
     * The fetched elements of a collection annotated with <code>@OrderBy</code> are in that order when the page is not ordered by them, on every provider, and
     * follow the ordering of the page when it is, also when it is ordered by their ID.
     */
    @Test
    void testPageWithTracksKeepsMappedOrderUnlessOrderedByThem() {
        var album = persistAlbum();

        try {
            var byId = Map.<String, Object>of("id", album.getId());
            var mapped = albumRepository.getPage(Page.with().allMatch(byId).build(), false, "tracks");
            assertEquals(List.of("c", "b", "a"), mapped.get(0).getTracks().stream().map(Track::getTitle).toList(), "Mapped order");
            var ordered = albumRepository.getPage(Page.with().allMatch(byId).orderBy("tracks.title", true).build(), false, "tracks");
            assertEquals(List.of("a", "b", "c"), ordered.get(0).getTracks().stream().map(Track::getTitle).toList(), "Ordering of page");
            var orderedById = albumRepository.getPage(Page.with().allMatch(byId).orderBy("tracks.id", true).build(), false, "tracks");
            var titlesById = album.getTracks().stream().sorted(comparing(Track::getId)).map(Track::getTitle).toList();
            assertEquals(titlesById, orderedById.get(0).getTracks().stream().map(Track::getTitle).toList(), "Ordering of page by ID");
            var titlesByIdDescending = new ArrayList<>(titlesById);
            Collections.reverse(titlesByIdDescending);
            var orderedByIdDescending = albumRepository.getPage(Page.with().allMatch(byId).orderBy("tracks.id", false).build(), false, "tracks");
            var descendingTitles = orderedByIdDescending.get(0).getTracks().stream().map(Track::getTitle).toList();
            assertEquals(titlesByIdDescending, descendingTitles, "Ordering of page by ID descending");
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * The fetched embeddable elements of a page filtered or ordered by their fields are those which the database selects, in the order of the database. This
     * includes an element whose association is <code>NULL</code>, and the position of <code>NULL</code> in the ordering.
     */
    @Test
    void testPageWithCreditsFilteredOrOrderedByThemFollowsDatabase() {
        var album = persistAlbum();

        try {
            var byId = Map.<String, Object>of("id", album.getId());
            assertCreditsFilteredBy(album, "credits.name", Like.contains("AN"), "LOWER(c.name) LIKE ?2", "%an%");
            assertCreditsFilteredBy(album, "credits.person", null, "c.person IS NULL");
            var ordered = albumRepository.getPage(Page.with().allMatch(byId).orderBy("credits.role", true).build(), false, "credits");
            assertEquals(creditNames(album, " ORDER BY c.role"), namesOf(ordered.get(0).getCredits()), "Credits ordered by role");
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * The fetched elements of a collection of entities with an embedded ID, annotated with <code>@OrderBy</code>, are in that order.
     */
    @Test
    void testPageWithMembershipsKeepsMappedOrderOfEmbeddedIdEntities() {
        var start = LocalDate.of(2026, 1, 1);
        var memberships = List.of(
            new Membership(new MembershipId("ordered-a", "x"), new Period(start, null)),
            new Membership(new MembershipId("ordered-b", "x"), new Period(start.plusMonths(2), null)),
            new Membership(new MembershipId("ordered-c", "x"), new Period(start.plusMonths(1), null))
        );
        memberships.forEach(membership -> transactionServiceCDI.run(() -> membershipRepository.persist(membership)));
        var album = new Album();
        album.setTitle("memberships");
        album.getMemberships().addAll(memberships);
        transactionServiceCDI.run(() -> albumRepository.persist(album));

        try {
            var page = albumRepository.getPage(Page.with().allMatch(Map.of("id", album.getId())).build(), false, "memberships");
            assertEquals(List.of("ordered-b", "ordered-c", "ordered-a"), memberNamesOf(page.get(0).getMemberships()), "Mapped order");
        }
        finally {
            deleteAlbum(album);
            transactionServiceCDI.run(() -> memberships.forEach(membership -> membershipRepository.delete(membershipRepository.getById(membership.getId()))));
        }
    }

    /**
     * The fetched elements of a collection annotated with <code>@OrderBy</code> on two fields are in that order, also among the elements which tie on the first
     * field.
     */
    @Test
    void testPageWithTracksKeepsMappedOrderOnTwoFields() {
        var album = new Album();
        album.setTitle("discs");
        addTrack(album, 2, "c");
        addTrack(album, 1, "a");
        addTrack(album, 2, "d");
        addTrack(album, 1, "b");
        transactionServiceCDI.run(() -> albumRepository.persist(album));

        try {
            var page = albumRepository.getPage(Page.with().allMatch(Map.of("id", album.getId())).build(), false, "tracks");
            assertEquals(List.of("b", "a", "d", "c"), titlesOf(page.get(0).getTracks()), "Mapped order");
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * The fetched elements of an element collection annotated with <code>@OrderBy</code> are in that order: embeddables by the named field as the database
     * orders it, <code>NULL</code> included, and basic values by their value.
     */
    @Test
    void testPageWithElementCollectionsKeepsMappedOrder() {
        var album = persistAlbum();

        try {
            var page = albumRepository.getPage(Page.with().allMatch(Map.of("id", album.getId())).build(), false, "credits", "genres");
            assertEquals(creditNames(album, " ORDER BY c.role DESC"), namesOf(page.get(0).getCredits()), "Credits in mapped order");
            assertEquals(List.of("blues", "jazz", "rock"), page.get(0).getGenres(), "Genres in mapped order");
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * A ranged page with a fetch of a collection annotated with <code>@OrderBy</code> returns the entities of the range, each with all its elements in that
     * order.
     */
    @Test
    void testRangedPageOfAlbumsKeepsAllTracksOfEachAlbumInMappedOrder() {
        var albums = new ArrayList<Album>();

        try {
            List.of("range-c", "range-a", "range-b").forEach(title -> albums.add(persistAlbum(title)));
            var page = albumRepository.getPage(
                Page.with().range(1, 2).allMatch(Map.of("title", Like.startsWith("range-"))).orderBy("title", true).build(), true, "tracks"
            );
            assertEquals(List.of("range-b", "range-c"), page.stream().map(Album::getTitle).toList(), "Albums");
            assertEquals(3, page.getEstimatedTotalNumberOfResults(), "Count");
            page.forEach(album -> assertEquals(List.of("c", "b", "a"), titlesOf(album.getTracks()), "Tracks of " + album.getTitle()));
        }
        finally {
            albums.forEach(this::deleteAlbum);
        }
    }

    /**
     * A fetch of a collection annotated with <code>@OrderBy</code> under a fetched association holds each element once, in that order, also when several
     * entities of the page share the association.
     */
    @Test
    void testPageOfTracksWithFetchedAlbumTracksKeepsMappedOrder() {
        var album = persistAlbum();

        try {
            var page = trackRepository.getPage(
                Page.with().allMatch(Map.of("album.id", album.getId())).orderBy("title", true).build(), true, "album", "album.tracks"
            );
            assertEquals(List.of("a", "b", "c"), titlesOf(page), "Tracks");
            page.forEach(track -> assertEquals(List.of("c", "b", "a"), titlesOf(track.getAlbum().getTracks()), "Tracks of album of " + track.getTitle()));
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * The fetched elements of a collection whose <code>@OrderBy</code> names a whole embeddable are in the order of the provider, the same as when the
     * collection is loaded lazily.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK_OR_OPENJPA, disabledReason = ORDER_BY_WHOLE_EMBEDDABLE)
    void testPageWithMembershipsOrderedByWholeEmbeddableFollowsProvider() {
        var start = LocalDate.of(2026, 1, 1);
        var memberships = List.of(
            new Membership(new MembershipId("club-a", "x"), new Period(start, null)),
            new Membership(new MembershipId("club-b", "x"), new Period(start.plusMonths(2), null)),
            new Membership(new MembershipId("club-c", "x"), new Period(start.plusMonths(1), null))
        );
        var club = new Club();
        club.setName("club");
        club.getMemberships().addAll(memberships);

        try {
            memberships.forEach(membership -> transactionServiceCDI.run(() -> membershipRepository.persist(membership)));
            transactionServiceCDI.run(() -> clubRepository.persist(club));
            var page = clubRepository.getPage(Page.with().allMatch(Map.of("id", club.getId())).build(), false, "memberships");
            var lazilyLoaded = transactionServiceCDI.call(() -> memberNamesOf(clubRepository.getById(club.getId()).getMemberships()));
            assertEquals(lazilyLoaded, memberNamesOf(page.get(0).getMemberships()), "Order of provider");
        }
        finally {
            transactionServiceCDI.run(
                () -> Optional.ofNullable(club.getId()).map(clubRepository::getById).ifPresent(clubRepository::delete)
            );
            transactionServiceCDI.run(
                () -> memberships.stream().map(membership -> membershipRepository.getById(membership.getId())).filter(Objects::nonNull)
                    .forEach(membershipRepository::delete)
            );
        }
    }

    /**
     * A load graph of a collection annotated with <code>@OrderBy</code> loads its elements in that order.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = "OpenJPA does not support EntityManager#getEntityGraph()")
    void testGetByIdWithLoadGraphKeepsMappedOrderOfTracks() {
        var album = persistAlbum();

        try {
            assertEquals(List.of("c", "b", "a"), titlesOf(albumRepository.getByIdWithLoadGraph(album.getId(), Album.WITH_TRACKS).getTracks()), "Mapped order");
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * A fetch in a {@link CriteriaQueryBuilder} of a collection annotated with <code>@OrderBy</code> loads its elements in that order.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = ECLIPSELINK, disabledReason = ECLIPSELINK_ORDER_BY_FETCH_JOIN)
    void testListByCriteriaQueryBuilderWithFetchKeepsMappedOrderOfTracks() {
        var album = persistAlbum();

        try {
            CriteriaQueryBuilder<Album> withTracks = (builder, query, root) -> {
                root.fetch("tracks", LEFT);
                query.distinct(true).where(builder.equal(root.get("id"), album.getId()));
            };
            assertEquals(List.of("c", "b", "a"), titlesOf(albumRepository.list(withTracks, Map.of()).get(0).getTracks()), "Mapped order");
        }
        finally {
            deleteAlbum(album);
        }
    }

    /**
     * A criteria on a basic field of an association of embeddable elements returns and counts the entities with a matching element.
     */
    @Test
    void testPageWithCriteriaOnFieldOfAssociationOfCreditsCounts() {
        var credited = persistAlbum("credited-a");
        var uncredited = new Album();
        uncredited.setTitle("credited-b");
        uncredited.getCredits().add(new Credit("Nobody", "kazoo", null));
        transactionServiceCDI.run(() -> albumRepository.persist(uncredited));

        try {
            var criteria = Map.<String, Object>of("title", Like.startsWith("credited-"), "credits.person.email", personRepository.getById(1L).getEmail());
            var page = albumRepository.getPage(Page.with().allMatch(criteria).build(), true);
            assertEquals(List.of(credited.getId()), page.stream().map(Album::getId).toList(), "Albums");
            assertEquals(1, page.getEstimatedTotalNumberOfResults(), "Count");
        }
        finally {
            List.of(credited, uncredited).forEach(this::deleteAlbum);
        }
    }

    /**
     * A <code>null</code> criteria on a collection of embeddables matches the entities without elements, and a negated one the entities with elements. It
     * restricts neither the elements of an ordering on the collection nor the fetched elements. The count is not asserted on OpenJPA, see
     * {@link OmniPersistenceIT#OPENJPA_COUNT}.
     */
    @Test
    void testPageWithNullCriteriaOnCreditsMatchesAlbumsWithoutCredits() {
        var credited = persistAlbum("nullcredits-a");
        var uncredited = new Album();
        uncredited.setTitle("nullcredits-b");
        transactionServiceCDI.run(() -> albumRepository.persist(uncredited));

        try {
            var criteria = new HashMap<String, Object>(Map.of("title", Like.startsWith("nullcredits-")));
            criteria.put("credits", null);
            var without = albumRepository.getPage(Page.with().allMatch(criteria).build(), true);
            assertEquals(List.of(uncredited.getId()), without.stream().map(Album::getId).toList(), "Without credits");
            assertCountUnlessOpenJPA(without, "Count without credits");
            criteria.put("credits", Not.value(null));
            var with = albumRepository.getPage(Page.with().allMatch(criteria).orderBy("credits.role", true).build(), true, "credits");
            assertEquals(List.of(credited.getId()), with.stream().map(Album::getId).toList(), "With credits");
            assertCountUnlessOpenJPA(with, "Count with credits");
            assertEquals(credited.getCredits().size(), with.get(0).getCredits().size(), "Fetched credits");
        }
        finally {
            List.of(credited, uncredited).forEach(this::deleteAlbum);
        }
    }

    /**
     * A <code>null</code> criteria on a collection nested in a collection matches the entities of which no element has nested elements, also without elements,
     * and a negated one the entities of which an element has nested elements. The count is not asserted on OpenJPA, see
     * {@link OmniPersistenceIT#OPENJPA_COUNT}.
     */
    @Test
    void testPageWithNullCriteriaOnNestedCollectionMatchesEntitiesWithoutNestedElements() {
        var start = LocalDate.of(2026, 1, 1);
        var memberships = List.of(
            new Membership(new MembershipId("nested-a", "x"), new Period(start, null)),
            new Membership(new MembershipId("nested-a", "y"), new Period(start, null)),
            new Membership(new MembershipId("nested-c", "x"), new Period(start, null)),
            new Membership(new MembershipId("nested-c", "y"), new Period(start, null))
        );
        memberships.get(2).getRoles().add("chair");
        var albums = new ArrayList<Album>();

        try {
            memberships.forEach(membership -> transactionServiceCDI.run(() -> membershipRepository.persist(membership)));

            for (var title : List.of("nested-a", "nested-b", "nested-c")) {
                var album = new Album();
                album.setTitle(title);
                memberships.stream().filter(membership -> membership.getId().getMemberName().equals(title)).forEach(album.getMemberships()::add);
                transactionServiceCDI.run(() -> albumRepository.persist(album));
                albums.add(album);
            }

            var criteria = new HashMap<String, Object>(Map.of("title", Like.startsWith("nested-")));
            criteria.put("memberships.roles", null);
            var without = albumRepository.getPage(Page.with().allMatch(criteria).orderBy("title", true).build(), true);
            assertEquals(List.of("nested-a", "nested-b"), without.stream().map(Album::getTitle).toList(), "Without roles");
            assertCountUnlessOpenJPA(without, "Count without roles");
            criteria.put("memberships.roles", Not.value(null));
            var with = albumRepository.getPage(Page.with().allMatch(criteria).orderBy("title", true).build(), true);
            assertEquals(List.of("nested-c"), with.stream().map(Album::getTitle).toList(), "With roles");
            assertCountUnlessOpenJPA(with, "Count with roles");
        }
        finally {
            albums.forEach(this::deleteAlbum);
            transactionServiceCDI.run(
                () -> memberships.stream().map(membership -> membershipRepository.getById(membership.getId())).filter(Objects::nonNull)
                    .forEach(membershipRepository::delete)
            );
        }
    }

    private void assertCreditsFilteredBy(Album album, String field, Object criteria, String condition, Object... parameters) {
        var criteriaMap = new HashMap<String, Object>(Map.of("id", album.getId()));
        criteriaMap.put(field, criteria);
        var page = albumRepository.getPage(Page.with().allMatch(criteriaMap).build(), false, "credits");
        var expected = Set.copyOf(creditNames(album, " AND " + condition, parameters));
        assertEquals(1, page.size(), "Album by " + field);
        assertEquals(expected, Set.copyOf(namesOf(page.get(0).getCredits())), "Credits by " + field);
    }

    private List<String> creditNames(Album album, String condition, Object... parameters) {
        var query = albumRepository.getEntityManager()
            .createQuery("SELECT c.name FROM Album a JOIN a.credits c WHERE a.id = ?1" + condition, String.class)
            .setParameter(1, album.getId());

        for (var i = 0; i < parameters.length; i++) {
            query.setParameter(i + 2, parameters[i]);
        }

        return List.copyOf(query.getResultList());
    }

    private static List<String> namesOf(List<Credit> credits) {
        return credits.stream().map(Credit::getName).toList();
    }

    private Album persistAlbum() {
        return persistAlbum("album");
    }

    private Album persistAlbum(String title) {
        var album = new Album();
        album.setTitle(title);
        List.of("a", "b", "c").forEach(trackTitle -> addTrack(album, 1, trackTitle));
        album.getGenres().addAll(List.of("rock", "jazz", "blues"));

        transactionServiceCDI.run(() -> {
            var person = personRepository.getById(1L);
            album.getCredits().addAll(
                List.of(new Credit("anna", "lead", person), new Credit("Bob", null, null), new Credit("Jan", "bass", person), new Credit("Zed", "drums", null))
            );
            albumRepository.persist(album);
        });
        return album;
    }

    private static void addTrack(Album album, int disc, String title) {
        var track = new Track();
        track.setDisc(disc);
        track.setTitle(title);
        track.setAlbum(album);
        album.getTracks().add(track);
    }

    private void deleteAlbum(Album album) {
        transactionServiceCDI.run(() -> albumRepository.delete(albumRepository.getById(album.getId())));
    }

    private static List<String> titlesOf(List<Track> tracks) {
        return tracks.stream().map(Track::getTitle).toList();
    }

    private static List<String> memberNamesOf(List<Membership> memberships) {
        return memberships.stream().map(membership -> membership.getId().getMemberName()).toList();
    }

    private List<Phone> persistTemporaryPhones(List<String> values) {
        var phones = new ArrayList<Phone>();

        for (var value : values) {
            var phone = new Phone();
            phone.setType(Phone.Type.WORK);
            phone.setNumber(value == null ? "temporary" : value);
            phone.setLabel(value);
            transactionServiceCDI.run(() -> {
                var owner = personRepository.getById(1L);
                phone.setOwner(owner);
                phoneRepository.persist(phone);
                owner.getPhones().add(phone);
            });
            phones.add(phone);
        }

        return phones;
    }

    private void deleteTemporaryPhones(List<Phone> phones) {
        var ids = phones.stream().map(Phone::getId).filter(Objects::nonNull).collect(toSet());
        transactionServiceCDI.run(() -> {
            personRepository.getById(1L).getPhones().removeIf(phone -> ids.contains(phone.getId()));
            ids.stream().map(phoneRepository::getById).filter(Objects::nonNull).forEach(phoneRepository::delete);
        });
    }

    @Test
    void testManageIfNecessaryOfDtoReturnsItUnchanged() {
        var card = new PersonCard(1L, "card@example.com", "address", 0L);
        assertSame(card, transactionServiceCDI.call(() -> personRepository.manageIfNecessary(card)), "DTO");
    }

    /**
     * {@link EntityRepository#manage(Object)} of an entity which has been deleted in the meanwhile throws {@link EntityNotFoundException}, while
     * {@link EntityRepository#manageIfNecessary(Object)} returns it unchanged.
     */
    @Test
    void testManageOfDeletedEntityThrowsWhileManageIfNecessaryReturnsIt() {
        var text = new Text();
        var id = transactionServiceCDI.call(() -> textRepository.persist(text));
        transactionServiceCDI.run(() -> textRepository.delete(textRepository.getById(id)));
        text.setId(id);

        assertThrows(EntityNotFoundException.class, () -> transactionServiceCDI.call(() -> textRepository.manage(text)), "manage");
        assertSame(text, transactionServiceCDI.call(() -> textRepository.manageIfNecessary(text)), "manageIfNecessary");
    }

    /**
     * {@link EntityRepository#manageIfNecessary(Object)} of a detached lazy association, which is an uninitialized proxy on Hibernate, returns the managed
     * entity.
     */
    @Test
    void testManageIfNecessaryOfDetachedLazyOwnerReturnsManagedEntity() {
        var phoneId = phoneRepository.getPage(Page.ONE, false).get(0).getId();
        var owner = transactionServiceCDI.call(() -> phoneRepository.getById(phoneId).getOwner());
        var expectedEmail = personRepository.getById(owner.getId()).getEmail();
        var state = transactionServiceCDI.call(() -> {
            var managed = personRepository.manageIfNecessary(owner);
            return List.of(personRepository.getEntityManager().contains(managed), managed.getEmail());
        });
        assertEquals(List.of(true, expectedEmail), state, "Managed, with email");
    }

    @Test
    void testEscapeLikeInCriteriaQueryBuilderOfListFindAndFindFirst() {
        var articles = List.of(persistArticle("a_b", null), persistArticle("axb", null));

        try {
            CriteriaQueryBuilder<Article> builder = (criteriaBuilder, query, root) -> query
                .where(criteriaBuilder.like(root.<String>get("title"), "%" + escapeLike("a_b"), LIKE_ESCAPE));
            var expected = codesOf(articles.subList(0, 1));
            assertEquals(expected, codesOf(articleRepository.list(builder, Map.of())), "list");
            assertEquals(expected, codesOf(articleRepository.find(builder, Map.of()).stream().toList()), "find");
            assertEquals(expected, codesOf(articleRepository.findFirst(builder, Map.of()).stream().toList()), "findFirst");
        }
        finally {
            deleteArticles(articles);
        }
    }

    @Test
    void testFindWithMultipleResultsThrowsNonUniqueResult() {
        assertThrows(NonUniqueResultException.class, () -> personRepository.find("WHERE e.id BETWEEN ?1 AND ?2", 2L, 4L), "JPQL");
        assertThrows(
            NonUniqueResultException.class,
            () -> personRepository.find((criteriaBuilder, query, root) -> query.where(root.get("id").in(2L, 3L)), Map.of()),
            "CriteriaQueryBuilder"
        );
    }

    /**
     * Each criteria on an enum mapped via a converter selects exactly the entities whose enum value {@link Criteria#applies(Object)} accepts.
     */
    @Test
    @DisabledIfSystemProperty(
        named = PROFILE_ID, matches = OPENJPA, disabledReason = "OpenJPA fails with a ClassCastException on persisting an enum field with an AttributeConverter"
    )
    void testCriteriaOnConvertedEnumAgreeWithApplies() {
        var articles = new ArrayList<Article>();

        try {
            for (var gender : Gender.values()) {
                var article = newArticle("converted-" + gender, null);
                article.setAudienceCode(gender);
                transactionServiceCDI.run(() -> articleRepository.persist(article));
                articles.add(article);
            }

            for (var criteria : List.<Criteria<?>>of(
                Like.startsWith("mal"), Like.contains("male"), IgnoreCase.value("male"), Order.greaterThan(Gender.FEMALE), Order.lessThanOrEqualTo(Gender.TRANS)
            )) {
                var expected = articles.stream().filter(article -> criteria.applies(article.getAudienceCode())).map(Article::getCode).sorted().toList();
                var criteriaMap = new HashMap<>(TITLED);
                criteriaMap.put("audienceCode", criteria);
                var page = articleRepository.getPage(Page.with().allMatch(criteriaMap).orderBy("id", true).build(), true);
                assertEquals(expected, codesOf(page), criteria.toString());
            }
        }
        finally {
            deleteArticles(articles);
        }
    }

    /**
     * Criteria on a <code>@ManyToMany</code> behave like on a <code>@OneToMany</code>: multiple values must all match, and a negated value excludes the
     * entities having it.
     */
    @Test
    @DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = OPENJPA_COUNT)
    void testCriteriaOnManyToManyAgreeWithOneToMany() {
        var first = newCategory("m2m-a", "A");
        var second = newCategory("m2m-b", "B");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> List.of(first, second).forEach(categoryRepository::persist));
            articles.add(persistArticleInCategories("both", first, second));
            articles.add(persistArticleInCategories("first", first));
            articles.add(persistArticleInCategories("none"));

            assertEquals(codesOf(articles.subList(0, 1)), codesOfCategorized(Map.of("categories.slug", List.of("m2m-a", "m2m-b"))), "All of");
            assertEquals(codesOf(articles.subList(0, 1)), codesOfCategorized(Map.of("categories.slug", "m2m-b")), "Single value");
            assertEquals(codesOf(articles.subList(2, 3)), codesOfCategorized(Map.of("categories.slug", Not.value("m2m-a"))), "Not");
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(first, second));
        }
    }

    /**
     * The fetched elements of a <code>@ManyToMany</code> are those matching an entity value on their association, also when the entity does not implement
     * <code>equals()</code>.
     */
    @Test
    void testPageWithFetchedManyToManyFilteredByEntityValueWithoutEquals() {
        withCategorizedArticles(articles -> {
            var criteria = new HashMap<>(TITLED);
            criteria.put("categories.parent", categoryRepository.getById("m2m-root"));
            var page = articleRepository.getPage(Page.with().allMatch(criteria).orderBy("id", true).build(), true, "categories");
            assertEquals(codesOf(articles.subList(0, 2)), codesOf(page), "Articles");
            assertEquals(2, page.getEstimatedTotalNumberOfResults(), "Count");
            page.forEach(
                article -> assertEquals(
                    Set.of("m2m-a"), article.getCategories().stream().map(Category::getSlug).collect(toSet()), "Fetched categories of " + article.getTitle()
                )
            );
        });
    }

    /**
     * A criteria on a basic field of an association of the elements of a <code>@ManyToMany</code> returns and counts the entities with a matching element.
     */
    @Test
    void testPageWithCriteriaOnFieldOfAssociationOfCategoriesCounts() {
        withCategorizedArticles(
            articles -> assertEquals(codesOf(articles.subList(0, 2)), codesOfCategorized(Map.of("categories.parent.slug", "m2m-root")), "Articles")
        );
    }

    /**
     * Runs given test with the articles "both", "first" and "second" in the categories A and B, where A has the parent category Root.
     */
    private void withCategorizedArticles(Consumer<List<Article>> test) {
        var root = newCategory("m2m-root", "Root");
        var first = newCategory("m2m-a", "A");
        var second = newCategory("m2m-b", "B");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> {
                categoryRepository.persist(root);
                first.setParent(categoryRepository.getById("m2m-root"));
                List.of(first, second).forEach(categoryRepository::persist);
            });
            articles.add(persistArticleInCategories("both", first, second));
            articles.add(persistArticleInCategories("first", first));
            articles.add(persistArticleInCategories("second", second));
            test.accept(articles);
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(first, second, root));
        }
    }

    /**
     * A <code>null</code> criteria on a collection matches the entities without elements, and a negated one the entities with elements, on a basic element
     * collection and on a <code>@ManyToMany</code>, also as optional criteria. The count is not asserted on OpenJPA, see
     * {@link OmniPersistenceIT#OPENJPA_COUNT}.
     */
    @Test
    void testPageWithNullCriteriaOnCollectionMatchesEntitiesWithoutElements() {
        var category = newCategory("m2m-a", "A");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> categoryRepository.persist(category));
            articles.add(persistArticle("tagged", null, "x"));
            articles.add(persistArticleInCategories("categorized", category));
            articles.add(persistArticle("none", null));
            var tagged = codesOf(articles.subList(0, 1));
            var categorized = codesOf(articles.subList(1, 2));
            var untagged = codesOf(articles.subList(1, 3));
            var uncategorized = codesOf(List.of(articles.get(0), articles.get(2)));
            assertEquals(untagged, codesOfTitledArticles(Collections.singletonMap("tags", null), Map.of()), "Without tags");
            assertEquals(tagged, codesOfTitledArticles(Map.of("tags", Not.value(null)), Map.of()), "With tags");
            assertEquals(uncategorized, codesOfTitledArticles(Collections.singletonMap("categories", null), Map.of()), "Without categories");
            assertEquals(categorized, codesOfTitledArticles(Map.of("categories", Not.value(null)), Map.of()), "With categories");
            var tagsOrCategories = Map.<String, Object>of("tags", Not.value(null), "categories", Not.value(null));
            assertEquals(codesOf(articles.subList(0, 2)), codesOfTitledArticles(Map.of(), tagsOrCategories), "With tags or categories");
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(category));
        }
    }

    /**
     * An ordering on a field of a <code>@ManyToMany</code> returns each entity once, and places the entities without elements where the database sorts
     * <code>NULL</code>, see {@link Database#sortsNullsLow()}.
     */
    @Test
    void testGetPageOrderedByManyToManyPlacesArticlesWithoutCategoriesAsNulls() {
        var first = newCategory("m2m-a", "A");
        var second = newCategory("m2m-b", "B");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> List.of(first, second).forEach(categoryRepository::persist));
            articles.add(persistArticleInCategories("b", second));
            articles.add(persistArticleInCategories("a", first));
            articles.add(persistArticleInCategories("none"));
            var categorized = List.of(articles.get(1).getCode(), articles.get(0).getCode());
            var uncategorized = List.of(articles.get(2).getCode());
            var nullsLow = articleRepository.getDatabase().sortsNullsLow();
            var ascending = new ArrayList<>(nullsLow ? uncategorized : categorized);
            ascending.addAll(nullsLow ? categorized : uncategorized);
            var descending = new ArrayList<>(ascending);
            Collections.reverse(descending);

            for (var order : Map.of(true, ascending, false, descending).entrySet()) {
                var page = articleRepository.getPage(Page.with().allMatch(TITLED).orderBy("categories.name", order.getKey()).build(), true);
                assertEquals(order.getValue(), codesOf(page), "Ascending " + order.getKey());
                assertEquals(3, page.getEstimatedTotalNumberOfResults(), "Count ascending " + order.getKey());
            }
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(first, second));
        }
    }

    private Article persistArticleInCategories(String title, Category... categories) {
        var article = newArticle(title, null);
        transactionServiceCDI.run(() -> {
            Arrays.stream(categories).map(category -> categoryRepository.getById(category.getSlug())).forEach(article.getCategories()::add);
            articleRepository.persist(article);
        });
        return article;
    }

    /**
     * Returns the codes of the titled articles matching all of given required criteria and any of given optional criteria, ordered by ID, and asserts their
     * count, except on OpenJPA.
     */
    private List<Long> codesOfTitledArticles(Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
        var page = pageOfTitledArticles(requiredCriteria, optionalCriteria);
        assertCountUnlessOpenJPA(page, "Count of " + requiredCriteria + " and any of " + optionalCriteria);
        return codesOf(page);
    }

    private PartialResultList<Article> pageOfTitledArticles(Map<String, Object> requiredCriteria, Map<String, Object> optionalCriteria) {
        var criteria = new HashMap<>(TITLED);
        criteria.putAll(requiredCriteria);
        return articleRepository.getPage(Page.with().allMatch(criteria).anyMatch(optionalCriteria).orderBy("id", true).build(), true);
    }

    /**
     * Asserts that the count of given page equals its size, except on OpenJPA, see {@link OmniPersistenceIT#OPENJPA_COUNT}.
     */
    private void assertCountUnlessOpenJPA(PartialResultList<?> page, String message) {
        if (personRepository.getProvider() != Provider.OPENJPA) {
            assertEquals(page.size(), page.getEstimatedTotalNumberOfResults(), message);
        }
    }

    private List<Long> codesOfCategorized(Map<String, Object> categoryCriteria) {
        var page = pageOfTitledArticles(categoryCriteria, Map.of());
        assertEquals(page.size(), page.getEstimatedTotalNumberOfResults(), "Count of " + categoryCriteria);
        return codesOf(page);
    }

    @Test
    void testGetPageWithFetchAndCriteriaOnGrandparentResolvesNestedPath() {
        var root = newCategory("tree-root", "Root");
        var child = newCategory("tree-child", "Child");
        var grandchild = newCategory("tree-grandchild", "Grandchild");

        try {
            transactionServiceCDI.run(() -> {
                categoryRepository.persist(root);
                child.setParent(categoryRepository.getById("tree-root"));
                categoryRepository.persist(child);
                grandchild.setParent(categoryRepository.getById("tree-child"));
                categoryRepository.persist(grandchild);
            });

            var criteria = Page.with().allMatch(Map.of("parent.parent.name", "Root")).build();
            assertEquals(List.of("tree-grandchild"), slugsOf(categoryRepository.getPage(criteria, true)), "Without fetch");
            assertEquals(List.of("tree-grandchild"), slugsOf(categoryRepository.getPage(criteria, true, "parent")), "With fetch of parent");
        }
        finally {
            deleteCategories(List.of(grandchild, child, root));
        }
    }

    private static List<String> slugsOf(List<Category> categories) {
        return categories.stream().map(Category::getSlug).toList();
    }

    /**
     * A page of the root of an entity hierarchy, ordered by an element collection, returns the subclass instances in order across offset pages, also when
     * restricted to one subclass.
     */
    @Test
    void testPageOfEntityHierarchyOrderedByElementCollection() {
        var documents = List.<Document>of(
            newDocument(new Invoice(), "b", 10), newDocument(new Letter(), "a", "Ann"), newDocument(new Invoice(), "c", 20),
            newDocument(new Letter(), "d", "Dave")
        );

        try {
            documents.forEach(document -> transactionServiceCDI.run(() -> documentRepository.persist(document)));
            var expected = List.of(documents.get(1).getId(), documents.get(0).getId(), documents.get(2).getId(), documents.get(3).getId());
            var titled = Map.<String, Object>of("title", Like.startsWith(TITLE_PREFIX));
            var firstPage = documentRepository.getPage(Page.with().range(0, 2).allMatch(titled).orderBy("labels", true).build(), true);
            var secondPage = documentRepository.getPage(Page.with().range(2, 2).allMatch(titled).orderBy("labels", true).build(), true);
            assertEquals(expected.subList(0, 2), firstPage.stream().map(Document::getId).toList(), "First page");
            assertEquals(expected.subList(2, 4), secondPage.stream().map(Document::getId).toList(), "Second page");
            assertEquals(4, firstPage.getEstimatedTotalNumberOfResults(), "Count");
            assertTrue(firstPage.get(0) instanceof Letter && firstPage.get(1) instanceof Invoice, "Subclass instances");
            var details = new ArrayList<>(firstPage.stream().map(EntityRepositoryIT::detailOf).toList());
            details.addAll(secondPage.stream().map(EntityRepositoryIT::detailOf).toList());
            assertEquals(List.of("Ann", 10, 20, "Dave"), details, "Subclass fields");

            var invoices = documentRepository.getPage(
                Page.with().allMatch(titled).orderBy("labels", true).build(), true,
                (criteriaBuilder, query, root) -> query.where(criteriaBuilder.equal(root.type(), Invoice.class))
            );
            assertEquals(List.of(documents.get(0).getId(), documents.get(2).getId()), invoices.stream().map(Document::getId).toList(), "Criteria on subclass");
            assertEquals(List.of(10, 20), invoices.stream().map(EntityRepositoryIT::detailOf).toList(), "Subclass fields of criteria on subclass");
        }
        finally {
            transactionServiceCDI.run(
                () -> documents.stream().filter(document -> document.getId() != null).map(document -> documentRepository.getById(document.getId()))
                    .filter(Objects::nonNull).forEach(documentRepository::delete)
            );
        }
    }

    /**
     * A page of the root of a joined entity hierarchy with a criteria on an element collection returns and counts the matching subclass instances, also when
     * restricted to one subclass. The count of the latter is not asserted on OpenJPA.
     */
    @Test
    void testPageOfEntityHierarchyWithCriteriaOnElementCollectionCounts() {
        var documents = List.<Document>of(
            newDocument(new Invoice(), "b", 10), newDocument(new Letter(), "a", "Ann"), newDocument(new Invoice(), "c", 20),
            newDocument(new Letter(), "d", "Dave")
        );
        documents.subList(0, 3).forEach(document -> document.getLabels().add("billing"));

        try {
            documents.forEach(document -> transactionServiceCDI.run(() -> documentRepository.persist(document)));
            var criteria = new HashMap<>(TITLED);
            criteria.put("labels", "billing");
            var billed = documentRepository.getPage(Page.with().allMatch(criteria).orderBy("id", true).build(), true);
            assertEquals(documents.subList(0, 3).stream().map(Document::getId).toList(), billed.stream().map(Document::getId).toList(), "Documents");
            assertEquals(3, billed.getEstimatedTotalNumberOfResults(), "Count");

            var invoices = documentRepository.getPage(
                Page.with().allMatch(criteria).orderBy("id", true).build(), true,
                (criteriaBuilder, query, root) -> query.where(criteriaBuilder.equal(root.type(), Invoice.class))
            );
            assertEquals(List.of(documents.get(0).getId(), documents.get(2).getId()), invoices.stream().map(Document::getId).toList(), "Invoices");

            if (documentRepository.getProvider() != Provider.OPENJPA) { // OpenJPA renders TYPE() in the count subquery as 1 = 1, so the count ignores it.
                assertEquals(2, invoices.getEstimatedTotalNumberOfResults(), "Count of invoices");
            }
        }
        finally {
            transactionServiceCDI.run(
                () -> documents.stream().filter(document -> document.getId() != null).map(document -> documentRepository.getById(document.getId()))
                    .filter(Objects::nonNull).forEach(documentRepository::delete)
            );
        }
    }

    private static Document newDocument(Document document, String label, Object detail) {
        document.setTitle(TITLE_PREFIX + label);
        document.getLabels().add(label);

        if (document instanceof Invoice invoice) {
            invoice.setAmount((Integer) detail);
        }
        else if (document instanceof Letter letter) {
            letter.setAuthor((String) detail);
        }

        return document;
    }

    private static Object detailOf(Document document) {
        return document instanceof Invoice invoice ? invoice.getAmount() : ((Letter) document).getAuthor();
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
            var firstCategory = descendingCodes.subList(1, 3);
            assertEquals(firstCategory, codesOfArticles(Map.of(), "category.name", Like.startsWith("Fir")), "Criteria on association");
            assertEquals(List.of(codes.get(2)), codesOfArticles(Map.of(), "category", second), "Criteria on entity");

            assertEquals(firstCategory, codesOfArticles(Map.of(), "tags", "x"), "Criteria on element collection");
            var ignoreCase = articleRepository.getPage(Page.with().allMatch(Map.of("tags", "X")).build(), true);
            assertEquals(firstCategory, codesOf(ignoreCase), "Criteria on element collection ignore case");
            assertEquals(2, ignoreCase.getEstimatedTotalNumberOfResults(), "Count of criteria on element collection ignore case");
            assertEquals(
                List.of(codes.get(0)), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("tags", List.of("x", "y"))).build(), true)),
                "Criteria on all of element collection"
            );
            assertEquals(firstCategory, codesOfArticles(Map.of(), "tags", Like.contains("X")), "Like on element collection");
            assertEquals(descendingCodes, codesOfArticles(TITLED, "deleted", Like.contains("fals")), "Like on boolean matching false");
            assertEquals(List.of(), codesOfArticles(TITLED, "deleted", Like.contains("tru")), "Like on boolean matching true");
            assertEquals(descendingCodes, codesOfArticles(TITLED, "deleted", Like.contains("e")), "Like on boolean matching true and false");
            assertEquals(List.of(), codesOfArticles(TITLED, "deleted", Like.contains("foo")), "Like on boolean matching neither true nor false");
            assertEquals(List.of(codes.get(0)), codesOfArticles(TITLED, "flags", Like.contains("tru")), "Like on boolean element collection");
            assertEquals(List.of(), codesOfArticles(TITLED, "flags", Like.contains("foo")), "Like on boolean element collection matching neither");
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
            assertEquals(firstCategory, codesOfArticles(Map.of(), "ratings", "05"), "Numeric element collection");
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

            transactionServiceCDI.run(() -> articleRepository.softDelete(articleRepository.getById(articles.get(2).getCode())));
            var deleted = List.of(articles.get(2).getCode());
            var notDeleted = descendingCodes.stream().filter(code -> !deleted.contains(code)).toList();
            assertEquals(deleted, codesOfArticles(TITLED, "deleted", Bool.value(true)), "Bool true on soft deleted field");
            assertEquals(notDeleted, codesOfArticles(TITLED, "deleted", Bool.value(false)), "Bool false on soft deleted field");
            assertEquals(deleted, codesOfArticles(TITLED, "deleted", Like.contains("tru")), "Like on boolean matching true");
            assertEquals(notDeleted, codesOfArticles(TITLED, "deleted", Like.contains("fals")), "Like on boolean matching false");
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

    private List<Long> codesOfArticles(Map<String, Object> criteria, String field, Object value) {
        var allCriteria = new HashMap<>(criteria);
        allCriteria.put(field, value);
        return codesOf(articleRepository.getPage(Page.with().allMatch(allCriteria).build(), true));
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
