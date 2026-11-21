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
package org.omnifaces.persistence.test.service;

import static jakarta.persistence.criteria.JoinType.LEFT;
import static java.util.stream.Collectors.toMap;
import static org.omnifaces.persistence.JPA.LIKE_ESCAPE;
import static org.omnifaces.persistence.JPA.castAsString;
import static org.omnifaces.persistence.JPA.concat;
import static org.omnifaces.persistence.JPA.escapeLike;
import static org.omnifaces.persistence.Provider.ECLIPSELINK;
import static org.omnifaces.persistence.Provider.HIBERNATE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.query.Page;
import org.omnifaces.persistence.query.Select;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Address;
import org.omnifaces.persistence.test.model.Group;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.dto.PersonCard;
import org.omnifaces.persistence.test.model.dto.PersonPhoneStats;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;

public abstract class PersonService extends BaseEntityService<Long, Person> {

    public PartialResultList<Person> getPageWithAddress(Page page, boolean count) {
        return getPage(page, count, "address");
    }

    public PartialResultList<Person> getPageWithPhones(Page page, boolean count) {
        return getPage(page, count, "phones");
    }

    public PartialResultList<Person> getPageWithPhonesWithoutTransaction(Page page, boolean count) {
        if (getEntityManager().isJoinedToTransaction()) {
            throw new IllegalStateException("Transaction is active");
        }

        return getPage(page, count, "phones");
    }

    public PartialResultList<Person> getPageWithPhonesViaFetchAttribute(Page page, boolean count) {
        return getPage(page, count, (builder, query, person) -> person.fetch(getMetamodel().getList("phones", Phone.class), LEFT));
    }

    public PartialResultList<Person> getPageWithAddressAndPhoneOwnersViaFetchAttributes(Page page, boolean count) {
        return getPage(page, count, (builder, query, person) -> {
            person.fetch(getMetamodel().getSingularAttribute("address", Address.class));
            person.fetch(getMetamodel().getList("phones", Phone.class), LEFT)
                .fetch(getEntityManager().getMetamodel().entity(Phone.class).getSingularAttribute("owner", Person.class));
        });
    }

    public boolean isManagedAfterPageWithPhones(Long id, Page page) {
        var person = getById(id);
        getPageWithPhones(page, false);
        return getEntityManager().contains(person);
    }

    public PartialResultList<Person> getPageWithPhoneOwners(Page page, boolean count) {
        return getPage(page, count, "phones.owner");
    }

    public PartialResultList<Person> getPageWithGroups(Page page, boolean count) {
        return getPage(page, count, "groups");
    }

    public PartialResultList<Person> getPageViaQueryBuilder(Page page, boolean count) {
        return getPage(page, count, (builder, query, person) -> {
        });
    }

    public PartialResultList<Person> getPageViaQueryBuilderInvokingService(Page page) {
        return getPage(page, false, (builder, query, person) -> {
            getById(1L);
        });
    }

    /**
     * Restricts the page to the persons whose ID contains the given term, via a <code>LIKE</code> predicate built in the query builder. When the given phone
     * service is not <code>null</code>, it is invoked first.
     */
    public PartialResultList<Person> getPageViaQueryBuilderBuildingLike(Page page, PhoneService phoneService, String term) {
        return getPage(page, false, (builder, query, person) -> {
            if (phoneService != null) {
                phoneService.getById(1L);
            }

            query.where(builder.like(castAsString(builder, person.get("id")), "%" + escapeLike(term) + "%", LIKE_ESCAPE));
        });
    }

    public Person getByEmailViaNamedTypedQuery(String email) {
        return createNamedTypedQuery(Person.BY_EMAIL).setParameter("email", email).getSingleResult();
    }

    public Person getByEmailViaNamedQuery(String email) {
        return (Person) createNamedQuery(Person.BY_EMAIL).setParameter("email", email).getSingleResult();
    }

    public Person getByEmailViaTypedQuery(String email) {
        return createTypedQuery("SELECT e FROM Person e WHERE e.email = ?1").setParameter(1, email).getSingleResult();
    }

    public Long getIdByEmailViaTypedQuery(String email) {
        return createTypedQuery("SELECT e.id FROM Person e WHERE e.email = ?1", Long.class).setParameter(1, email).getSingleResult();
    }

    public PartialResultList<PersonCard> getPageOfPersonCards(Page page, boolean count) {
        return getPage(page, count, PersonCard.class, (builder, query, person) -> {
            Join<Person, Address> personAddress = person.join("address");
            Join<Person, Phone> personPhones = person.join("phones");

            LinkedHashMap<Getter<PersonCard>, Expression<?>> mapping = new LinkedHashMap<>();
            mapping.put(PersonCard::getId, person.get("id"));
            mapping.put(PersonCard::getEmail, person.get("email"));
            mapping.put(
                PersonCard::getAddressString,
                concat(
                    builder, personAddress.get("street"), " ", personAddress.get("houseNumber"), ", ", personAddress.get("postcode"), " ",
                    personAddress.get("city"), ", ", personAddress.get("country")
                )
            );
            mapping.put(PersonCard::getTotalPhones, builder.count(personPhones));

            query.groupBy(
                person.get("id"), person.get("email"), personAddress.get("street"), personAddress.get("houseNumber"), personAddress.get("postcode"),
                personAddress.get("city"), personAddress.get("country")
            );
            return mapping;
        });
    }

    /**
     * The same person cards as {@link #getPageOfPersonCards(Page, boolean)}, in the query shape of the OptimusFaces integration tests. It groups by the joined
     * address entity. It groups only outside the count subquery. It maps the address string to the <code>@Formula</code> on Hibernate.
     */
    public PartialResultList<PersonCard> getPageOfPersonCardsAsOptimusFaces(Page page, boolean count) {
        return getPage(page, count, PersonCard.class, (builder, query, person) -> {
            Join<Person, Address> personAddress = person.join("address");
            Join<Person, Phone> personPhones = person.join("phones");

            LinkedHashMap<Getter<PersonCard>, Expression<?>> mapping = new LinkedHashMap<>();
            mapping.put(PersonCard::getId, person.get("id"));
            mapping.put(PersonCard::getEmail, person.get("email"));

            if (getProvider() == HIBERNATE) {
                mapping.put(PersonCard::getAddressString, personAddress.get("string"));
            }
            else {
                mapping.put(
                    PersonCard::getAddressString,
                    concat(
                        builder, personAddress.get("street"), " ", personAddress.get("houseNumber"), ", ", personAddress.get("postcode"), " ",
                        personAddress.get("city"), ", ", personAddress.get("country")
                    )
                );
            }

            if (!(query instanceof Subquery)) {
                query.groupBy(person.get("id"), person.get("email"), personAddress);
            }

            mapping.put(PersonCard::getTotalPhones, builder.count(personPhones));
            return mapping;
        });
    }

    /**
     * The person cards with as address string the concatenation of the ID, a String and an Integer, of which only the String is a String.
     */
    public PartialResultList<PersonCard> getPageOfPersonCardsWithNonStringConcatParts(Page page, boolean count) {
        return getPage(page, count, PersonCard.class, (builder, query, person) -> {
            LinkedHashMap<Getter<PersonCard>, Expression<?>> mapping = new LinkedHashMap<>();
            mapping.put(PersonCard::getId, person.get("id"));
            mapping.put(PersonCard::getEmail, person.get("email"));
            mapping.put(PersonCard::getAddressString, concat(builder, person.get("id"), "-", 1));
            mapping.put(PersonCard::getTotalPhones, builder.count(person.join("phones")));

            query.groupBy(person.get("id"), person.get("email"));
            return mapping;
        });
    }

    /**
     * The persons having all given groups, via a plain <code>IN</code> on the joined groups, grouped by the person, with a <code>HAVING COUNT(DISTINCT)</code>
     * on the number of groups.
     */
    public List<Person> listHavingAllGroupsViaPlainIn(Set<Group> groups, boolean fetchPhones) {
        var builder = getEntityManager().getCriteriaBuilder();
        var query = builder.createQuery(Person.class);
        var person = query.from(Person.class);

        if (fetchPhones) {
            person.fetch("phones", LEFT);
        }

        Join<Person, Group> personGroups = person.join("groups");
        query.select(person).where(personGroups.in(groups)).groupBy(person).having(builder.equal(builder.countDistinct(personGroups), (long) groups.size()));
        return getEntityManager().createQuery(query).getResultList();
    }

    public PartialResultList<Person> getAllWithAddress() {
        return getPageWithAddress(Page.ALL, false);
    }

    public PartialResultList<Person> getAllWithPhones() {
        return getPageWithPhones(Page.ALL, false);
    }

    public PartialResultList<Person> getAllWithGroups() {
        return getPageWithGroups(Page.ALL, false);
    }

    public PartialResultList<PersonPhoneStats> getPageOfPersonPhoneStats(Page page, boolean count) {
        return getPage(page, count, PersonPhoneStats.class, (builder, query, person) -> {
            Join<Person, Phone> personPhones = person.join("phones");
            Expression<Long> phoneId = personPhones.get("id");

            LinkedHashMap<Getter<PersonPhoneStats>, Expression<?>> mapping = new LinkedHashMap<>();
            mapping.put(PersonPhoneStats::getId, person.get("id"));
            mapping.put(PersonPhoneStats::getMaxPhoneId, builder.max(phoneId));
            mapping.put(PersonPhoneStats::getSumPhoneId, builder.sum(phoneId));
            mapping.put(PersonPhoneStats::getTotalPhoneTypes, builder.countDistinct(personPhones.get("type")));
            mapping.put(PersonPhoneStats::getSumPhoneIdOrZero, builder.coalesce(builder.sum(phoneId), 0L));

            query.groupBy(person.get("id"));
            return mapping;
        });
    }

    public PartialResultList<PersonCard> getAllPersonCards() {
        return getPageOfPersonCards(Page.ALL, false);
    }

    public List<Person> listByIdRange(Long minId, Long maxId) {
        return list("WHERE e.id BETWEEN ?1 AND ?2 ORDER BY e.id", minId, maxId);
    }

    public Optional<Person> findByIdAndEmail(Long id, String email) {
        return find("WHERE e.id = ?1 AND e.email = ?2", id, email);
    }

    public Optional<Person> findFirstByIdRange(Long minId, Long maxId) {
        return findFirst("WHERE e.id BETWEEN ?1 AND ?2 ORDER BY e.id", minId, maxId);
    }

    public List<Person> listByIdRangeViaNamedParameters(Long minId, Long maxId) {
        return list("WHERE e.id BETWEEN :minId AND :maxId ORDER BY e.id", Map.of("minId", minId, "maxId", maxId));
    }

    public Optional<Person> findByIdAndEmailViaNamedParameters(Long id, String email) {
        return find("WHERE e.id = :id AND e.email = :email", Map.of("id", id, "email", email));
    }

    public Optional<Person> findFirstByIdRangeViaNamedParameters(Long minId, Long maxId) {
        return findFirst("WHERE e.id BETWEEN :minId AND :maxId ORDER BY e.id", Map.of("minId", minId, "maxId", maxId));
    }

    public int updateEmailByIdRange(String email, Long minId, Long maxId) {
        return update("SET e.email = ?1 WHERE e.id BETWEEN ?2 AND ?3", email, minId, maxId);
    }

    public int updateEmailByMappedIdRange(String email, Long minId, Long maxId) {
        return update("SET e.email = :email WHERE e.id BETWEEN :minId AND :maxId", Map.of("email", email, "minId", minId, "maxId", maxId));
    }

    public PartialResultList<Person> getPageWithoutCache(Page page) {
        return getPage(page, false, false);
    }

    @Override
    public Optional<Person> find(Select select) {
        return super.find(select);
    }

    @Override
    public Optional<Person> findFirst(Select select) {
        return super.findFirst(select);
    }

    @Override
    public List<Person> list(Select select) {
        return super.list(select);
    }

    /**
     * Returns per expression on the person ID whether {@link Provider#isAggregation(Expression)} recognizes it as an aggregation.
     */
    public Map<String, Boolean> getAggregationsOfIdExpressions() {
        var builder = getEntityManager().getCriteriaBuilder();
        Expression<Long> id = builder.createQuery(Long.class).from(Person.class).get("id");
        var expressions = Map.<String, Expression<?>>of(
            "id", id, "sum(id)", builder.sum(id), "coalesce(sum(id), 0)", builder.coalesce(builder.sum(id), 0L), "id + 1", builder.sum(id, 1L), "id + id",
            builder.sum(id, id), "coalesce(id + 1, 0)", builder.coalesce(builder.sum(id, 1L), 0L)
        );
        return expressions.entrySet().stream().collect(toMap(Entry::getKey, entry -> getProvider().isAggregation(entry.getValue())));
    }

    /**
     * Returns whether the person with given ID is in the second level cache.
     */
    public boolean isInSecondLevelCache(Long id) {
        return getEntityManager().getEntityManagerFactory().getCache().contains(Person.class, id);
    }

    /**
     * Updates the nickname of the person with given ID via native SQL, which the second level cache of the persistence provider does not see.
     */
    public int updateNicknameViaNativeSql(Long id, String nickname) {
        // EclipseLink creates default table and column names in uppercase, which SQL Server with a case-sensitive collation distinguishes.
        var sql = "UPDATE Person SET nickname = ?1 WHERE id = ?2";
        return getEntityManager().createNativeQuery(getProvider() == ECLIPSELINK ? sql.toUpperCase(Locale.ROOT) : sql).setParameter(1, nickname)
            .setParameter(2, id)
            .executeUpdate();
    }

}
