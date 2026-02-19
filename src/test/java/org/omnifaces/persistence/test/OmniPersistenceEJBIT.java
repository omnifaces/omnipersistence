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

import jakarta.ejb.EJB;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.CommentServiceEJB;
import org.omnifaces.persistence.test.service.ConfigService;
import org.omnifaces.persistence.test.service.ConfigServiceEJB;
import org.omnifaces.persistence.test.service.LookupServiceEJB;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PersonServiceEJB;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.PhoneServiceEJB;
import org.omnifaces.persistence.test.service.StartupServiceCDI;
import org.omnifaces.persistence.test.service.TextServiceEJB;
import org.omnifaces.persistence.test.model.Lookup;

public class OmniPersistenceEJBIT extends OmniPersistenceIT {

    @Deployment
    public static WebArchive createDeployment() {
        return OmniPersistenceIT.createDeployment(OmniPersistenceEJBIT.class, StartupServiceCDI.class);
    }

    @EJB private PersonServiceEJB personService;
    @EJB private PhoneServiceEJB phoneService;
    @EJB private TextServiceEJB textService;
    @EJB private CommentServiceEJB commentService;
    @EJB private LookupServiceEJB lookupService;
    @EJB private ConfigServiceEJB configService;

    @Override protected PersonService personService()                   { return personService; }
    @Override protected PhoneService phoneService()                     { return phoneService; }
    @Override protected BaseEntityService<Long, Text> textService()     { return textService; }
    @Override protected BaseEntityService<Long, Comment> commentService() { return commentService; }
    @Override protected BaseEntityService<String, Lookup> lookupService() { return lookupService; }
    @Override protected ConfigService configService()                   { return configService; }

}
