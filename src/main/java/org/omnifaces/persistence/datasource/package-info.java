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
 * Switchable {@link javax.sql.DataSource} implementations configured by an external properties file.
 * <p>
 * This package contains:
 * <ul>
 * <li>{@link SwitchableCommonDataSource} - data source which reads the real driver class and connection settings from a properties file</li>
 * <li>{@link SwitchableXADataSource} - the XA variant of {@link SwitchableCommonDataSource}</li>
 * <li>{@link CommonDataSourceWrapper} - wrapper which sets and gets the properties of any data source by name, via its getters and setters</li>
 * <li>{@link PropertiesFileLoader} - SPI to replace how the properties file is loaded, registered via {@link java.util.ServiceLoader}</li>
 * </ul>
 * Declare the data source in {@code web.xml} or via {@code @DataSourceDefinition} and set its {@code configFile} property to the properties file. Swapping the
 * file switches the target database without a change in the application.
 */
package org.omnifaces.persistence.datasource;
