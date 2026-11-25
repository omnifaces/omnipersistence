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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
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
    private static final int PORT = 5432;

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
    void getParentLoggerDelegatesToWrappedDataSource() {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setConfigFile(CONFIG_FILE);
        assertThrows(SQLFeatureNotSupportedException.class, dataSource::getParentLogger);
    }

    @Test
    void typedPropertiesSetBeforeInitAreReadBackBeforeInit() {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setUser("u");
        dataSource.setPassword("p");
        dataSource.setCompatible("9.0");
        dataSource.setLogLevel(1);
        dataSource.setProtocolVersion(2);
        dataSource.setPrepareThreshold(3);
        dataSource.setUnknownLength(4);
        dataSource.setSocketTimeout(5);
        dataSource.setSsl(true);
        dataSource.setSslfactory("factory");
        dataSource.setApplicationName("app");
        dataSource.setTcpKeepAlive(true);
        dataSource.setBinaryTransfer(true);
        dataSource.setBinaryTransferEnable("enabled");
        dataSource.setBinaryTransferDisable("disabled");
        dataSource.setPortNumber(Integer.valueOf(PORT));

        assertEquals("u", dataSource.getUser());
        assertEquals("p", dataSource.getPassword());
        assertEquals("9.0", dataSource.getCompatible());
        assertEquals(1, dataSource.getLogLevel());
        assertEquals(2, dataSource.getProtocolVersion());
        assertEquals(3, dataSource.getPrepareThreshold());
        assertEquals(4, dataSource.getUnknownLength());
        assertEquals(5, dataSource.getSocketTimeout());
        assertTrue(dataSource.getSsl());
        assertEquals("factory", dataSource.getSslfactory());
        assertEquals("app", dataSource.getApplicationName());
        assertTrue(dataSource.getTcpKeepAlive());
        assertTrue(dataSource.getBinaryTransfer());
        assertEquals("enabled", dataSource.getBinaryTransferEnable());
        assertEquals("disabled", dataSource.getBinaryTransferDisable());
        assertEquals(PORT, dataSource.getPortNumber());
    }

    @Test
    void typedPropertiesSetAfterInitAreSetOnWrappedDataSourceAndReadBack(@TempDir Path services) throws IOException {
        var dataSource = initializedFullDataSource(services);
        dataSource.setDatabaseName("db");
        dataSource.setUser("u");
        dataSource.setPassword("p");
        dataSource.setCompatible("9.0");
        dataSource.setLogLevel(1);
        dataSource.setProtocolVersion(2);
        dataSource.setPrepareThreshold(3);
        dataSource.setReceiveBufferSize(4);
        dataSource.setSendBufferSize(5);
        dataSource.setUnknownLength(6);
        dataSource.setSocketTimeout(7);
        dataSource.setSsl(true);
        dataSource.setSslfactory("factory");
        dataSource.setApplicationName("app");
        dataSource.setTcpKeepAlive(true);
        dataSource.setBinaryTransfer(true);
        dataSource.setBinaryTransferEnable("enabled");
        dataSource.setBinaryTransferDisable("disabled");
        dataSource.setPortNumber(PORT);

        var wrapped = (FullDataSource) dataSource.getWrapped();
        assertEquals("u", wrapped.getUser());
        assertEquals("p", wrapped.getPassword());
        assertEquals("9.0", wrapped.getCompatible());
        assertEquals(1, wrapped.getLogLevel());
        assertEquals(2, wrapped.getProtocolVersion());
        assertEquals(3, wrapped.getPrepareThreshold());
        assertEquals(4, wrapped.getReceiveBufferSize());
        assertEquals(5, wrapped.getSendBufferSize());
        assertEquals(6, wrapped.getUnknownLength());
        assertEquals(7, wrapped.getSocketTimeout());
        assertTrue(wrapped.getSsl());
        assertEquals("factory", wrapped.getSslfactory());
        assertEquals("app", wrapped.getApplicationName());
        assertTrue(wrapped.getTcpKeepAlive());
        assertTrue(wrapped.getBinaryTransfer());
        assertEquals("enabled", wrapped.getBinaryTransferEnable());
        assertEquals("disabled", wrapped.getBinaryTransferDisable());
        assertEquals(PORT, wrapped.getPortNumber());
        assertEquals("u", dataSource.getUser());
        assertEquals("p", dataSource.getPassword());
        assertEquals("9.0", dataSource.getCompatible());
        assertEquals(1, dataSource.getLogLevel());
        assertEquals(2, dataSource.getProtocolVersion());
        assertEquals(3, dataSource.getPrepareThreshold());
        assertEquals(6, dataSource.getUnknownLength());
        assertEquals(7, dataSource.getSocketTimeout());
        assertTrue(dataSource.getSsl());
        assertEquals("factory", dataSource.getSslfactory());
        assertEquals("app", dataSource.getApplicationName());
        assertTrue(dataSource.getTcpKeepAlive());
        assertTrue(dataSource.getBinaryTransfer());
        assertEquals("enabled", dataSource.getBinaryTransferEnable());
        assertEquals("disabled", dataSource.getBinaryTransferDisable());
        assertEquals(PORT, dataSource.getPortNumber());
    }

    @Test
    void commonPropertiesWhichWrappedDataSourceLacksAreIgnored() {
        var dataSource = new SwitchableCommonDataSource();
        dataSource.setConfigFile(CONFIG_FILE);
        dataSource.setUser("u");
        dataSource.setSsl(true);
        dataSource.setPortNumber(PORT);

        assertNull(dataSource.getUser());
        assertFalse(dataSource.getSsl());
        assertEquals(0, dataSource.getPortNumber());
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

    private static SwitchableCommonDataSource initializedFullDataSource(Path services) throws IOException {
        var registration = services.resolve("META-INF/services/" + PropertiesFileLoader.class.getName());
        Files.createDirectories(registration.getParent());
        Files.writeString(registration, FullDataSourceFileLoader.class.getName());
        var thread = Thread.currentThread();
        var originalClassLoader = thread.getContextClassLoader();

        try (var classLoader = new URLClassLoader(new URL[] { services.toUri().toURL() }, originalClassLoader)) {
            thread.setContextClassLoader(classLoader);
            var dataSource = new SwitchableCommonDataSource();
            dataSource.setConfigFile("full.properties");
            return dataSource;
        }
        finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    public static class FullDataSourceFileLoader implements PropertiesFileLoader {

        @Override
        public Map<String, String> loadFromFile(String fileName) {
            return Map.of("className", FullDataSource.class.getName());
        }

    }

    public static class FullDataSource extends RecordingDataSource {

        private String user;

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        private String compatible;

        public String getCompatible() {
            return compatible;
        }

        public void setCompatible(String compatible) {
            this.compatible = compatible;
        }

        private int logLevel;

        public int getLogLevel() {
            return logLevel;
        }

        public void setLogLevel(int logLevel) {
            this.logLevel = logLevel;
        }

        private int protocolVersion;

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public void setProtocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        private int prepareThreshold;

        public int getPrepareThreshold() {
            return prepareThreshold;
        }

        public void setPrepareThreshold(int prepareThreshold) {
            this.prepareThreshold = prepareThreshold;
        }

        private int receiveBufferSize;

        public int getReceiveBufferSize() {
            return receiveBufferSize;
        }

        public void setReceiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
        }

        private int sendBufferSize;

        public int getSendBufferSize() {
            return sendBufferSize;
        }

        public void setSendBufferSize(int sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
        }

        private int unknownLength;

        public int getUnknownLength() {
            return unknownLength;
        }

        public void setUnknownLength(int unknownLength) {
            this.unknownLength = unknownLength;
        }

        private int socketTimeout;

        public int getSocketTimeout() {
            return socketTimeout;
        }

        public void setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        private String sslfactory;

        public String getSslfactory() {
            return sslfactory;
        }

        public void setSslfactory(String sslfactory) {
            this.sslfactory = sslfactory;
        }

        private String applicationName;

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        private String binaryTransferEnable;

        public String getBinaryTransferEnable() {
            return binaryTransferEnable;
        }

        public void setBinaryTransferEnable(String binaryTransferEnable) {
            this.binaryTransferEnable = binaryTransferEnable;
        }

        private String binaryTransferDisable;

        public String getBinaryTransferDisable() {
            return binaryTransferDisable;
        }

        public void setBinaryTransferDisable(String binaryTransferDisable) {
            this.binaryTransferDisable = binaryTransferDisable;
        }

        private int portNumber;

        public int getPortNumber() {
            return portNumber;
        }

        public void setPortNumber(int portNumber) {
            this.portNumber = portNumber;
        }

        private boolean ssl;

        public boolean getSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        private boolean tcpKeepAlive;

        public boolean getTcpKeepAlive() {
            return tcpKeepAlive;
        }

        public void setTcpKeepAlive(boolean tcpKeepAlive) {
            this.tcpKeepAlive = tcpKeepAlive;
        }

        private boolean binaryTransfer;

        public boolean getBinaryTransfer() {
            return binaryTransfer;
        }

        public void setBinaryTransfer(boolean binaryTransfer) {
            this.binaryTransfer = binaryTransfer;
        }

    }

}
