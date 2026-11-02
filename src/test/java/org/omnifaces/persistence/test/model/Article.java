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
import static jakarta.persistence.GenerationType.IDENTITY;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.omnifaces.persistence.audit.Audit;
import org.omnifaces.persistence.audit.AuditListener;
import org.omnifaces.persistence.listener.BaseEntityListener;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.SoftDeletable;

/**
 * An entity which does not extend {@link BaseEntity}, with a generated ID not named <code>id</code>.
 */
@Entity
@EntityListeners({ BaseEntityListener.class, AuditListener.class })
public class Article {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long code;

    @Audit
    private String title;

    @ManyToOne(fetch = LAZY)
    private Category category;

    @ElementCollection
    private Set<String> tags = new HashSet<>();

    @SoftDeletable
    private boolean deleted;

    public Long getCode() {
        return code;
    }

    public void setCode(Long code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

}
