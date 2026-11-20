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

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import jakarta.transaction.Transactional;

/**
 * <p>
 * CDI event fired by {@link AuditListener} once per committed JTA transaction, with all {@link AuditedChange} instances of that transaction in flush order. It
 * is fired after the {@link AuditedChange} events of the transaction, and is not fired for a transaction without changes, nor for a transaction which is rolled
 * back. An observer can thus persist the whole audit log of a transaction in one transaction of its own, for example via
 * {@link Transactional.TxType#REQUIRES_NEW}. When the changes cannot be queued for the transaction, this event is fired during the flush, right after the
 * {@link AuditedChange} events of the flushed entity, and then holds the changes of that one entity only. {@link AuditListener} describes when this happens.
 * <p>
 * Usage example:
 *
 * <pre>
 *
 * &#64;Transactional(REQUIRES_NEW)
 * public void onAuditedChanges(&#64;Observes AuditedChanges changes) {
 *     changes.forEach(change -&gt; auditLogService.persist(new AuditLog(change)));
 * }
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 2.0
 * @see AuditedChange
 * @see AuditListener
 */
public final class AuditedChanges implements Iterable<AuditedChange> {

    private final List<AuditedChange> changes;

    AuditedChanges(List<AuditedChange> changes) {
        this.changes = List.copyOf(changes);
    }

    /**
     * Returns the changes in flush order.
     *
     * @return The changes in flush order, as an unmodifiable list.
     */
    public List<AuditedChange> getChanges() {
        return changes;
    }

    /**
     * Returns the changes in flush order as a stream.
     *
     * @return The changes in flush order as a stream.
     */
    public Stream<AuditedChange> stream() {
        return changes.stream();
    }

    @Override
    public Iterator<AuditedChange> iterator() {
        return changes.iterator();
    }

    @Override
    public String toString() {
        return "AuditedChanges" + changes;
    }

}
