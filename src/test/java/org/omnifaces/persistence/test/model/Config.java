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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;

import org.omnifaces.persistence.audit.Audit;
import org.omnifaces.persistence.audit.AuditListener;
import org.omnifaces.persistence.model.NonDeletable;

@Entity
@NonDeletable
@EntityListeners(AuditListener.class)
public class Config extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "configKey")
    private String key;

    @Audit
    @Column(name = "configValue")
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
