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

import static jakarta.ejb.TransactionAttributeType.NOT_SUPPORTED;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;

import org.omnifaces.persistence.model.dto.Page;
import org.omnifaces.persistence.test.model.Person;
import org.omnifaces.utils.collection.PartialResultList;

@Stateless
public class PersonServiceEJB extends PersonService {

    @Override
    @TransactionAttribute(NOT_SUPPORTED)
    public PartialResultList<Person> getPageWithPhonesWithoutTransaction(Page page, boolean count) {
        return super.getPageWithPhonesWithoutTransaction(page, count);
    }

}
