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
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toSet;
import static org.omnifaces.persistence.service.BaseEntityService.getCurrentBaseEntityService;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.stream.Streams.stream;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.metamodel.Attribute;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * <p>
 * Jakarta Persistence entity listener that tracks changes to fields annotated with {@link Audit} and fires a CDI {@link AuditedChange}
 * event for each detected change.
 * <p>
 * Usage:
 * <ol>
 * <li>Declare this listener as {@link EntityListeners} on your entity.
 * <li>Put {@link Audit} annotation on fields of interest.
 * <li>Observe the {@link AuditedChange} CDI event.
 * </ol>
 * <p>
 * Entity example:
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
 * @since 1.0
 * @see Audit
 * @see AuditedChange
 */
public class AuditListener {

    private static final Map<Class<?>, Map<PropertyDescriptor, Map<Serializable, Object>>> AUDITABLE_PROPERTIES = new ConcurrentHashMap<>();

    @Inject
    private BeanManager beanManager;

    private Optional<BeanManager> optionalBeanManager;

    /**
     * Snapshots the initial values of auditable properties after the entity is loaded from the database.
     * @param entity The entity that was loaded.
     */
    @PostLoad
    public void beforeUpdate(BaseEntity<?> entity) {
        getAuditableProperties(entity).forEach((property, values) -> values.put(entity.getId(), invokeMethod(entity, property.getReadMethod())));
    }

    /**
     * Compares current values with the snapshot taken during {@link PostLoad} and fires
     * an {@link AuditedChange} event if a difference is detected.
     * @param entity The entity being updated.
     */
    @PreUpdate
    public void afterUpdate(BaseEntity<?> entity) {
        getAuditableProperties(entity).forEach((property, values) -> {
            var id = entity.getId();
            if (values.containsKey(id)) {
                Object newValue = invokeMethod(entity, property.getReadMethod());
                Object oldValue = values.remove(id);

                if (!Objects.equals(oldValue, newValue)) {
                    fireAuditedChangeEvent(entity, property.getName(), oldValue, newValue);
                }
            }
        });
    }

    private static Map<PropertyDescriptor, Map<Serializable, Object>> getAuditableProperties(BaseEntity<?> entity) {
        return AUDITABLE_PROPERTIES.computeIfAbsent(entity.getClass(), k -> {
            var baseEntityService = getCurrentBaseEntityService();
            var auditablePropertyNames = baseEntityService.getMetamodel(entity).getDeclaredAttributes().stream()
                .filter(a -> a.getJavaMember() instanceof Field && ((Field) a.getJavaMember()).isAnnotationPresent(Audit.class))
                .map(Attribute::getName)
                .collect(toSet());

            try {
                return stream(getBeanInfo(baseEntityService.getProvider().getEntityType(entity)).getPropertyDescriptors())
                    .filter(p -> auditablePropertyNames.contains(p.getName()))
                    .collect(toConcurrentMap(identity(), v -> new ConcurrentHashMap<>()));
            }
            catch (IntrospectionException e) {
                throw new UnsupportedOperationException(e);
            }
        });
    }

    private void fireAuditedChangeEvent(BaseEntity<?> entity, String propertyName, Object oldValue, Object newValue) {
        findBeanManager().ifPresent(beanManager ->
            beanManager.getEvent()
                       .fire(new AuditedChange(entity, entity.getClass().getSimpleName(), propertyName, oldValue, newValue)));
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
