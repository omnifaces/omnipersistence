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

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Basic;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

import org.omnifaces.persistence.model.SoftDeletable;

/**
 * A soft deletable entity with a lazy <code>Map</code> and a lazy <code>byte[]</code>.
 */
@Entity
public class Attachment extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    @ElementCollection
    private Map<String, String> properties = new HashMap<>();

    @Lob
    @Basic(fetch = LAZY)
    private byte[] content;

    @SoftDeletable
    private boolean deleted;

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

}
