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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.omnifaces.persistence.service.EntityRepository;
import org.omnifaces.persistence.test.model.Document;
import org.omnifaces.persistence.test.model.Invoice;

/**
 * Injects an {@link EntityRepository} of an entity and one of its subclass. Only the deployments of the tests on such repositories hold it, because
 * OpenWebBeans fails a deployment with it.
 */
@ApplicationScoped
public class DocumentAndInvoiceRepositories {

    @Inject
    private EntityRepository<Long, Document> documents;

    @Inject
    private EntityRepository<Long, Invoice> invoices;

    public EntityRepository<Long, Document> getDocuments() {
        return documents;
    }

    public EntityRepository<Long, Invoice> getInvoices() {
        return invoices;
    }

}
