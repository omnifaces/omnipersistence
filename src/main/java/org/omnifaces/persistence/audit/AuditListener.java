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

import static java.beans.Introspector.getBeanInfo;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedFields;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;

import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Jakarta Persistence entity listener that tracks changes to fields annotated with {@link Audit} and fires a CDI {@link AuditedChange} event for each detected
 * change.
 * <p>
 * Usage:
 * <ol>
 * <li>Declare this listener as {@link EntityListeners} on your entity.
 * <li>Put {@link Audit} annotation on fields of interest.
 * <li>Observe the {@link AuditedChange} CDI event.
 * </ol>
 * <p>
 * Only instances which went through {@link PostLoad} in the current persistence context are audited, so an entity which is persisted and then modified in the
 * same persistence context is not audited. Each flush of a change is audited against the values of the previous flush. The {@link Audit} fields must be of a
 * basic type, such as a string, number, date or enum, and not an entity or collection, because the values are kept until the entity instance itself is
 * released.
 * <p>
 * Entity example:
 *
 * <pre>
 * &#64;Entity
 * &#64;EntityListeners(AuditListener.class)
 * public class YourEntity extends BaseEntity&lt;Long&gt; {
 *
 *     &#64;Audit
 *     &#64;Column
 *     private String email;
 *
 *     // ...
 * }
 * </pre>
 * <p>
 * Observer example:
 *
 * <pre>
 *
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
 * @since 1.0
 * @see Audit
 * @see AuditedChange
 */
public class AuditListener {

    private static final ClassValue<List<PropertyDescriptor>> AUDITABLE_PROPERTIES = new ClassValue<>() {

        @Override
        protected List<PropertyDescriptor> computeValue(Class<?> type) {
            return computeAuditableProperties(type);
        }

    };

    /**
     * The values of the auditable properties per entity instance, as loaded or last updated. Each persistence context has its own instance of the same entity,
     * so the key must be the instance itself, and its entry is released along with it.
     */
    static final WeakIdentityMap<BaseEntity<?>, Map<String, Object>> SNAPSHOTS = new WeakIdentityMap<>();

    @Inject
    private BeanManager beanManager;

    private Optional<BeanManager> optionalBeanManager;

    /**
     * Default constructor.
     */
    public AuditListener() {
    }

    /**
     * Snapshots the initial values of auditable properties after the entity is loaded from the database.
     *
     * @param entity The entity that was loaded.
     */
    @PostLoad
    public void beforeUpdate(BaseEntity<?> entity) {
        var auditableProperties = AUDITABLE_PROPERTIES.get(entity.getClass());

        if (!auditableProperties.isEmpty()) {
            SNAPSHOTS.put(entity, readValues(entity, auditableProperties));
        }
    }

    /**
     * Compares current values with the snapshot taken during {@link PostLoad} or the previous {@link PreUpdate} and fires an {@link AuditedChange} event for
     * each difference.
     *
     * @param entity The entity being updated.
     */
    @PreUpdate
    public void afterUpdate(BaseEntity<?> entity) {
        detectChanges(entity).forEach(this::fireAuditedChangeEvent);
    }

    /**
     * Lets the given managed entity be audited against the snapshot of the given detached entity it was merged from, if it has no snapshot of its own. Some
     * persistence providers, such as OpenJPA, do not invoke {@link PostLoad} on the managed entity returned by {@link EntityManager#merge(Object)}.
     * {@link BaseEntityService#update(BaseEntity)} invokes this after merging.
     *
     * @param detached The detached entity which was merged.
     * @param managed The managed entity returned by the merge.
     * @since 1.2
     */
    public static void copySnapshot(BaseEntity<?> detached, BaseEntity<?> managed) {
        if (detached != managed && SNAPSHOTS.get(managed) == null) {
            var snapshot = SNAPSHOTS.get(detached);

            if (snapshot != null) {
                SNAPSHOTS.put(managed, snapshot);
            }
        }
    }

    /**
     * Returns the changes of the auditable properties of the given entity since its snapshot, and replaces the snapshot with the current values.
     *
     * @param entity The entity being updated.
     * @return The changes of the auditable properties of the given entity since its snapshot, or an empty list if it has no snapshot.
     */
    static List<AuditedChange> detectChanges(BaseEntity<?> entity) {
        var oldValues = SNAPSHOTS.get(entity);

        if (oldValues == null) {
            return emptyList();
        }

        var newValues = readValues(entity, AUDITABLE_PROPERTIES.get(entity.getClass()));
        SNAPSHOTS.put(entity, newValues);
        var changes = new ArrayList<AuditedChange>();

        for (var newValue : newValues.entrySet()) {
            var oldValue = oldValues.get(newValue.getKey());

            if (!Objects.equals(oldValue, newValue.getValue())) {
                changes.add(new AuditedChange(entity, entity.getClass().getSimpleName(), newValue.getKey(), oldValue, newValue.getValue()));
            }
        }

        return changes;
    }

    private static List<PropertyDescriptor> computeAuditableProperties(Class<?> type) {
        var auditableFieldNames = listAnnotatedFields(type, Audit.class).stream().map(Field::getName).collect(toSet());

        try {
            return stream(getBeanInfo(type).getPropertyDescriptors()).filter(property -> auditableFieldNames.contains(property.getName())).toList();
        }
        catch (IntrospectionException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private static Map<String, Object> readValues(BaseEntity<?> entity, List<PropertyDescriptor> properties) {
        var values = new LinkedHashMap<String, Object>(properties.size());

        for (var property : properties) {
            values.put(property.getName(), invokeMethod(entity, property.getReadMethod()));
        }

        return values;
    }

    private void fireAuditedChangeEvent(AuditedChange change) {
        findBeanManager().ifPresent(beanManager -> beanManager.getEvent().fire(change));
    }

    private Optional<BeanManager> findBeanManager() {
        if (optionalBeanManager == null) {
            optionalBeanManager = Optional.ofNullable(getBeanManager());
        }

        return optionalBeanManager;
    }

    private BeanManager getBeanManager() {
        if (beanManager == null) {
            try {
                beanManager = CDI.current().getBeanManager(); // Work around for CDI @Inject not working in JPA EntityListener (as observed in OpenJPA).
            }
            catch (IllegalStateException ignore) {
                beanManager = null; // Can happen when actually not in CDI environment, e.g. local unit test.
            }
        }

        return beanManager;
    }

}
