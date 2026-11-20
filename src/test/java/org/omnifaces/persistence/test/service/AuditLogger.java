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

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.omnifaces.persistence.audit.AuditedChange;
import org.omnifaces.persistence.audit.AuditedChanges;
import org.omnifaces.persistence.test.model.AuditLog;
import org.omnifaces.persistence.test.model.Config;

/**
 * Persists an {@link AuditLog} for each {@link AuditedChange} of a {@link Config}, in a transaction of its own. A {@link Config} whose key starts with
 * {@link #LOGGED_PER_TRANSACTION} is logged from {@link AuditedChanges} instead, in one transaction for all its changes. Changes of other entities are not
 * logged, so that the entity events of their tests stay free of {@link AuditLog}.
 */
@ApplicationScoped
public class AuditLogger {

    public static final String LOGGED_PER_TRANSACTION = "audit.transaction.";

    @Inject
    private AuditLogService auditLogService;

    @Transactional(REQUIRES_NEW)
    public void onAuditedChange(@Observes AuditedChange change) {
        if (change.getEntity() instanceof Config config && !isLoggedPerTransaction(config)) {
            auditLogService.persist(new AuditLog(change));
        }
    }

    @Transactional(REQUIRES_NEW)
    public void onAuditedChanges(@Observes AuditedChanges changes) {
        for (var change : changes) {
            if (change.getEntity() instanceof Config config && isLoggedPerTransaction(config)) {
                auditLogService.persist(new AuditLog(change));
            }
        }
    }

    private static boolean isLoggedPerTransaction(Config config) {
        return config.getKey().startsWith(LOGGED_PER_TRANSACTION);
    }

}
