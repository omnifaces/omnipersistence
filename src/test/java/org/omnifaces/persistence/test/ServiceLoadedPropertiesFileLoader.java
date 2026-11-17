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

import java.util.Map;

import org.omnifaces.persistence.datasource.PropertiesFileLoader;

/**
 * Test {@link PropertiesFileLoader} for {@link SwitchableCommonDataSourceTest}. It returns the requested file name as server name of a
 * {@link RecordingDataSource}.
 */
public class ServiceLoadedPropertiesFileLoader implements PropertiesFileLoader {

    @Override
    public Map<String, String> loadFromFile(String fileName) {
        return Map.of("className", RecordingDataSource.class.getName(), "serverName", fileName);
    }

}
