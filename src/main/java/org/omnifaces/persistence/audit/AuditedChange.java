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

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * <p>
 * CDI event fired by {@link AuditListener} when a field annotated with {@link Audit} has changed during a Jakarta Persistence {@code @PreUpdate} lifecycle
 * callback.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * public void onAuditedChange(&#64;Observes AuditedChange change) {
 *     YourAuditLog log = new YourAuditLog();
 *     log.setEntityName(change.getEntityName());
 *     log.setEntityId(change.getEntityId());
 *     log.setPropertyName(change.getPropertyName());
 *     log.setOldValue(Objects.toString(change.getOldValue(), null));
 *     log.setNewValue(Objects.toString(change.getNewValue(), null));
 *     yourAuditLogService.persist(log);
 * }
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Audit
 * @see AuditListener
 */
public class AuditedChange {

    private final Object entity;
    private final Object entityId;
    private final String entityName;
    private final String propertyName;
    private final Object oldValue;
    private final Object newValue;

    AuditedChange(Object entity, Object entityId, String entityName, String propertyName, Object oldValue, Object newValue) {
        this.entity = entity;
        this.entityId = entityId;
        this.entityName = entityName;
        this.propertyName = propertyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /**
     * Returns the entity that was changed.
     *
     * @param <E> The generic entity type.
     * @return The entity that was changed.
     * @throws ClassCastException When <code>E</code> is of wrong type.
     */
    @SuppressWarnings("unchecked")
    public <E> E getEntity() {
        return (E) entity;
    }

    /**
     * Returns the ID of the entity that was changed: via {@link BaseEntity#getId()} when it extends it, else via its single attribute annotated with {@link Id}
     * or {@link EmbeddedId}, on its field, or else on its getter as declared by the entity class itself. A getter which overrides an annotated getter without
     * repeating the annotation is not recognized, because annotations on methods are not inherited.
     *
     * @param <I> The generic ID type.
     * @return The ID of the entity that was changed, or <code>null</code> when it has none, or when its ID is not a single annotated attribute, e.g. when it is
     * mapped by an {@link IdClass} or only in <code>orm.xml</code>.
     * @throws ClassCastException When <code>I</code> is of wrong type.
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    public <I> I getEntityId() {
        return (I) entityId;
    }

    /**
     * Returns the Jakarta Persistence entity name.
     *
     * @return The Jakarta Persistence entity name.
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Returns the name of the audited property that changed.
     *
     * @return The name of the audited property that changed.
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Returns the old value of the property before the change.
     *
     * @return The old value of the property before the change.
     */
    public Object getOldValue() {
        return oldValue;
    }

    /**
     * Returns the new value of the property after the change.
     *
     * @return The new value of the property after the change.
     */
    public Object getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return String.format("AuditedChange[%s.%s: %s -> %s]", entity, propertyName, oldValue, newValue);
    }

}
