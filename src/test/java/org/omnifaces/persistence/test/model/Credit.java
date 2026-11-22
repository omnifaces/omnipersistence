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
package org.omnifaces.persistence.test.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.ManyToOne;

/**
 * A credit of an {@link Album}, as embeddable element of a collection, with a nullable field and a nullable association.
 */
@Embeddable
public class Credit {

    private String name;

    private String role;

    @ManyToOne
    private Person person;

    public Credit() {
        // Required by Jakarta Persistence.
    }

    public Credit(String name, String role, Person person) {
        this.name = name;
        this.role = role;
        this.person = person;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public Person getPerson() {
        return person;
    }

}
