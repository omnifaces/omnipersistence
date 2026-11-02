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

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.omnifaces.persistence.event.Created;
import org.omnifaces.persistence.event.Deleted;
import org.omnifaces.persistence.event.Updated;
import org.omnifaces.persistence.listener.BaseEntityListener;

/**
 * Records the entity lifecycle events fired by {@link BaseEntityListener}, as event name and entity type, e.g. <code>Created Article</code>.
 */
@ApplicationScoped
public class EntityEvents {

    private final List<String> events = new ArrayList<>();

    public synchronized void onCreated(@Observes @Created Object entity) {
        events.add("Created " + entity.getClass().getSimpleName());
    }

    public synchronized void onUpdated(@Observes @Updated Object entity) {
        events.add("Updated " + entity.getClass().getSimpleName());
    }

    public synchronized void onDeleted(@Observes @Deleted Object entity) {
        events.add("Deleted " + entity.getClass().getSimpleName());
    }

    public synchronized List<String> get() {
        return List.copyOf(events);
    }

    public synchronized void clear() {
        events.clear();
    }

}
