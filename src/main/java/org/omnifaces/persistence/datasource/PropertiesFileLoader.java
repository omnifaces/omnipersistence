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
package org.omnifaces.persistence.datasource;

import java.util.Map;

/**
 * <p>
 * SPI for loading data source properties from an external file. Implementations of this interface can be registered
 * via {@link java.util.ServiceLoader} to customize how {@link SwitchableCommonDataSource} loads its configuration.
 * <p>
 * If no implementation is found via ServiceLoader, the default behavior is to load the properties file from
 * <code>META-INF/</code> on the classpath.
 *
 * @author Arjan Tijms
 * @since 1.0
 * @see SwitchableCommonDataSource
 */
public interface PropertiesFileLoader {

    Map<String, String> loadFromFile(String fileName);

}
