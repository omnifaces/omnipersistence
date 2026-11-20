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

import static jakarta.persistence.InheritanceType.JOINED;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.ManyToOne;

/**
 * The root of an entity hierarchy with a joined table per subclass.
 */
@Entity
@Inheritance(strategy = JOINED)
public class Document extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    private String title;

    @ElementCollection
    private Set<String> labels = new HashSet<>();

    @ManyToOne
    private Document inReplyTo;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    public Document getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(Document inReplyTo) {
        this.inReplyTo = inReplyTo;
    }

}
