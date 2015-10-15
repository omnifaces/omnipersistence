package org.omnifaces.persistence.datasource;

import java.util.Map;

public interface PropertiesFileLoader {

	Map<String, String> loadFromFile(String fileName);
	
}
