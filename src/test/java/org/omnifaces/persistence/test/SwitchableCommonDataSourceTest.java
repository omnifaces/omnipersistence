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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omnifaces.persistence.datasource.PropertiesFileLoader;
import org.omnifaces.persistence.datasource.SwitchableCommonDataSource;

/**
 * Unit tests for {@link SwitchableCommonDataSource}. Its config file in <code>META-INF</code>, with or without <code>.properties</code> extension, names the
 * class of the wrapped data source and the properties to set on it, converted from text to their types. A primitive property which is not set is zero or false.
 * The properties set before the config file are set on the wrapped data source as well, and every property set afterwards is set directly on it. A
 * {@link PropertiesFileLoader} registered with the {@link ServiceLoader} loads the config file instead of <code>META-INF</code>.
 */
class SwitchableCommonDataSourceTest {

    private static final String CONFIG_FILE = "switchable-test.properties";
    private static final int MAX_CONNECTIONS = 7;
    private static final int LOGIN_TIMEOUT = 42;

    @Test
    void configFileInstantiatesClassNameAndSetsConvertedProperties() {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setConfigFile(CONFIG_FILE);

        var wrapped = (RecordingDataSource) dataSource.getWrapped();
        assertEquals("example", wrapped.getServerName());
        assertEquals(MAX_CONNECTIONS, wrapped.getMaxConnections());
    }

    @Test
    void propertiesSetBeforeConfigFileAreSetOnWrappedDataSource() {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setDatabaseName("early");
        dataSource.setConfigFile(CONFIG_FILE);

        assertEquals("early", ((RecordingDataSource) dataSource.getWrapped()).getDatabaseName());
    }

    @Test
    void propertiesSetAfterConfigFileAreSetOnWrappedDataSource() throws Exception {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setConfigFile(CONFIG_FILE);
        var logWriter = new PrintWriter(new StringWriter());
        dataSource.setLoginTimeout(LOGIN_TIMEOUT);
        dataSource.setLogWriter(logWriter);

        var wrapped = (RecordingDataSource) dataSource.getWrapped();
        assertEquals(LOGIN_TIMEOUT, wrapped.getLoginTimeout());
        assertEquals(LOGIN_TIMEOUT, dataSource.getLoginTimeout());
        assertSame(logWriter, wrapped.getLogWriter());
        assertSame(logWriter, dataSource.getLogWriter());
    }

    @Test
    void configFileWithoutPropertiesExtensionIsLoadedAsWell() {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setConfigFile(CONFIG_FILE.replace(".properties", ""));
        assertEquals("example", ((RecordingDataSource) dataSource.getWrapped()).getServerName());
    }

    @Test
    void primitivePropertiesWhichAreNotSetAreZeroOrFalse() throws Exception {
        var dataSource = new SwitchableCommonDataSource();
        assertEquals(0, dataSource.getLoginTimeout());
        assertEquals(0, dataSource.getPortNumber());
        assertFalse(dataSource.getSsl());
    }

    @Test
    void configFileWithoutClassNameIsRejected() {
        var dataSource = new SwitchableCommonDataSource();
        assertThrows(IllegalStateException.class, () -> dataSource.setConfigFile("switchable-test-without-class-name.properties"));
    }

    @Test
    void configFileWithUnknownPropertyIsRejected() {
        assertConfigFileRejected(
            "switchable-test-unknown-property.properties",
            "Data source org.omnifaces.persistence.test.RecordingDataSource has no writable property noSuchProperty."
        );
    }

    @Test
    void configFileWithReadOnlyPropertyIsRejected() {
        assertConfigFileRejected(
            "switchable-test-read-only-property.properties",
            "Data source org.omnifaces.persistence.test.RecordingDataSource has no writable property parentLogger."
        );
    }

    @Test
    void configFileWithUnconvertiblePropertyIsRejected() {
        assertConfigFileRejected(
            "switchable-test-unconvertible-property.properties",
            "Data source org.omnifaces.persistence.test.RecordingDataSource has property logWriter of type java.io.PrintWriter, which cannot be converted from a String."
        );
    }

    private static void assertConfigFileRejected(String configFile, String message) {
        var dataSource = new SwitchableCommonDataSource();
        assertEquals(message, assertThrows(IllegalArgumentException.class, () -> dataSource.setConfigFile(configFile)).getMessage());
    }

    @Test
    void configFileIsLoadedByServiceLoadedPropertiesFileLoader(@TempDir Path services) throws IOException {
        var registration = services.resolve("META-INF/services/" + PropertiesFileLoader.class.getName());
        Files.createDirectories(registration.getParent());
        Files.writeString(registration, ServiceLoadedPropertiesFileLoader.class.getName());
        var thread = Thread.currentThread();
        var originalClassLoader = thread.getContextClassLoader();

        try (var classLoader = new URLClassLoader(new URL[] { services.toUri().toURL() }, originalClassLoader)) {
            thread.setContextClassLoader(classLoader);
            var dataSource = new SwitchableCommonDataSource();
            dataSource.setConfigFile("not-in-meta-inf.properties");
            assertEquals("not-in-meta-inf.properties", ((RecordingDataSource) dataSource.getWrapped()).getServerName());
        }
        finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

}
