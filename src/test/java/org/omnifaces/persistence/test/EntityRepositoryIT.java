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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.test.Serializations.serializeAndDeserialize;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.TOTAL_PHONES_PER_PERSON_0;
import static org.omnifaces.persistence.test.service.StartupServiceCDI.TOTAL_RECORDS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.persistence.TransactionRequiredException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.criteria.Like;
import org.omnifaces.persistence.exception.IllegalEntityStateException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.persistence.test.model.Article;
import org.omnifaces.persistence.test.model.Category;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.EntityEvents;
import org.omnifaces.persistence.test.service.StartupServiceEJB;
import org.omnifaces.persistence.test.service.TestAuditListener;
import org.omnifaces.persistence.test.service.TransactionService;
import org.omnifaces.persistence.test.service.TransactionServiceCDI;
import org.omnifaces.persistence.test.service.TransactionServiceEJB;

/**
 * Integration tests for an injected {@link EntityRepository}. Reading actions run without a transaction, modifying actions run within a transaction of
 * {@link TransactionService}. {@link Article} and {@link Category} do not extend from {@link BaseEntity} and their ID is not named <code>id</code>.
 */
@ExtendWith(ArquillianExtension.class)
public class EntityRepositoryIT {

    private static final String TITLE_PREFIX = "plain-";

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
    void testGetByIdWithLoadGraph() {
        if (OmniPersistenceIT.isOpenJPA()) {
            return; // OpenJPA does not support EntityManager#getEntityGraph().
        }

        var person = personRepository.getByIdWithLoadGraph(1L, Person.WITH_PHONES);
        assertTrue(personRepository.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(person, "phones"), "Phones are loaded");
        assertEquals(TOTAL_PHONES_PER_PERSON_0, person.getPhones().size(), "Phones loaded by entity graph");
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
    void testDeleteOfLazyAssociationUnsetsItsId() {
        var category = newCategory("lazy", "Lazy");
        transactionServiceCDI.run(() -> categoryRepository.persist(category));
        var article = persistArticle("lazy", category);
        var lazyCategory = transactionServiceCDI.call(() -> {
            var reference = articleRepository.getById(article.getCode()).getCategory();
            articleRepository.delete(article);
            categoryRepository.delete(reference);
            return reference;
        });
        assertNull(categoryRepository.getById("lazy"), "Deleted category");

        if (!OmniPersistenceIT.isEclipseLink() && !OmniPersistenceIT.isOpenJPA()) {
            assertNull(lazyCategory.getSlug(), "Slug is unset after delete");
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
    void testEntityWithoutBaseEntityQueries() {
        var first = newCategory("first", "First");
        var second = newCategory("second", "Second");
        var articles = new ArrayList<Article>();

        try {
            transactionServiceCDI.run(() -> List.of(first, second).forEach(categoryRepository::persist));
            articles.add(persistArticle("a", first, "x", "y"));
            articles.add(persistArticle("b", first, "x"));
            articles.add(persistArticle("c", second));
            var codes = articles.stream().map(Article::getCode).sorted().toList();
            var descendingCodes = new ArrayList<>(codes);
            Collections.reverse(descendingCodes);
            var titled = Map.<String, Object>of("title", Like.startsWith(TITLE_PREFIX));

            assertEquals(descendingCodes, codesOf(articleRepository.getByIds(codes)), "getByIds orders by code descending");
            assertEquals(descendingCodes, codesOf(articleRepository.list()).stream().filter(codes::contains).toList(), "list orders by code descending");
            assertEquals(descendingCodes, codesOf(articleRepository.getPage(Page.with().allMatch(titled).build(), true)), "Default ordering by id");
            assertEquals(codes, codesOf(articleRepository.getPage(Page.with().allMatch(titled).orderBy("id", true).build(), true)), "Ordering by id");
            assertEquals(
                List.of(codes.get(0)), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("id", codes.get(0))).build(), true)), "Criteria on id"
            );
            assertEquals(
                2, articleRepository.getPage(Page.with().allMatch(Map.of("category.name", Like.startsWith("Fir"))).build(), true).size(),
                "Criteria on association"
            );
            assertEquals(1, articleRepository.getPage(Page.with().allMatch(Map.of("category", second)).build(), true).size(), "Criteria on entity");

            if (!OmniPersistenceIT.isOpenJPA()) { // OpenJPA cannot apply LOWER() to a String @ElementCollection in a subquery.
                assertEquals(2, articleRepository.getPage(Page.with().allMatch(Map.of("tags", "x")).build(), true).size(), "Criteria on element collection");
                assertEquals(
                    List.of(codes.get(0)), codesOf(articleRepository.getPage(Page.with().allMatch(Map.of("tags", List.of("x", "y"))).build(), true)),
                    "Criteria on all of element collection"
                );
            }

            var withQueryBuilder = articleRepository.getPage(Page.with().allMatch(titled).build(), true, (criteriaBuilder, query, root) -> {
            });
            assertEquals(3, withQueryBuilder.getEstimatedTotalNumberOfResults(), "Count via count subquery");

            var withCategories = articleRepository.getPage(Page.with().range(0, 10).allMatch(titled).build(), true, "category");
            assertEquals(3, withCategories.getEstimatedTotalNumberOfResults(), "Count with fetched category");
            assertTrue(withCategories.stream().allMatch(article -> article.getCategory().getName() != null), "Categories are fetched");

            var firstPage = articleRepository.getPage(Page.with().range(0, 2).allMatch(titled).orderBy("code", true).build(), false);
            assertEquals(codes.subList(0, 2), codesOf(firstPage), "First page by cursor");
            var nextPage = articleRepository.getPage(Page.with().range(firstPage.get(1), 2, false).allMatch(titled).orderBy("code", true).build(), false);
            assertEquals(codes.subList(2, 3), codesOf(nextPage), "Next page by cursor");
            var nextPageById = articleRepository.getPage(Page.with().range(0, 2, Map.of("id", codes.get(2)), false).allMatch(titled).build(), false);
            assertEquals(descendingCodes.subList(1, 3), codesOf(nextPageById), "Next page by cursor values on id");
        }
        finally {
            deleteArticles(articles);
            deleteCategories(List.of(first, second));
        }
    }

    private static List<String> auditedArticleChanges() {
        return TestAuditListener.getChanges().stream()
            .map(change -> {
                var article = (Article) change.getEntity();
                return article.getCode() + "." + change.getPropertyName() + ": " + change.getOldValue() + " -> " + change.getNewValue();
            })
            .toList();
    }

    private static Article newArticle(String title, Category category) {
        var article = new Article();
        article.setTitle(TITLE_PREFIX + title);
        article.setCategory(category);
        return article;
    }

    private Article persistArticle(String title, Category category, String... tags) {
        var article = newArticle(title, null);
        article.getTags().addAll(List.of(tags));
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

    private static List<Long> codesOf(List<Article> articles) {
        return articles.stream().map(Article::getCode).toList();
    }

}
