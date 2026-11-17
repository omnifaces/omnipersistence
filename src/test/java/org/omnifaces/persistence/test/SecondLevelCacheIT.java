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

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.test.service.PersonServiceCDI;
import org.omnifaces.persistence.test.service.StartupServiceEJB;

/**
 * Tests the second level cache hints of {@code getPage()}, in a deployment whose persistence unit caches all entities in the second level cache on every
 * provider.
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
        var id = 1L;
        var page = Page.with().allMatch(Map.of("id", id)).build();
        var nickname = personService.getPage(page, false).get(0).nickname();
        assertTrue(personService.isInSecondLevelCache(id), "Person is in second level cache after cacheable page");

        try {
            personService.updateNicknameViaNativeSql(id, "uncached");
            assertEquals("uncached", personService.getPageWithoutCache(page).get(0).nickname(), "Nickname read without cache");
        }
        finally {
            personService.updateNicknameViaNativeSql(id, nickname);
            personService.getPageWithoutCache(page); // Puts the restored nickname back in the second level cache.
        }
    }

}
