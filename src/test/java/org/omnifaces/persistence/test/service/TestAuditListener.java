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
package org.omnifaces.persistence.test.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.omnifaces.persistence.audit.AuditedChange;
import org.omnifaces.persistence.audit.AuditedChanges;

@ApplicationScoped
public class TestAuditListener {

    private static final List<AuditedChange> CHANGES = new CopyOnWriteArrayList<>();
    private static final List<Integer> TRANSACTION_STATUSES = new CopyOnWriteArrayList<>();
    private static final List<AuditedChanges> CHANGES_PER_TRANSACTION = new CopyOnWriteArrayList<>();

    @Resource
    private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public void onAuditedChange(@Observes AuditedChange change) {
        CHANGES.add(change);
        TRANSACTION_STATUSES.add(transactionSynchronizationRegistry.getTransactionStatus());
    }

    public void onAuditedChanges(@Observes AuditedChanges changes) {
        CHANGES_PER_TRANSACTION.add(changes);
    }

    public static List<AuditedChange> getChanges() {
        return CHANGES;
    }

    /**
     * Returns the status of the audited transaction at the moment each change was observed, in the order of {@link #getChanges()}.
     *
     * @return The status of the audited transaction at the moment each change was observed.
     */
    public static List<Integer> getTransactionStatuses() {
        return TRANSACTION_STATUSES;
    }

    /**
     * Returns the observed {@link AuditedChanges} events, one per committed transaction.
     *
     * @return The observed {@link AuditedChanges} events.
     */
    public static List<AuditedChanges> getChangesPerTransaction() {
        return CHANGES_PER_TRANSACTION;
    }

    public static void clearChanges() {
        CHANGES.clear();
        TRANSACTION_STATUSES.clear();
        CHANGES_PER_TRANSACTION.clear();
    }

}
