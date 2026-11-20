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

import static jakarta.transaction.Status.STATUS_ACTIVE;
import static jakarta.transaction.Status.STATUS_MARKED_ROLLBACK;
import static jakarta.transaction.Status.STATUS_NO_TRANSACTION;
import static jakarta.transaction.Status.STATUS_ROLLEDBACK;
import static jakarta.transaction.Status.STATUS_ROLLING_BACK;
import static java.beans.Introspector.getBeanInfo;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Map.entry;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toSet;
import static org.omnifaces.utils.reflect.Reflections.accessField;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedFields;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.event.Created;
import org.omnifaces.persistence.event.Deleted;
import org.omnifaces.persistence.listener.BaseEntityListener;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Jakarta Persistence entity listener that tracks changes to fields annotated with {@link Audit} and fires a CDI {@link AuditedChange} event for each detected
 * change after the transaction is committed, followed by one CDI {@link AuditedChanges} event with all changes of the transaction.
 * <p>
 * Usage:
 * <ol>
 * <li>Declare this listener as {@link EntityListeners} on your entity.
 * <li>Put {@link Audit} annotation on fields of interest. Each of them must have a getter.
 * <li>Observe the {@link AuditedChange} or {@link AuditedChanges} CDI event.
 * </ol>
 * <p>
 * Any entity can be audited, it does not need to extend {@code BaseEntity}. Only instances which went through {@link PostLoad} in the current persistence
 * context are audited, so an entity which is persisted and then modified in the same persistence context is not audited. Each flush of a change is audited
 * against the values of the previous flush. The {@link Audit} fields must be of a basic type, such as a string, number, date or enum, and not an entity or
 * collection, because the values are kept until the entity instance itself is released.
 * <p>
 * A value is changed when it is not equal to the previous value, also by content for an array. When one value is an instance of the class of the other and that
 * class is {@link Comparable}, it is changed only when {@link Comparable#compareTo(Object)} does not return 0, so that a <code>Timestamp</code> loaded for a
 * <code>Date</code> of the same instant, or a <code>BigDecimal</code> of another scale, is no change. A value type whose <code>compareTo</code> returns 0 for
 * values which are not equal is thus not audited for that difference. A mutable value such as a <code>Date</code>, <code>Calendar</code> or array is kept as a
 * copy, so that a change in place is audited.
 * <p>
 * Only updates are audited. For persist and delete, observe the CDI events {@link Created} and {@link Deleted} instead. {@link BaseEntityListener} fires them
 * for every entity extending {@code BaseEntity}, and for any other entity which declares it in {@link EntityListeners}.
 * <p>
 * The values of the {@link Audit} fields and the ID are read via reflection during {@link PreUpdate}. When reading fails, for example because reflective access
 * is refused, an {@link IllegalStateException} is thrown, which aborts the flush.
 * <p>
 * Outside a CDI container no event is fired. The CDI lookup is then retried on every callback, so that events are fired once CDI becomes available.
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
 * The changes are detected during the flush, from a {@link PreUpdate} callback, and queued per JTA transaction via the
 * {@link TransactionSynchronizationRegistry}. The {@link AuditedChange} events are fired in order after the transaction is committed, followed by one
 * {@link AuditedChanges} event with all of them, and none is fired when the transaction is rolled back. So an observer can persist an audit log. It must then
 * start a transaction of its own, for example via {@link Transactional.TxType#REQUIRES_NEW}, because the audited transaction is already completed. The audit
 * log is thus not written atomically with the change: when writing it fails, the change stays committed. An exception of an observer is logged as a warning,
 * and the other events of the transaction are still fired. The entity may no longer be managed by then, so a lazy association of
 * {@link AuditedChange#getEntity()} may not be loaded. The observer should not extend {@link BaseEntityService}: its <code>@PostConstruct</code> uses the
 * {@link EntityManager} before its own transaction is started, which a server such as TomEE rejects when the observer is first created after the commit. Inject
 * the service into the observer instead, as below. On GlassFish, a {@link Transactional.TxType#REQUIRES_NEW} observer can throw after its own transaction is
 * committed, because it cannot resume the completed audited transaction, and CDI then does not notify the observers after it. Give such an observer a higher
 * {@link Priority} than the other observers of the event, so that it is notified last.
 * <p>
 * Observer example:
 *
 * <pre>
 * &#64;ApplicationScoped
 * public class AuditLogger {
 *
 *     &#64;Inject
 *     private AuditLogService auditLogService;
 *
 *     &#64;Transactional(REQUIRES_NEW)
 *     public void onAuditedChange(&#64;Observes AuditedChange change) {
 *         auditLogService.persist(new AuditLog(change));
 *     }
 *
 * }
 * </pre>
 * <p>
 * The queue of a transaction is registered when an audited entity is loaded in it, or else when its change is flushed. When that is not possible, the event is
 * fired immediately, during the flush, and a warning is logged once per entity class and reason. This happens when there is no JTA transaction on the current
 * thread or no {@link TransactionSynchronizationRegistry}, as outside a Jakarta EE container, and when the transaction rejects the registration, as when an
 * entity which was not loaded in the current transaction is flushed at commit, for example via an extended or application-managed {@link EntityManager}. The
 * {@link AuditedChanges} event then holds the changes of the flushed entity only, and is fired right after its {@link AuditedChange} events. An observer must
 * then not invoke {@link EntityManager} operations in the persistence context being flushed: Jakarta Persistence, section "Lifecycle Callback Methods", says
 * that a portable lifecycle method should not invoke them, and its footnote extends this to objects injected into an entity listener. A change flushed in a
 * transaction which is marked for rollback or rolled back is not fired at all.
 * <p>
 * The queue follows the JTA transaction of the current thread, also for a <code>RESOURCE_LOCAL</code> persistence unit. A change of such a persistence unit
 * made while a JTA transaction is active is thus fired after the commit of that JTA transaction, or dropped with its rollback.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Audit
 * @see AuditedChange
 * @see AuditedChanges
 */
public class AuditListener {

    private static final Logger logger = Logger.getLogger(AuditListener.class.getName());

    private static final String TRANSACTION_SYNCHRONIZATION_REGISTRY = "java:comp/TransactionSynchronizationRegistry";
    private static final Object QUEUE_KEY = AuditedChangeQueue.class;

    private static final String LOG_WARNING_FIRED_DURING_FLUSH = "AuditedChange and AuditedChanges of %s are fired during the flush instead of after the commit, because %s."
        + " An observer must then not use the EntityManager of the persistence context being flushed. This is logged once per entity class and reason.";
    private static final String LOG_WARNING_OBSERVER_FAILED = "Observer of AuditedChange of %s %s property %s failed after the commit."
        + " The change stays committed, and the other events of the transaction are still fired.";
    private static final String LOG_WARNING_CHANGES_OBSERVER_FAILED = "Observer of AuditedChanges failed after the commit."
        + " The changes stay committed, and their AuditedChange events are already fired.";

    /**
     * The entity class names and fallbacks which are warned about.
     */
    static final Set<Entry<String, Fallback>> WARNED_FALLBACKS = ConcurrentHashMap.newKeySet();

    private static volatile Supplier<TransactionSynchronizationRegistry> registryLookup = AuditListener::lookupRegistry;
    private static volatile TransactionSynchronizationRegistry registry;
    private static volatile boolean registryLookedUp;

    private static final ClassValue<List<PropertyDescriptor>> AUDITABLE_PROPERTIES = new ClassValue<>() {

        @Override
        protected List<PropertyDescriptor> computeValue(Class<?> type) {
            return computeAuditableProperties(type);
        }

    };

    private static final ClassValue<Function<Object, Object>> ID_GETTERS = new ClassValue<>() {

        @Override
        protected Function<Object, Object> computeValue(Class<?> type) {
            return computeIdGetter(type);
        }

    };

    /**
     * The values of the auditable properties per entity instance, as loaded or last updated. Each persistence context has its own instance of the same entity,
     * so the key must be the instance itself, and its entry is released along with it.
     */
    static final WeakIdentityMap<Object, Map<String, Object>> SNAPSHOTS = new WeakIdentityMap<>();

    @Inject
    private BeanManager beanManager;

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
    public void beforeUpdate(Object entity) {
        var auditableProperties = AUDITABLE_PROPERTIES.get(entity.getClass());

        if (!auditableProperties.isEmpty()) {
            SNAPSHOTS.put(entity, readValues(entity, auditableProperties));
            registerQueue();
        }
    }

    /**
     * Compares current values with the snapshot taken during {@link PostLoad} or the previous {@link PreUpdate} and queues an {@link AuditedChange} event for
     * each difference, to be fired after the transaction is committed, along with one {@link AuditedChanges} event. When it cannot be queued, it is fired
     * immediately, as described in the class documentation.
     *
     * @param entity The entity being updated.
     */
    @PreUpdate
    public void afterUpdate(Object entity) {
        var changes = detectChanges(entity);

        if (!changes.isEmpty()) {
            enqueue(changes).ifPresent(fallback -> {
                warnOnce(entity.getClass(), fallback);
                fireAuditedChangeEvents(changes);
            });
        }
    }

    /**
     * Lets the given managed entity be audited against the snapshot of the given detached entity it was merged from, if it has no snapshot of its own. Some
     * persistence providers, such as OpenJPA, do not invoke {@link PostLoad} on the managed entity returned by {@link EntityManager#merge(Object)}.
     * {@link BaseEntityService#update(Object)} invokes this after merging.
     *
     * @param detached The detached entity which was merged.
     * @param managed The managed entity returned by the merge.
     * @since 1.2
     */
    public static void copySnapshot(Object detached, Object managed) {
        if (detached != managed && SNAPSHOTS.get(managed) == null) {
            var snapshot = SNAPSHOTS.get(detached);

            if (snapshot != null) {
                SNAPSHOTS.put(managed, snapshot);
                registerQueue();
            }
        }
    }

    /**
     * Returns the changes of the auditable properties of the given entity since its snapshot, and replaces the snapshot with the current values.
     *
     * @param entity The entity being updated.
     * @return The changes of the auditable properties of the given entity since its snapshot, or an empty list if it has no snapshot.
     */
    static List<AuditedChange> detectChanges(Object entity) {
        var oldValues = SNAPSHOTS.get(entity);

        if (oldValues == null) {
            return emptyList();
        }

        var newValues = readValues(entity, AUDITABLE_PROPERTIES.get(entity.getClass()));
        SNAPSHOTS.put(entity, newValues);
        var changes = new ArrayList<AuditedChange>();
        Object entityId = null;

        for (var newValue : newValues.entrySet()) {
            var oldValue = oldValues.get(newValue.getKey());

            if (!isSameValue(oldValue, newValue.getValue())) {
                if (changes.isEmpty()) {
                    entityId = ID_GETTERS.get(entity.getClass()).apply(entity);
                }

                changes.add(
                    new AuditedChange(
                        entity, entityId, Provider.getEntityName(entity.getClass()), newValue.getKey(), oldValue, copyIfMutable(newValue.getValue())
                    )
                );
            }
        }

        return changes;
    }

    private static List<PropertyDescriptor> computeAuditableProperties(Class<?> type) {
        var auditableFieldNames = listAnnotatedFields(type, Audit.class).stream().map(Field::getName).collect(toSet());

        return getPropertyDescriptors(type).stream().filter(property -> auditableFieldNames.contains(property.getName())).toList();
    }

    private static Function<Object, Object> computeIdGetter(Class<?> type) {
        if (BaseEntity.class.isAssignableFrom(type)) {
            return entity -> ((BaseEntity<?>) entity).getId();
        }

        var idFields = listAnnotatedFields(type, Id.class);
        idFields.addAll(listAnnotatedFields(type, EmbeddedId.class));

        if (!idFields.isEmpty()) {
            return singleOrNull(idFields, (entity, idField) -> accessField(entity, idField));
        }

        var idGetters = getPropertyDescriptors(type).stream().map(PropertyDescriptor::getReadMethod).filter(Objects::nonNull)
            .filter(getter -> getter.isAnnotationPresent(Id.class) || getter.isAnnotationPresent(EmbeddedId.class)).toList();
        return singleOrNull(idGetters, (entity, idGetter) -> invokeMethod(entity, idGetter));
    }

    private static <M> Function<Object, Object> singleOrNull(List<M> idMembers, BiFunction<Object, M, Object> reader) {
        if (idMembers.size() != 1) {
            return entity -> null;
        }

        var idMember = idMembers.get(0);
        return entity -> reader.apply(entity, idMember);
    }

    private static List<PropertyDescriptor> getPropertyDescriptors(Class<?> type) {
        try {
            return List.of(getBeanInfo(type).getPropertyDescriptors());
        }
        catch (IntrospectionException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private static Map<String, Object> readValues(Object entity, List<PropertyDescriptor> properties) {
        var values = new LinkedHashMap<String, Object>(properties.size());

        for (var property : properties) {
            values.put(property.getName(), copyIfMutable(invokeMethod(entity, property.getReadMethod())));
        }

        return values;
    }

    private static Object copyIfMutable(Object value) {
        if (value instanceof Date date) {
            return date.clone();
        }
        else if (value instanceof Calendar calendar) {
            return calendar.clone();
        }
        else if (value != null && value.getClass().isArray()) {
            var length = Array.getLength(value);
            var copy = Array.newInstance(value.getClass().getComponentType(), length);
            System.arraycopy(value, 0, copy, 0, length);
            return copy;
        }
        else {
            return value;
        }
    }

    /**
     * Compares by {@link Comparable#compareTo(Object)} when one value is an instance of the class of the other: a provider may load a <code>Date</code> as a
     * <code>Timestamp</code>, and <code>BigDecimal</code> equals takes the scale into account.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static boolean isSameValue(Object oldValue, Object newValue) {
        if (Objects.deepEquals(oldValue, newValue)) {
            return true;
        }
        else if (oldValue instanceof Comparable comparable && oldValue.getClass().isInstance(newValue)) {
            return comparable.compareTo(newValue) == 0;
        }
        else if (newValue instanceof Comparable comparable && newValue.getClass().isInstance(oldValue)) {
            return comparable.compareTo(oldValue) == 0;
        }
        else {
            return false;
        }
    }

    private static void registerQueue() {
        var foundRegistry = findRegistry(false);

        if (foundRegistry != null && foundRegistry.getTransactionStatus() == STATUS_ACTIVE) {
            try {
                getOrRegisterQueue(foundRegistry);
            }
            catch (IllegalStateException ignore) {
                // The fallback is decided when a change is flushed.
            }
        }
    }

    /**
     * Returns the fallback when the given changes cannot be queued, or empty when they are queued, or dropped because the transaction will not commit.
     */
    private Optional<Fallback> enqueue(List<AuditedChange> changes) {
        var foundRegistry = findRegistry(true);

        if (foundRegistry == null) {
            return Optional.of(Fallback.NO_REGISTRY);
        }

        return switch (foundRegistry.getTransactionStatus()) {
            case STATUS_NO_TRANSACTION -> Optional.of(Fallback.NO_TRANSACTION);
            case STATUS_MARKED_ROLLBACK, STATUS_ROLLING_BACK, STATUS_ROLLEDBACK -> Optional.empty();
            default -> enqueue(foundRegistry, changes);
        };
    }

    private Optional<Fallback> enqueue(TransactionSynchronizationRegistry foundRegistry, List<AuditedChange> changes) {
        try {
            var queue = getOrRegisterQueue(foundRegistry);
            queue.addAll(changes, this::fireAuditedChangeEventsAfterCommit);
            return Optional.empty();
        }
        catch (IllegalStateException e) {
            return Optional.of(Fallback.REGISTRATION_REJECTED);
        }
    }

    private static AuditedChangeQueue getOrRegisterQueue(TransactionSynchronizationRegistry foundRegistry) {
        var queue = (AuditedChangeQueue) foundRegistry.getResource(QUEUE_KEY);

        if (queue == null) {
            queue = new AuditedChangeQueue();
            foundRegistry.registerInterposedSynchronization(queue);
            foundRegistry.putResource(QUEUE_KEY, queue);
        }

        return queue;
    }

    /**
     * Returns the registry, looked up once, and again when <code>retry</code> is <code>true</code> and no registry was found so far.
     */
    private static TransactionSynchronizationRegistry findRegistry(boolean retry) {
        var foundRegistry = registry;

        if (foundRegistry == null && (retry || !registryLookedUp)) {
            registryLookedUp = true;
            foundRegistry = registryLookup.get();
            registry = foundRegistry;
        }

        return foundRegistry;
    }

    /**
     * Uses the given lookup of the registry from now on, and forgets the registry found so far.
     */
    static void useRegistryLookup(Supplier<TransactionSynchronizationRegistry> lookup) {
        registryLookup = lookup;
        registry = null;
        registryLookedUp = false;
    }

    /**
     * Returns the registry from JNDI, or <code>null</code> when there is none.
     */
    static TransactionSynchronizationRegistry lookupRegistry() {
        try {
            return (TransactionSynchronizationRegistry) new InitialContext().lookup(TRANSACTION_SYNCHRONIZATION_REGISTRY);
        }
        catch (NamingException ignore) {
            return null; // Can happen when actually not in Jakarta EE environment, e.g. local unit test.
        }
    }

    private static void warnOnce(Class<?> entityType, Fallback fallback) {
        if (WARNED_FALLBACKS.add(entry(entityType.getName(), fallback))) {
            logger.warning(() -> format(LOG_WARNING_FIRED_DURING_FLUSH, entityType.getName(), fallback.reason));
        }
    }

    private void fireAuditedChangeEvents(List<AuditedChange> changes) {
        changes.forEach(this::fireEvent);
        fireEvent(new AuditedChanges(changes));
    }

    private void fireAuditedChangeEventsAfterCommit(List<AuditedChange> changes) {
        for (var change : changes) {
            try {
                fireEvent(change);
            }
            catch (RuntimeException e) {
                logger.log(WARNING, e, () -> format(LOG_WARNING_OBSERVER_FAILED, change.getEntityName(), change.getEntityId(), change.getPropertyName()));
            }
        }

        try {
            fireEvent(new AuditedChanges(changes));
        }
        catch (RuntimeException e) {
            logger.log(WARNING, LOG_WARNING_CHANGES_OBSERVER_FAILED, e);
        }
    }

    private void fireEvent(Object event) {
        findBeanManager().ifPresent(beanManager -> beanManager.getEvent().fire(event));
    }

    private Optional<BeanManager> findBeanManager() {
        if (beanManager == null) {
            try {
                beanManager = CDI.current().getBeanManager(); // Work around for CDI @Inject not working in JPA EntityListener (as observed in OpenJPA).
            }
            catch (IllegalStateException ignore) {
                beanManager = null; // Can happen when actually not in CDI environment, e.g. local unit test.
            }
        }

        return Optional.ofNullable(beanManager);
    }

    enum Fallback {

        NO_REGISTRY("there is no TransactionSynchronizationRegistry"),
        NO_TRANSACTION("there is no active JTA transaction"),
        REGISTRATION_REJECTED(
            "the transaction rejected the registration, as when the entity was not loaded in the current transaction and is flushed at commit"
        );

        private final String reason;

        Fallback(String reason) {
            this.reason = reason;
        }

    }

}
