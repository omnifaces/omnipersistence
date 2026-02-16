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
package org.omnifaces.persistence.audit;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * <p>
 * CDI event fired by {@link AuditListener} when a field annotated with {@link Audit} has changed during a JPA
 * {@code @PreUpdate} lifecycle callback.
 * <p>
 * Usage example:
 * <pre>
 * public void onAuditedChange(&#64;Observes AuditedChange change) {
 *     YourAuditLog log = new YourAuditLog();
 *     log.setEntityName(change.getEntityName());
 *     log.setEntityId(change.getEntity().getId());
 *     log.setPropertyName(change.getPropertyName());
 *     log.setOldValue(Objects.toString(change.getOldValue(), null));
 *     log.setNewValue(Objects.toString(change.getNewValue(), null));
 *     yourAuditLogService.persist(log);
 * }
 * </pre>
 *
 * @author Bauke Scholtz
 * @see Audit
 * @see AuditListener
 */
public class AuditedChange {

    private final BaseEntity<?> entity;
    private final String entityName;
    private final String propertyName;
    private final Object oldValue;
    private final Object newValue;

    AuditedChange(BaseEntity<?> entity, String entityName, String propertyName, Object oldValue, Object newValue) {
        this.entity = entity;
        this.entityName = entityName;
        this.propertyName = propertyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /**
     * Returns the entity that was changed.
     * @return The entity that was changed.
     */
    public BaseEntity<?> getEntity() {
        return entity;
    }

    /**
     * Returns the JPA entity name.
     * @return The JPA entity name.
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Returns the name of the audited property that changed.
     * @return The name of the audited property that changed.
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Returns the old value of the property before the change.
     * @return The old value of the property before the change.
     */
    public Object getOldValue() {
        return oldValue;
    }

    /**
     * Returns the new value of the property after the change.
     * @return The new value of the property after the change.
     */
    public Object getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return String.format("AuditedChange[%s#%s.%s: %s -> %s]", entityName, entity.getId(), propertyName, oldValue, newValue);
    }

}
