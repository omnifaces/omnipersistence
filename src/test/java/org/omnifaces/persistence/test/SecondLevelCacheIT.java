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
package org.omnifaces.persistence.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.LongFunction;

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.query.Select;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.persistence.test.service.PersonServiceCDI;
import org.omnifaces.persistence.test.service.StartupServiceEJB;

/**
 * Tests the second level cache hints of {@code getPage()} and of a {@link Select}, in a deployment whose persistence unit caches all entities in the second
 * level cache on every provider.
 */
@ExtendWith(ArquillianExtension.class)
public class SecondLevelCacheIT {

    @Deployment
    public static WebArchive createDeployment() {
        return OmniPersistenceIT.createDeployment(SecondLevelCacheIT.class, StartupServiceEJB.class, true);
    }

    @Inject
    private PersonServiceCDI personService;

    /**
     * A page which is not cacheable reads a change which was made behind the second level cache, after a cacheable page has put the entity in there.
     */
    @Test
    void testPageWithoutCacheReadsChangeMadeBehindSecondLevelCache() {
        assertReadsChangeMadeBehindSecondLevelCache(id -> personService.getPageWithoutCache(pageOf(id)).get(0).nickname());
    }

    /**
     * A find by a JPQL select with positional parameters which is not cacheable reads a change which was made behind the second level cache.
     */
    @Test
    void testFindByJpqlSelectWithoutCacheReadsChangeMadeBehindSecondLevelCache() {
        assertReadsChangeMadeBehindSecondLevelCache(id -> personService.find(Select.jpql("WHERE e.id = ?1", id).cacheable(false).build()).get().nickname());
    }

    /**
     * A find first by a JPQL select with mapped parameters which is not cacheable reads a change which was made behind the second level cache.
     */
    @Test
    void testFindFirstByJpqlSelectWithoutCacheReadsChangeMadeBehindSecondLevelCache() {
        assertReadsChangeMadeBehindSecondLevelCache(
            id -> personService.findFirst(Select.jpql("WHERE e.id = :id", Map.of("id", id)).cacheable(false).build()).get().nickname()
        );
    }

    /**
     * A list by a criteria select which is not cacheable reads a change which was made behind the second level cache.
     */
    @Test
    void testListByCriteriaSelectWithoutCacheReadsChangeMadeBehindSecondLevelCache() {
        assertReadsChangeMadeBehindSecondLevelCache(
            id -> personService.list(
                Select.<Person>criteria((criteriaBuilder, query, root) -> query.where(criteriaBuilder.equal(root.get("id"), id)), Map.of()).cacheable(false)
                    .build()
            ).get(0).nickname()
        );
    }

    /**
     * A list by an all select which is not cacheable reads a change which was made behind the second level cache.
     */
    @Test
    void testListByAllSelectWithoutCacheReadsChangeMadeBehindSecondLevelCache() {
        assertReadsChangeMadeBehindSecondLevelCache(
            id -> personService.list(Select.all().cacheable(false).build()).stream().filter(person -> person.getId() == id).findFirst().get().nickname()
        );
    }

    private static Page pageOf(long id) {
        return Page.with().allMatch(Map.of("id", id)).build();
    }

    private void assertReadsChangeMadeBehindSecondLevelCache(LongFunction<String> readNicknameWithoutCache) {
        var id = 1L;
        var nickname = personService.getPage(pageOf(id), false).get(0).nickname();
        assertTrue(personService.isInSecondLevelCache(id), "Person is in second level cache after cacheable page");

        try {
            personService.updateNicknameViaNativeSql(id, "uncached");
            assertEquals("uncached", readNicknameWithoutCache.apply(id), "Nickname read without cache");
        }
        finally {
            personService.updateNicknameViaNativeSql(id, nickname);
            personService.getPageWithoutCache(pageOf(id)); // Puts the restored nickname back in the second level cache.
        }
    }

}
