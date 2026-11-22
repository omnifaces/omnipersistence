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

import static jakarta.persistence.CascadeType.PERSIST;
import static jakarta.persistence.CascadeType.REMOVE;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

/**
 * An entity with collections whose mapped order differs from the natural order of their elements, one of entities with an embedded ID, and with a collection of
 * embeddables.
 */
@Entity
public class Album extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    private String title;

    @OneToMany(mappedBy = "album", cascade = { PERSIST, REMOVE })
    @OrderBy("title DESC")
    private List<Track> tracks = new ArrayList<>();

    @ElementCollection
    private List<Credit> credits = new ArrayList<>();

    @OneToMany
    @OrderBy("period.startDate DESC")
    private List<Membership> memberships = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
    }

    public List<Credit> getCredits() {
        return credits;
    }

    public void setCredits(List<Credit> credits) {
        this.credits = credits;
    }

    public List<Membership> getMemberships() {
        return memberships;
    }

}
