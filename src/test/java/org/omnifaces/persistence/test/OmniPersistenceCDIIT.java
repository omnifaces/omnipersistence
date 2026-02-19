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

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Comment;
import org.omnifaces.persistence.test.model.Lookup;
import org.omnifaces.persistence.test.model.Text;
import org.omnifaces.persistence.test.service.CommentServiceCDI;
import org.omnifaces.persistence.test.service.ConfigService;
import org.omnifaces.persistence.test.service.ConfigServiceCDI;
import org.omnifaces.persistence.test.service.LookupServiceCDI;
import org.omnifaces.persistence.test.service.PersonService;
import org.omnifaces.persistence.test.service.PersonServiceCDI;
import org.omnifaces.persistence.test.service.PhoneService;
import org.omnifaces.persistence.test.service.PhoneServiceCDI;
import org.omnifaces.persistence.test.service.StartupServiceEJB;
import org.omnifaces.persistence.test.service.TextServiceCDI;

public class OmniPersistenceCDIIT extends OmniPersistenceIT {

    @Deployment
    public static WebArchive createDeployment() {
        return OmniPersistenceIT.createDeployment(OmniPersistenceCDIIT.class, StartupServiceEJB.class);
    }

    @Inject private PersonServiceCDI personService;
    @Inject private PhoneServiceCDI phoneService;
    @Inject private TextServiceCDI textService;
    @Inject private CommentServiceCDI commentService;
    @Inject private LookupServiceCDI lookupService;
    @Inject private ConfigServiceCDI configService;

    @Override protected PersonService personService()                   { return personService; }
    @Override protected PhoneService phoneService()                     { return phoneService; }
    @Override protected BaseEntityService<Long, Text> textService()     { return textService; }
    @Override protected BaseEntityService<Long, Comment> commentService() { return commentService; }
    @Override protected BaseEntityService<String, Lookup> lookupService() { return lookupService; }
    @Override protected ConfigService configService()                   { return configService; }

}
