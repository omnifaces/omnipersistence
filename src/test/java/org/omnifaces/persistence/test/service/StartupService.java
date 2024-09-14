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
package org.omnifaces.persistence.test.service;

import static java.lang.Math.abs;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

import org.omnifaces.persistence.test.model.Address;
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Gender;
import org.omnifaces.persistence.test.model.Group;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.model.Phone;
import org.omnifaces.persistence.test.model.Text;

@Startup
@Singleton
public class StartupService {

    public static final int TOTAL_RECORDS = 200;
    public static final int ROWS_PER_PAGE = 10;
    public static final int TOTAL_PHONES_PER_PERSON_0 = 3;

    @Inject
    private TextService textService;

    @Inject
    private CommentService commentService;

    @Inject
    private PersonService personService;

    @PostConstruct
    public void init() {
        createTestPersons();
        createTestTexts();
        createTestComments();
    }

    private void createTestPersons() {
        var genders = Gender.values();
        var phoneTypes = Phone.Type.values();
        var groups = Arrays.asList(Group.values());
        var random = ThreadLocalRandom.current();

        for (var i = 0; i < TOTAL_RECORDS; i++) {
            var person = new Person();
            person.setEmail("name" + i + "@example.com");
            person.setGender(genders[random.nextInt(genders.length)]);
            person.setDateOfBirth(LocalDate.ofEpochDay(random.nextLong(LocalDate.of(1900, 1, 1).toEpochDay(), LocalDate.of(2000, 1, 1).toEpochDay())));

            var address = new Address();
            address.setStreet("Street" + i);
            address.setHouseNumber("" + i);
            address.setPostcode("Postcode" + i);
            address.setCity("City" + i);
            address.setCountry("Country" + i);
            person.setAddress(address);

            var totalPhones = i == 0 ? TOTAL_PHONES_PER_PERSON_0 : random.nextInt(1, 6);
            for (var j = 0; j < totalPhones; j++) {
                var phone = new Phone();
                phone.setType(phoneTypes[random.nextInt(phoneTypes.length)]);
                phone.setNumber("0" + abs(random.nextInt()));
                phone.setOwner(person);
                person.getPhones().add(phone);
            }

            Collections.shuffle(groups, random);
            person.getGroups().addAll(groups.subList(0, random.nextInt(1, groups.size() + 1)));

            personService.persist(person);
        }
    }

    private void createTestTexts() {
        textService.persist(new Text());
        textService.persist(new Text());
    }

    private void createTestComments() {
        commentService.persist(new Comment());
        commentService.persist(new Comment());
    }

}
