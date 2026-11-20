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

import jakarta.persistence.Entity;

import org.omnifaces.persistence.audit.AuditedChange;

@Entity
public class AuditLog extends LocalGeneratedIdEntity {

    private static final long serialVersionUID = 1L;

    private String entityName;
    private String entityId;
    private String propertyName;
    private String oldValue;
    private String newValue;

    public AuditLog() {
        // Keep default c'tor alive for JPA.
    }

    public AuditLog(AuditedChange change) {
        entityName = change.getEntityName();
        entityId = String.valueOf((Object) change.getEntityId());
        propertyName = change.getPropertyName();
        oldValue = String.valueOf(change.getOldValue());
        newValue = String.valueOf(change.getNewValue());
    }

    public String getEntityName() {
        return entityName;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

}
