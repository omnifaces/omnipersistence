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

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import org.omnifaces.persistence.audit.AuditedChange;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.AuditLog;
import org.omnifaces.persistence.test.model.Config;

/**
 * Persists an {@link AuditLog} for each {@link AuditedChange}, in a transaction of its own, the way an application observes it.
 */
@ApplicationScoped
public class AuditLogService extends BaseEntityService<Long, AuditLog> {

    @Transactional(REQUIRES_NEW)
    public void onAuditedChange(@Observes AuditedChange change) {
        persist(new AuditLog(change));
    }

    public List<String> listValueChanges(Config config) {
        return getEntityManager()
            .createQuery(
                "SELECT l FROM AuditLog l WHERE l.entityName = 'Config' AND l.entityId = :entityId AND l.propertyName = 'value' ORDER BY l.id", AuditLog.class
            )
            .setParameter("entityId", String.valueOf(config.getId()))
            .getResultStream()
            .map(log -> log.getOldValue() + " -> " + log.getNewValue())
            .toList();
    }

}
