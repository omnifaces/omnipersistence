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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.omnifaces.persistence.audit.AuditedChange;

@ApplicationScoped
public class TestAuditListener {

    private static final List<AuditedChange> CHANGES = new CopyOnWriteArrayList<>();

    public void onAuditedChange(@Observes AuditedChange change) {
        CHANGES.add(change);
    }

    public static List<AuditedChange> getChanges() {
        return CHANGES;
    }

    public static void clearChanges() {
        CHANGES.clear();
    }

}
