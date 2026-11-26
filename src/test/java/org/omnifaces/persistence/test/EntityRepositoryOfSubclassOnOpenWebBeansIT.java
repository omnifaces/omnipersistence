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

import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.omnifaces.persistence.test.OmniPersistenceIT.PROFILE_ID;
import static org.omnifaces.persistence.test.OmniPersistenceIT.withOmniPersistence;

import jakarta.enterprise.inject.AmbiguousResolutionException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.ShouldThrowException;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.persistence.test.model.Document;
import org.omnifaces.persistence.test.service.DocumentAndInvoiceRepositories;

/**
 * On OpenWebBeans, the CDI implementation of TomEE, a deployment which injects an {@link EntityRepository} of an entity and one of its subclass fails with
 * {@link AmbiguousResolutionException}, because OpenWebBeans matches the repository of the subclass to the injection point of the entity.
 */
@ExtendWith(ArquillianExtension.class)
@DisabledIfSystemProperty(
    named = PROFILE_ID, matches = "(?!.*-openjpa).*", disabledReason = "Only OpenWebBeans, the CDI implementation of TomEE, fails this deployment"
)
class EntityRepositoryOfSubclassOnOpenWebBeansIT {

    private static final String PERSISTENCE_XML = """
        <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
            <persistence-unit name="EntityRepositoryOfSubclassOnOpenWebBeansIT" />
        </persistence>
        """;

    /**
     * The deployment holds no EJB and its own persistence unit on the default data source of TomEE, because TomEE keeps the EJBs and the persistence unit of a
     * failed deployment, so that a later deployment with the same EJBs or persistence unit fails.
     */
    @Deployment
    @ShouldThrowException(AmbiguousResolutionException.class)
    public static WebArchive createDeployment() {
        var archive = create(WebArchive.class)
            .addPackage(Document.class.getPackage())
            .addClass(DocumentAndInvoiceRepositories.class)
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
            .addAsResource(new StringAsset(PERSISTENCE_XML), "META-INF/persistence.xml");
        return withOmniPersistence(archive);
    }

    @Test
    void testDeploymentFails() {
        // The deployment itself is the test, see @ShouldThrowException.
    }

}
