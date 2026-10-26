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

import static jakarta.persistence.criteria.JoinType.LEFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostponedFetch}. It must postpone fetches by attribute name, and reject everything else with an {@link UnsupportedOperationException},
 * as there is no actual fetch to delegate to.
 */
public class PostponedFetchTest {

    @Test
    void fetchByNameIsPostponed() {
        var postponedFetches = new HashSet<String>();
        new PostponedFetch<>(postponedFetches, "phones").fetch("owner");
        assertEquals(Set.of("phones", "phones.owner"), postponedFetches);
    }

    @Test
    void fetchByNameWithJoinTypeIsPostponed() {
        var postponedFetches = new HashSet<String>();
        new PostponedFetch<>(postponedFetches, "phones").fetch("owner", LEFT);
        assertEquals(Set.of("phones", "phones.owner"), postponedFetches);
    }

    @Test
    void otherMethodsThrowUnsupportedOperationException() {
        var fetch = new PostponedFetch<>(new HashSet<>(), "phones");
        assertThrows(UnsupportedOperationException.class, fetch::getAttribute);
        assertThrows(UnsupportedOperationException.class, fetch::getFetches);
        assertThrows(UnsupportedOperationException.class, fetch::getParent);
        assertThrows(UnsupportedOperationException.class, fetch::getJoinType);
    }

}
