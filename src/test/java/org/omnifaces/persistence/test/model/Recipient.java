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
 * An embedded recipient of a {@link Letter}, with an association to a {@link Person}.
 */
@Embeddable
public class Recipient {

    @ManyToOne
    private Person person;

    public Recipient() {
        // Required by Jakarta Persistence.
    }

    public Recipient(Person person) {
        this.person = person;
    }

    public Person getPerson() {
        return person;
    }

}
