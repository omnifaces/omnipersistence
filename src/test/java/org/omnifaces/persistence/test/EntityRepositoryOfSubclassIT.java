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
import static org.omnifaces.persistence.test.OmniPersistenceIT.OPENJPA;
import static org.omnifaces.persistence.test.OmniPersistenceIT.PROFILE_ID;

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.persistence.test.model.Document;
import org.omnifaces.persistence.test.model.Invoice;
import org.omnifaces.persistence.test.service.DocumentAndInvoiceRepositories;
import org.omnifaces.persistence.test.service.StartupServiceEJB;

/**
 * An {@link EntityRepository} of an entity and one of its subclass can both be injected in one application. OpenWebBeans fails such a deployment, see
 * {@link EntityRepositoryOfSubclassOnOpenWebBeansIT}.
 */
@ExtendWith(ArquillianExtension.class)
@DisabledIfSystemProperty(named = PROFILE_ID, matches = OPENJPA, disabledReason = "OpenWebBeans, the CDI implementation of TomEE, fails this deployment")
class EntityRepositoryOfSubclassIT {

    @Deployment
    public static WebArchive createDeployment() {
        return OmniPersistenceIT.createDeployment(EntityRepositoryIT.class, StartupServiceEJB.class).addClass(DocumentAndInvoiceRepositories.class);
    }

    @Inject
    private DocumentAndInvoiceRepositories repositories;

    @Test
    void testRepositoriesOfEntityAndSubclassHoldEachTheirOwnType() {
        assertEquals(Document.class, repositories.getDocuments().getEntityType(), "Entity type of repository of Document");
        assertEquals(Invoice.class, repositories.getInvoices().getEntityType(), "Entity type of repository of Invoice");
    }

}
