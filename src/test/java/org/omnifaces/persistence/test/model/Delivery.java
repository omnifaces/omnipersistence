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

import static jakarta.persistence.FetchType.LAZY;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

/**
 * An entity whose transient fields delegate to <code>@ManyToOne</code> children: {@link #getStreet()} to the nearest child declaring it, {@link #getEmail()} to
 * one of two children at the same depth declaring it, and {@link #getTitle()} to a child reached only through a collection.
 */
@Entity
public class Delivery extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = LAZY)
    private Address origin;

    @ManyToOne(fetch = LAZY)
    private Person sender;

    @ManyToOne(fetch = LAZY)
    private Person recipient;

    @ManyToMany
    private List<Track> tracks = new ArrayList<>();

    public Address getOrigin() {
        return origin;
    }

    public void setOrigin(Address origin) {
        this.origin = origin;
    }

    public Person getSender() {
        return sender;
    }

    public void setSender(Person sender) {
        this.sender = sender;
    }

    public Person getRecipient() {
        return recipient;
    }

    public void setRecipient(Person recipient) {
        this.recipient = recipient;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
    }

    @Transient
    public String getTitle() {
        return getTracks().get(0).getAlbum().getTitle();
    }

    @Transient
    public String getStreet() {
        return getOrigin().getStreet();
    }

    @Transient
    public String getEmail() {
        return getSender().getEmail();
    }

}
