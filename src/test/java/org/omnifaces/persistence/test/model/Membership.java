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

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

/**
 * An entity with an <code>@EmbeddedId</code>, an <code>@Embedded</code> value and an element collection.
 */
@Entity
public class Membership {

    @EmbeddedId
    private MembershipId id;

    @Embedded
    private Period period;

    @ElementCollection
    private Set<String> roles = new HashSet<>();

    public Membership() {
        // Required by Jakarta Persistence.
    }

    public Membership(MembershipId id, Period period) {
        this.id = id;
        this.period = period;
    }

    public MembershipId getId() {
        return id;
    }

    public Period getPeriod() {
        return period;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

}
