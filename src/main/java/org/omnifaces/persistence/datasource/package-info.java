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
/**
 * Switchable {@link javax.sql.DataSource} implementations driven by an external properties file.
 * <p>
 * Declare {@link SwitchableCommonDataSource} (or its XA variant
 * {@link SwitchableXADataSource}) as a data source in {@code web.xml} or via
 * {@code @DataSourceDefinition} and point its {@code configFile} property at a properties file that specifies the
 * real driver class and connection settings. Swapping the file switches the target database without redeployment.
 * <p>
 * The properties-file loading strategy can be replaced by providing a custom
 * {@link PropertiesFileLoader} SPI implementation.
 *
 * @see SwitchableCommonDataSource
 * @see SwitchableXADataSource
 * @see PropertiesFileLoader
 */
package org.omnifaces.persistence.datasource;
