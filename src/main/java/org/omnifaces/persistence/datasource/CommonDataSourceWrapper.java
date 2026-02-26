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

import static java.beans.Introspector.getBeanInfo;
import static java.beans.PropertyEditorManager.findEditor;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.sql.CommonDataSource;

/**
 * <p>
 * A wrapper around a {@link CommonDataSource} that delegates all property access to the wrapped data source via
 * reflection-based JavaBeans introspection. This allows configuring any data source implementation generically
 * without compile-time dependency on the specific driver class.
 * <p>
 * Properties are set and retrieved by name using the wrapped data source's getter and setter methods.
 * Unknown properties are silently ignored.
 *
 * @author Arjan Tijms
 * @since 1.0
 * @see SwitchableCommonDataSource
 * @see SwitchableXADataSource
 */
public class CommonDataSourceWrapper implements CommonDataSource {

    private CommonDataSource wrapped;
    private Map<String, PropertyDescriptor> dataSourceProperties;
    private Set<String> commonProperties  = new HashSet<>(asList(
            "serverName", "databaseName", "portNumber",
            "user", "password", "compatible", "logLevel",
            "protocolVersion", "prepareThreshold", "receiveBufferSize",
            "unknownLength", "socketTimeout", "ssl", "sslfactory",
            "applicationName", "tcpKeepAlive", "binaryTransfer",
            "binaryTransferEnable", "binaryTransferDisable"
    ));

    /**
     * Initializes the wrapper with the given data source.
     * This method performs introspection on the wrapped instance to discover its available properties.
     * @param wrapped The data source to wrap.
     * @throws IllegalStateException If introspection fails.
     */
    public void initDataSource(CommonDataSource wrapped) {
        this.wrapped = wrapped;

        try {
            Map<String, PropertyDescriptor> mutableProperties = new HashMap<>();
            for (PropertyDescriptor propertyDescriptor : getBeanInfo(getWrapped().getClass()).getPropertyDescriptors()) {
                mutableProperties.put(propertyDescriptor.getName(), propertyDescriptor);
            }

            dataSourceProperties = unmodifiableMap(mutableProperties);

        } catch (IntrospectionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Gets a property from the wrapped data source via reflection.
     * @param <T> The expected return type.
     * @param name The name of the property to get.
     * @return The property value.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name) {

        PropertyDescriptor property = dataSourceProperties.get(name);

        if (property == null || (property.getReadMethod() == null && commonProperties.contains(name))) {
            // Ignore fabricated properties that the actual data source doesn't have.
            return null;
        }

        try {
            return (T) property.getReadMethod().invoke(getWrapped());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets a property on the wrapped data source via reflection.
     * @param name The name of the property to set.
     * @param value The value to set.
     */
    public void set(String name, Object value) {

        PropertyDescriptor property = dataSourceProperties.get(name);

        if (property == null || (property.getReadMethod() == null && commonProperties.contains(name))) {
            // Ignore fabricated properties that the actual data source doesn't have.
            return;
        }

        try {
            dataSourceProperties.get(name).getWriteMethod().invoke(getWrapped(), value);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets a property on the wrapped data source by converting the given String value to the target property type.
     * @param name The name of the property to set.
     * @param value The String value to convert and set.
     */
    public void setWithConversion(String name, String value) {

        PropertyDescriptor property = dataSourceProperties.get(name);

        PropertyEditor editor = findEditor(property.getPropertyType());
        editor.setAsText(value);

        try {
            property.getWriteMethod().invoke(getWrapped(), editor.getValue());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the wrapped {@link CommonDataSource} instance.
     * @return The wrapped data source.
     */
    public CommonDataSource getWrapped() {
        return wrapped;
    }

    // ------------------------- CommonDataSource-----------------------------------

    @Override
    public java.io.PrintWriter getLogWriter() throws SQLException {
        return get("loginWriter");
    }

    @Override
    public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        set("loginWriter", out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        set("loginTimeout", seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return get("loginTimeout");
    }

    // ------------------------- CommonDataSource JDBC 4.1 -----------------------------------

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return getWrapped().getParentLogger();
    }

    // ------------------------- Common properties -----------------------------------

    /**
     * Returns the server name.
     * @return The server name.
     */
    public String getServerName() {
        return get("serverName");
    }

    /**
     * Sets the server name.
     * @param serverName The server name.
     */
    public void setServerName(String serverName) {
        set("serverName", serverName);
    }

    /**
     * Returns the database name.
     * @return The database name.
     */
    public String getDatabaseName() {
        return get("databaseName");
    }

    /**
     * Sets the database name.
     * @param databaseName The database name.
     */
    public void setDatabaseName(String databaseName) {
        set("databaseName", databaseName);
    }

    /**
     * Returns the port number.
     * @return The port number.
     */
    public int getPortNumber() {
        return get("portNumber");
    }

    /**
     * Sets the port number.
     * @param portNumber The port number.
     */
    public void setPortNumber(int portNumber) {
        set("portNumber", portNumber);
    }

    /**
     * Sets the port number.
     * @param portNumber The port number.
     */
    public void setPortNumber(Integer portNumber) {
        set("portNumber", portNumber);
    }

    /**
     * Returns the user.
     * @return The user.
     */
    public String getUser() {
        return get("user");
    }

    /**
     * Sets the user.
     * @param user The user.
     */
    public void setUser(String user) {
        set("user", user);
    }

    /**
     * Returns the password.
     * @return The password.
     */
    public String getPassword() {
        return get("password");
    }

    /**
     * Sets the password.
     * @param password The password.
     */
    public void setPassword(String password) {
        set("password", password);
    }

    /**
     * Returns the compatible version.
     * @return The compatible version.
     */
    public String getCompatible() {
        return get("compatible");
    }

    /**
     * Sets the compatible version.
     * @param compatible The compatible version.
     */
    public void setCompatible(String compatible) {
        set("compatible", compatible);
    }

    /**
     * Returns the log level.
     * @return The log level.
     */
    public int getLogLevel() {
        return get("logLevel");
    }

    /**
     * Sets the log level.
     * @param logLevel The log level.
     */
    public void setLogLevel(int logLevel) {
        set("logLevel", logLevel);
    }

    /**
     * Returns the protocol version.
     * @return The protocol version.
     */
    public int getProtocolVersion() {
        return get("protocolVersion");
    }

    /**
     * Sets the protocol version.
     * @param protocolVersion The protocol version.
     */
    public void setProtocolVersion(int protocolVersion) {
        set("protocolVersion", protocolVersion);
    }

    /**
     * Returns the prepare threshold.
     * @return The prepare threshold.
     */
    public int getPrepareThreshold() {
        return get("prepareThreshold");
    }

    /**
     * Sets the prepare threshold.
     * @param prepareThreshold The prepare threshold.
     */
    public void setPrepareThreshold(int prepareThreshold) {
        set("prepareThreshold", prepareThreshold);
    }

    /**
     * Sets the receive buffer size.
     * @param receiveBufferSize The receive buffer size.
     */
    public void setReceiveBufferSize(int receiveBufferSize) {
        set("receiveBufferSize", receiveBufferSize);
    }

    /**
     * Sets the send buffer size.
     * @param sendBufferSize The send buffer size.
     */
    public void setSendBufferSize(int sendBufferSize) {
        set("sendBufferSize", sendBufferSize);
    }

    /**
     * Sets the unknown length.
     * @param unknownLength The unknown length.
     */
    public void setUnknownLength(int unknownLength) {
        set("unknownLength", unknownLength);
    }

    /**
     * Returns the unknown length.
     * @return The unknown length.
     */
    public int getUnknownLength() {
        return get("unknownLength");
    }

    /**
     * Sets the socket timeout.
     * @param socketTimeout The socket timeout.
     */
    public void setSocketTimeout(int socketTimeout) {
        set("socketTimeout", socketTimeout);
    }

    /**
     * Returns the socket timeout.
     * @return The socket timeout.
     */
    public int getSocketTimeout() {
        return get("socketTimeout");
    }

    /**
     * Sets whether SSL is enabled.
     * @param ssl True if enabled, false otherwise.
     */
    public void setSsl(boolean ssl) {
        set("ssl", ssl);
    }

    /**
     * Returns whether SSL is enabled.
     * @return True if enabled, false otherwise.
     */
    public boolean getSsl() {
        return get("ssl");
    }

    /**
     * Sets the SSL factory.
     * @param sslfactory The SSL factory class name.
     */
    public void setSslfactory(String sslfactory) {
        set("sslfactory", sslfactory);
    }

    /**
     * Returns the SSL factory.
     * @return The SSL factory.
     */
    public String getSslfactory() {
        return get("sslfactory");
    }

    /**
     * Sets the application name.
     * @param applicationName The application name.
     */
    public void setApplicationName(String applicationName) {
        set("applicationName", applicationName);
    }

    /**
     * Returns the application name.
     * @return The application name.
     */
    public String getApplicationName() {
        return get("applicationName");
    }

    /**
     * Sets whether TCP keep alive is enabled.
     * @param tcpKeepAlive True if enabled, false otherwise.
     */
    public void setTcpKeepAlive(boolean tcpKeepAlive) {
        set("tcpKeepAlive", tcpKeepAlive);
    }

    /**
     * Returns whether TCP keep alive is enabled.
     * @return True if enabled, false otherwise.
     */
    public boolean getTcpKeepAlive() {
        return get("tcpKeepAlive");
    }

    /**
     * Sets whether binary transfer is enabled.
     * @param binaryTransfer True if enabled, false otherwise.
     */
    public void setBinaryTransfer(boolean binaryTransfer) {
        set("binaryTransfer", binaryTransfer);
    }

    /**
     * Returns whether binary transfer is enabled.
     * @return True if enabled, false otherwise.
     */
    public boolean getBinaryTransfer() {
        return get("binaryTransfer");
    }

    /**
     * Sets the binary transfer enable setting.
     * @param binaryTransferEnable The binary transfer enable setting.
     */
    public void setBinaryTransferEnable(String binaryTransferEnable) {
        set("binaryTransferEnable", binaryTransferEnable);
    }

    /**
     * Returns the binary transfer enable setting
     * @return The binary transfer enable setting.
     */
    public String getBinaryTransferEnable() {
        return get("binaryTransferEnable");
    }

    /**
     * Sets the binary transfer disable setting.
     * @param binaryTransferDisable The binary transfer disable setting.
     */
    public void setBinaryTransferDisable(String binaryTransferDisable) {
        set("binaryTransferDisable", binaryTransferDisable);
    }

    /**
     * Returns the binary transfer disable setting.
     * @return The binary transfer disable setting.
     */
    public String getBinaryTransferDisable() {
        return get("binaryTransferDisable");
    }

}