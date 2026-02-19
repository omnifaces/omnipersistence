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

import org.omnifaces.persistence.Database;
import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.Config;

public abstract class ConfigService extends BaseEntityService<Long, Config> {

    public boolean isDatabaseH2() {
        return getDatabase() == Database.H2;
    }

    public boolean isProviderHibernate() {
        return getProvider() == Provider.HIBERNATE;
    }

    public boolean isProviderEclipseLink() {
        return getProvider() == Provider.ECLIPSELINK;
    }

    public boolean isProviderOpenJPA() {
        return getProvider() == Provider.OPENJPA;
    }

    public void updateValue(Long id, String newValue) {
        var config = getById(id);
        config.setValue(newValue);
    }

    public void updateKey(Long id, String newKey) {
        var config = getById(id);
        config.setKey(newKey);
    }

}
