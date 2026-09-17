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

import static org.omnifaces.persistence.JPA.concat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;

import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Address;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.dto.PersonCard;
import org.omnifaces.utils.collection.PartialResultList;
import org.omnifaces.utils.reflect.Getter;

public abstract class PersonService extends BaseEntityService<Long, Person> {

    public PartialResultList<Person> getPageWithAddress(Page page, boolean count) {
        return getPage(page, count, "address");
    }

    public PartialResultList<Person> getPageWithPhones(Page page, boolean count) {
        return getPage(page, count, "phones");
    }

    public PartialResultList<Person> getPageWithGroups(Page page, boolean count) {
        return getPage(page, count, "groups");
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

            query.groupBy(personAddress);
            return mapping;
        });
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

    public int updateEmailByIdRange(String email, Long minId, Long maxId) {
        return update("SET e.email = ?1 WHERE e.id BETWEEN ?2 AND ?3", email, minId, maxId);
    }

    public int updateEmailByMappedIdRange(String email, Long minId, Long maxId) {
        return update("SET e.email = :email WHERE e.id BETWEEN :minId AND :maxId", Map.of("email", email, "minId", minId, "maxId", maxId));
    }

}
