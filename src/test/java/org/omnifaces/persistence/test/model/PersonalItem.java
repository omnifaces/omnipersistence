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

import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * A mapped superclass with an association to a {@link Person}, for entities without a common entity supertype.
 */
@MappedSuperclass
public abstract class PersonalItem extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private Person holder;

    public Person getHolder() {
        return holder;
    }

    public void setHolder(Person holder) {
        this.holder = holder;
    }

}
