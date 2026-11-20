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

import static jakarta.transaction.Status.STATUS_COMMITTED;

import java.util.ArrayList;
import java.util.List;

import jakarta.transaction.Synchronization;

/**
 * The firings of the {@link AuditedChange} events of one transaction, run in order after the transaction is committed, and dropped when it is not committed.
 */
class AuditedChangeQueue implements Synchronization {

    private final List<Runnable> firings = new ArrayList<>();

    void add(Runnable firing) {
        firings.add(firing);
    }

    @Override
    public void beforeCompletion() {
        // NOOP.
    }

    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) {
            firings.forEach(Runnable::run);
        }
    }

}
