/*
 * Copyright 2021 OmniFaces
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

public class CommonDataSourceWrapper implements CommonDataSource {

	private CommonDataSource commonDataSource;
	private Map<String, PropertyDescriptor> dataSourceProperties;
	private Set<String> commonProperties  = new HashSet<>(asList(
	        "serverName", "databaseName", "portNumber", 
	        "user", "password", "compatible", "logLevel",
	        "protocolVersion", "prepareThreshold", "receiveBufferSize",
	        "unknownLength", "socketTimeout", "ssl", "sslfactory",
	        "applicationName", "tcpKeepAlive", "binaryTransfer",
	        "binaryTransferEnable", "binaryTransferDisable"
	));

	public void initDataSource(CommonDataSource dataSource) {
		this.commonDataSource = dataSource;

		try {
			Map<String, PropertyDescriptor> mutableProperties = new HashMap<>();
			for (PropertyDescriptor propertyDescriptor : getBeanInfo(dataSource.getClass()).getPropertyDescriptors()) {
				mutableProperties.put(propertyDescriptor.getName(), propertyDescriptor);
			}

			dataSourceProperties = unmodifiableMap(mutableProperties);

		} catch (IntrospectionException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String name) {
	    
	    PropertyDescriptor property = dataSourceProperties.get(name);
	    
	    if ((property == null || property.getReadMethod() == null) && commonProperties.contains(name)) {
	        // Ignore fabricated properties that the actual data source doesn't have.
	        return null;
	    }
	    
		try {
			return (T) property.getReadMethod().invoke(commonDataSource);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	public void set(String name, Object value) {
	    
	    PropertyDescriptor property = dataSourceProperties.get(name);
        
        if ((property == null || property.getReadMethod() == null) && commonProperties.contains(name)) {
            // Ignore fabricated properties that the actual data source doesn't have.
            return;
        }
	    
		try {
			dataSourceProperties.get(name).getWriteMethod().invoke(commonDataSource, value);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	public void setWithConversion(String name, String value) {

		PropertyDescriptor property = dataSourceProperties.get(name);

		PropertyEditor editor = findEditor(property.getPropertyType());
		editor.setAsText(value);

		try {
			property.getWriteMethod().invoke(commonDataSource, editor.getValue());
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	public CommonDataSource getWrapped() {
		return commonDataSource;
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
		return commonDataSource.getParentLogger();
	}

	// ------------------------- Common properties -----------------------------------

	public String getServerName() {
		return get("serverName");
	}

	public void setServerName(String serverName) {
		set("serverName", serverName);
	}

	public String getDatabaseName() {
		return get("databaseName");
	}

	public void setDatabaseName(String databaseName) {
		set("databaseName", databaseName);
	}

	public int getPortNumber() {
		return get("portNumber");
	}

	public void setPortNumber(int portNumber) {
		set("portNumber", portNumber);
	}

	public void setPortNumber(Integer portNumber) {
		set("portNumber", portNumber);
	}

	public String getUser() {
		return get("user");
	}

	public void setUser(String user) {
		set("user", user);
	}

	public String getPassword() {
		return get("password");
	}

	public void setPassword(String password) {
		set("password", password);
	}

	public String getCompatible() {
		return get("compatible");
	}

	public void setCompatible(String compatible) {
		set("compatible", compatible);
	}

	public int getLogLevel() {
		return get("logLevel");
	}

	public void setLogLevel(int logLevel) {
		set("logLevel", logLevel);
	}

	public int getProtocolVersion() {
		return get("protocolVersion");
	}

	public void setProtocolVersion(int protocolVersion) {
		set("protocolVersion", protocolVersion);
	}
	
	public int getPrepareThreshold() {
        return get("prepareThreshold");
    }

	public void setPrepareThreshold(int prepareThreshold) {
		set("prepareThreshold", prepareThreshold);
	}

	public void setReceiveBufferSize(int receiveBufferSize) {
		set("receiveBufferSize", receiveBufferSize);
	}

	public void setSendBufferSize(int sendBufferSize) {
		set("sendBufferSize", sendBufferSize);
	}

	public void setUnknownLength(int unknownLength) {
		set("unknownLength", unknownLength);
	}

	public int getUnknownLength() {
		return get("unknownLength");
	}

	public void setSocketTimeout(int socketTimeout) {
		set("socketTimeout", socketTimeout);
	}

	public int getSocketTimeout() {
		return get("socketTimeout");
	}

	public void setSsl(boolean ssl) {
		set("ssl", ssl);
	}

	public boolean getSsl() {
		return get("ssl");
	}

	public void setSslfactory(String sslfactory) {
		set("sslfactory", sslfactory);
	}

	public String getSslfactory() {
		return get("sslfactory");
	}

	public void setApplicationName(String applicationName) {
		set("applicationName", applicationName);
	}

	public String getApplicationName() {
		return get("applicationName");
	}

	public void setTcpKeepAlive(boolean tcpKeepAlive) {
		set("tcpKeepAlive", tcpKeepAlive);
	}

	public boolean getTcpKeepAlive() {
		return get("tcpKeepAlive");
	}

	public void setBinaryTransfer(boolean binaryTransfer) {
		set("binaryTransfer", binaryTransfer);
	}

	public boolean getBinaryTransfer() {
		return get("binaryTransfer");
	}

	public void setBinaryTransferEnable(String binaryTransferEnable) {
		set("binaryTransferEnable", binaryTransferEnable);
	}

	public String getBinaryTransferEnable() {
		return get("binaryTransferEnable");
	}

	public void setBinaryTransferDisable(String binaryTransferDisable) {
		set("binaryTransferDisable", binaryTransferDisable);
	}

	public String getBinaryTransferDisable() {
		return get("binaryTransferDisable");
	}

}