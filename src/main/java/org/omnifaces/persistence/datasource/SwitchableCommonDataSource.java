/*
 * Copyright 2018 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.persistence.datasource;

import static org.omnifaces.utils.properties.PropertiesUtils.loadPropertiesFromClasspath;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;

import javax.sql.CommonDataSource;

public class SwitchableCommonDataSource extends CommonDataSourceWrapper {

	private boolean init;
	private String configFile;
	private Map<String, Object> tempValues = new HashMap<>();

	@Override
	public void set(String name, Object value) {
		if (init) {
			super.set(name, value);
		} else {
			tempValues.put(name, value);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String name) {
		if (init) {
			return super.get(name);
		} else {
			return (T) tempValues.get(name);
		}
	}

	public String getConfigFile() {
		return configFile;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;

		// Nasty, but there's not an @PostConstruct equivalent on a DataSource that's called
		// when all properties have been set.
		doInit();
	}

	public void doInit() {

		// Get the properties that were defined separately from the @DataSourceDefinition/data-source element
		
		ServiceLoader<PropertiesFileLoader> loader = ServiceLoader.load(PropertiesFileLoader.class);
		if (!loader.iterator().hasNext()) {
			loader = ServiceLoader.load(PropertiesFileLoader.class, SwitchableCommonDataSource.class.getClassLoader());
		}
		
		Map<String, String> properties = new HashMap<>();
		
		if (!loader.iterator().hasNext()) {
			// No service loader was specified for loading the configfile.
			// Try the fallback default location of META-INF on the classpath
			properties.putAll(loadPropertiesFromClasspath("META-INF/" + configFile));
			
		} else {
			for (PropertiesFileLoader propertiesFileLoader : loader) {
				properties.putAll(propertiesFileLoader.loadFromFile(configFile));
			}
		}

		// Get & check the most important property; the class name of the data source that we wrap.
		String className = properties.get("className");
		if (className == null) {
			throw new IllegalStateException("Required parameter 'className' missing.");
		}

		initDataSource(newInstance(className));

		// Set the properties on the wrapped data source that were already set on this class before doInit()
		// was possible.
		for (Entry<String, Object> property : tempValues.entrySet()) {
			super.set(property.getKey(), property.getValue());
		}

		// Set the properties on the wrapped data source that were loaded from the external file.
		for (Entry<String, String> property : properties.entrySet()) {
			if (!property.getKey().equals("className")) {
				setWithConversion(property.getKey(), property.getValue());
			}
		}

		// After this properties will be set directly on the wrapped data source instance.
		init = true;
	}

	private CommonDataSource newInstance(String className) {
		try {
			return (CommonDataSource) Class.forName(className).newInstance();
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

}
