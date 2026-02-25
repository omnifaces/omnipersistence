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
package org.omnifaces.persistence.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Root;

/**
 * OpenJPA stubbornly applies the range (offset/limit) to join rows rather than root entity rows when a fetch join is present, resulting in fewer root entities returned than the requested limit.
 * This root will postpone all issued fetches so BaseEntityService can ultimately execute them as a secondary JPQL query to initialize the fetched collections on the already-returned root entities.
 * The only disadvantage is that you cannot anymore sort on them when used in a lazy model. This is a technical limitation.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see PostponedFetch
 */
class OpenJPARoot<X> extends RootWrapper<X> {

    private Set<String> postponedFetches;

    public OpenJPARoot(Root<X> wrapped) {
        super(wrapped);
        postponedFetches = new HashSet<>(2);
    }

    @Override
    @SuppressWarnings("hiding")
    public <X, Y> Fetch<X, Y> fetch(String attributeName) {
        return new PostponedFetch<>(postponedFetches, attributeName);
    }

    public boolean hasPostponedFetches() {
        return !postponedFetches.isEmpty();
    }

    public void runPostponedFetches(EntityManager entityManager, Class<?> entityType, List<?> ids) {
        for (var fetch : postponedFetches) {
            entityManager.createQuery("SELECT DISTINCT e FROM " + entityType.getSimpleName() + " e JOIN FETCH e." + fetch + " WHERE e.id IN :ids", entityType).setParameter("ids", ids).getResultList();
            // No need to explicitly set in root entities; 1st level cache will sort out this while still inside the same transaction.
        }
    }
}
