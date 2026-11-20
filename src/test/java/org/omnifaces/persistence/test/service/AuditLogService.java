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

import jakarta.enterprise.context.ApplicationScoped;

import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.AuditLog;
import org.omnifaces.persistence.test.model.Config;

@ApplicationScoped
public class AuditLogService extends BaseEntityService<Long, AuditLog> {

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
