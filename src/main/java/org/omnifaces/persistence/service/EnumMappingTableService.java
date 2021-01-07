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
package org.omnifaces.persistence.service;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.ejb.TransactionManagementType.BEAN;
import static javax.persistence.EnumType.ORDINAL;
import static org.omnifaces.persistence.model.EnumMapping.CODE_FIELD_NAME;
import static org.omnifaces.persistence.model.EnumMapping.ID_FIELD_NAME;
import static org.omnifaces.persistence.model.EnumMappingTable.DEFAULT_ENUM_HISTORY_TABLE_POSTFIX;
import static org.omnifaces.persistence.model.EnumMappingTable.DEFAULT_ENUM_TABLE_POSTFIX;
import static org.omnifaces.persistence.model.EnumMappingTable.MappingType.NO_ACTION;
import static org.omnifaces.utils.reflect.Reflections.accessField;
import static org.omnifaces.utils.reflect.Reflections.findField;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedEnumFields;
import static org.omnifaces.utils.reflect.Reflections.modifyField;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EnumType;
import javax.persistence.PersistenceUnit;
import javax.persistence.metamodel.EntityType;
import javax.transaction.UserTransaction;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.EnumMapping;
import org.omnifaces.persistence.model.EnumMappingTable;

/**
 * Auxiliary class that allows fine-tuning of {@link EnumMappingTable} enums.
 * Basing on the settings it implements two-side correspondence between java
 * enum classes and database table representations.
 * <h3>Logging</h3>
 * <p>
 * {@link EnumMappingTableService} uses JULI {@link Logger} for logging.
 * <ul>
 * <li>{@link Level#INFO} will log both successful modification of java enum or
 * database table representations basing on {@link EnumMapping}.
 * <li>{@link Level#WARNING} will log errors encountered during modification of
 * java enum or database table representations. It will also log the
 * inconsistency between java enum and database table representations that one
 * must treat accordingly, best of which should be to rewrite the java code or
 * modify the database tables in a way that such warnings are not generated and
 * the application will have one-to-one correspondence between java enum and
 * database table representations right from the start.
 * </ul>
 *
 * @see EnumMapping
 * @see EnumMappingTable
 * @author Sergey Kuntsel
 */
@Singleton
@Startup
@TransactionManagement(BEAN)
public class EnumMappingTableService {

        private static final Logger logger = Logger.getLogger(EnumMappingTableService.class.getName());

        private static final String LOG_WARNING_INVALID_ENUM_FIELD_NAME = "Field name %s specified within the annotation was not found on enum %s";
        private static final String LOG_WARNING_INVALID_ENUM_FIELD_TYPE = "Declared enum %s contains wrong type of field %s: expected %s, got %s";
        private static final String LOG_WARNING_UNMODIFIABLE_ENUM_FIELD = "Field name %s could not be used to modify enum %s";

        private static final String LOG_WARNING_CANNOT_ACCESS_ENUM_CONSTRUCTOR = "Cannot get access to create new instance method of enum %s";
        private static final String LOG_WARNING_CANNOT_INSTANTIATE_NEW_ENUM = "New constants for enum %s could not be instantiated";
        private static final String LOG_WARNING_CANNOT_MODIFY_ENUM_DATA = "Data for enum %s could not be modified: %s could not be performed";

        private static final String LOG_WARNING_ENUM_MAPPING_TABLE_CONNECTION_ERROR = "Couldn't connect to the target table %s: either table with name %s doesn't exist, or column name%s %s %s incorrect";
        private static final String LOG_WARNING_ENUM_MAPPING_TABLE_CREATION_ERROR = "Couldn't create the target table %s: check table names for possible collisions";
        private static final String LOG_WARNING_ENUM_MAPPING_TABLE_READ_ERROR = "Couldn't read from the target table %s: check column type%s (%s) for column name%s (%s)";
        private static final String LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR = "Couldn't modify the target table %s: %s was not performed successfully (%d %s actions needed%s)";

        private static final String LOG_WARNING_DIFFERENCE_IN_ENUM_AND_TABLE_DATA = "Difference in enum %s and table data detected: %s %s in %s%s";

        private static final String LOG_INFO_ENUM_MAPPING_TABLE_MODIFIED = "Data for table %s was modified basing on enum %s data: %d number of %s was performed";
        private static final String LOG_INFO_ENUM_DATA_MODIFIED = "Data for enum %s was modified: %d number of %s was performed";
    	private static final String LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING = "Enum mapping for enum %s: was %smodified";
    	private static final String LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING_TABLE = "Enum mapping table for enum %s: was %smodified";

    	private static final Map<Class<? extends Enum<?>>, Boolean> MODIFIED_ENUM_MAPPINGS = new ConcurrentHashMap<>();
    	private static final Map<Class<? extends Enum<?>>, Boolean> MODIFIED_ENUM_TABLE_MAPPINGS = new ConcurrentHashMap<>();

        @PersistenceUnit
        private EntityManagerFactory emf;

        @Resource
        UserTransaction ut;

        @PostConstruct
        public void init() {
        	if (Provider.of(emf.createEntityManager()) == Provider.ECLIPSELINK) {
        		return; // Doesn't work at all with EclipseLink.
        	}

        	emf.getMetamodel().getEntities().stream()
        		.map(EntityType::getJavaType)
        		.filter(BaseEntity.class::isAssignableFrom)
        		.forEach(this::computeModifiedEnumMapping);
        }

    	private void computeModifiedEnumMapping(Class<?> entityType) {
    		listAnnotatedEnumFields(entityType, javax.persistence.Enumerated.class).stream()
    			.filter(enumeratedType -> enumeratedType.isAnnotationPresent(EnumMapping.class) && !MODIFIED_ENUM_MAPPINGS.containsKey(enumeratedType))
    			.peek(enumeratedType -> {
    				boolean modified = EnumMappingTableService.modifyEnumMapping(enumeratedType);
    				logger.log(INFO, () -> format(LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING, enumeratedType, modified ? "" : "not "));
    				MODIFIED_ENUM_MAPPINGS.put(enumeratedType, modified);
    			})
    			.filter(enumeratedType -> !MODIFIED_ENUM_TABLE_MAPPINGS.containsKey(enumeratedType) && enumeratedType.getAnnotation(EnumMapping.class).enumMappingTable().mappingType() != NO_ACTION)
    			.forEach(enumeratedType -> {
    				boolean modified = modifyEnumMappingTable(enumeratedType);
    				logger.log(INFO, () -> format(LOG_INFO_COMPUTED_MODIFIED_ENUM_MAPPING_TABLE, enumeratedType, modified ? "" : "not "));
    				MODIFIED_ENUM_TABLE_MAPPINGS.put(enumeratedType, modified);
    			});
    	}

        private static class EnumData {

                private static class EnumDataEntry {

                        Integer id;
                        String code;
                        EnumData enumData;

                        public EnumDataEntry(Integer id, String code, EnumData enumData) {
                                this.id = id;
                                this.code = code;
                                this.enumData = enumData;
                        }

                        public Integer getId() {
                                return id;
                        }

                        public String getCode() {
                                return code;
                        }

                        public boolean compareComplementary(EnumDataEntry ede) {
                                return Objects.equals(enumData.ordinal ? code : id, ede.enumData.ordinal ? ede.code : ede.id);
                        }

                        @Override
                        public int hashCode() {
                                int hash = 7;
                                hash = enumData.ordinal ? 89 * hash + Objects.hashCode(this.id)
                                        : 89 * hash + Objects.hashCode(this.code);
                                return hash;
                        }

                        @Override
                        public boolean equals(Object obj) {
                                if (this == obj) {
                                        return true;
                                }
                                if (obj == null || getClass() != obj.getClass()) {
                                        return false;
                                }
                                final EnumDataEntry other = (EnumDataEntry) obj;
                                return enumData.ordinal ? Objects.equals(this.id, other.id)
                                        : Objects.equals(this.code, other.code);
                        }

                        @Override
                        public String toString() {
                                return "{" + "id = " + id + ", code = " + code + "}";
                        }

                }

                private Set<EnumDataEntry> data = new LinkedHashSet<>();
                private boolean ordinal;
                private boolean oneField;
                private Class<? extends Enum<?>> enumClass;

                public EnumData(boolean ordinal, boolean oneField, Class<? extends Enum<?>> enumClass) {
                        this.ordinal = ordinal;
                        this.oneField = oneField;
                        this.enumClass = enumClass;
                }

                public void addDataElement(Integer id, String code) {
                        addDataElementInternal(id, code);
                }

                public void addDataElement(Integer id) {
                        addDataElementInternal(id, null);
                }

                public void addDataElement(String code) {
                        addDataElementInternal(null, code);
                }

                private void addDataElementInternal(Integer id, String code) {
                        data.add(new EnumDataEntry(id, code, this));
                }

                public static void compareEnumData(EnumData base, EnumData target,
                        Set<EnumDataEntry> common, Set<EnumDataEntry> absentInBase,
                        Set<EnumDataEntry> absentInTarget, Map<EnumDataEntry, EnumDataEntry> different) {
                        Set<EnumDataEntry> baseEnumData = base.data;
                        Set<EnumDataEntry> targetEnumData = target.data;
                        common.clear();
                        absentInBase.clear();
                        absentInTarget.clear();
                        different.clear();
                        absentInBase.addAll(targetEnumData);

                        int tries = targetEnumData.size();
                        for (Iterator<EnumDataEntry> baseIter = baseEnumData.iterator(); baseIter.hasNext();) {
                                EnumDataEntry baseData = baseIter.next();
                                boolean found = false;
                                if (tries != 0) {
                                        for (Iterator<EnumDataEntry> targetIter = absentInBase.iterator(); targetIter.hasNext();) {
                                                EnumDataEntry targetData = targetIter.next();
                                                if (Objects.equals(baseData, targetData)) {
                                                        found = true;
                                                        tries--;
                                                        if (base.oneField) {
                                                                common.add(baseData);
                                                        } else {
                                                                if (baseData.compareComplementary(targetData)) {
                                                                        common.add(baseData);
                                                                } else {
                                                                        different.put(baseData, targetData);
                                                                }
                                                        }
                                                        targetIter.remove();
                                                        break;
                                                }
                                        }
                                }
                                if (!found) {
                                        absentInTarget.add(baseData);
                                }
                        }
                }

        }

        private boolean modifyEnumMappingTable(Class<? extends Enum<?>> enumeratedType) {
                // Read annotation information.
                EnumMapping mapping = enumeratedType.getAnnotation(EnumMapping.class);
                boolean ordinal = mapping.type() == EnumType.ORDINAL;
                String fieldName = mapping.fieldName();
                EnumMappingTable mappingTable = mapping.enumMappingTable();
                boolean enumPrecedence = mappingTable.mappingType() == EnumMappingTable.MappingType.ENUM;
                boolean doDeletes = mappingTable.deleteType() != EnumMappingTable.DeleteAction.NO_ACTION;
                boolean doSoftDeletes = mappingTable.deleteType() == EnumMappingTable.DeleteAction.SOFT_DELETE;
                boolean doInserts = mappingTable.doInserts();

                // Define proper enum fields.
                boolean oneFieldMapping = mappingTable.oneFieldMapping();
                String idFieldName = mappingTable.ordinalFieldName();
                String codeFieldName = mappingTable.stringFieldName();
                Optional<Field> idEnumFieldOptional = ordinal ? findField(enumeratedType, fieldName) : oneFieldMapping
                        ? Optional.empty() : findField(enumeratedType, idFieldName);
                Optional<Field> codeEnumFieldOptional = !ordinal ? findField(enumeratedType, fieldName) : oneFieldMapping
                        ? Optional.empty() : findField(enumeratedType, codeFieldName);
                Optional<Field> secondaryEnumFieldOptional = oneFieldMapping ? Optional.empty() : ordinal ? codeEnumFieldOptional : idEnumFieldOptional;

                // Define proper database table and column names.
                String idEnumColumn = ordinal ? mappingTable.ordinalColumnName() : oneFieldMapping
                        ? "" : mappingTable.ordinalColumnName();
                String codeEnumColumn = !ordinal ? mappingTable.stringColumnName() : oneFieldMapping
                        ? "" : mappingTable.stringColumnName();
                String tableName = mappingTable.tableName();
                String enumTable = "".equals(tableName) ? toSnakeCase(enumeratedType.getSimpleName()) + DEFAULT_ENUM_TABLE_POSTFIX : tableName;
                String deletedColumn = (doSoftDeletes && !enumPrecedence) ? mappingTable.deletedColumnName() : "";
                String historyTable = (doSoftDeletes && enumPrecedence) ? enumTable + DEFAULT_ENUM_HISTORY_TABLE_POSTFIX : "";

                // Do the update job.
                EntityManager entityManager = emf.createEntityManager();
                try {
                        // Check database tables for existence.
                        boolean existsTable = false;
                        boolean existsHistoryTable = false;
                        try {
                                String countTableQuery = "SELECT "
                                        + ("".equals(idEnumColumn) ? "" : "COUNT(et." + idEnumColumn + ")")
                                        + ("".equals(codeEnumColumn) ? "" : (oneFieldMapping ? "" : ", ") + "COUNT(et." + codeEnumColumn + ")")
                                        + ("".equals(deletedColumn) ? "" : ", COUNT(et." + deletedColumn + ")")
                                        + " FROM " + enumTable + " et;";// select count(et.id), count(et.code), count(et.deleted) from enum_table as et

                                entityManager.createNativeQuery(countTableQuery).getSingleResult();
                                existsTable = true;
                        } catch (Exception ignore) {
                                // Table doesn't exist.
                                existsTable = false;
                        }

                        // Check database history table for existence if necessary.
                        if (!"".equals(historyTable)) {
                                try {
                                        String countHistoryTableQuery = "SELECT "
                                                + ("".equals(idEnumColumn) ? "" : "COUNT(et." + idEnumColumn + ")")
                                                + ("".equals(codeEnumColumn) ? "" : (oneFieldMapping ? "" : ", ") + "COUNT(et." + codeEnumColumn + ")")
                                                + " FROM " + historyTable + " et;";// select count(et.id), count(et.code) from enum_table as et

                                        entityManager.createNativeQuery(countHistoryTableQuery).getSingleResult();
                                        existsHistoryTable = true;
                                } catch (Exception ignore) {
                                        // History table doesn't exist.
                                        existsHistoryTable = false;
                                }
                        }

                        // Create database tables if necessary.
                        boolean createdTable = false;
                        if (!existsTable) {
                                if (!enumPrecedence) {
                                        logger.log(WARNING, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_CONNECTION_ERROR, enumTable, enumTable,
                                                 oneFieldMapping ? "" : "s",
                                                 oneFieldMapping ? ordinal ? idEnumColumn : codeEnumColumn : idEnumColumn + ", " + codeEnumColumn,
                                                 oneFieldMapping ? "is" : "are"));
                                        return false;
                                }

                                String createTableQuery = "CREATE TABLE " + enumTable + " ("
                                        + ("".equals(idEnumColumn) ? "" : idEnumColumn + " INT NOT NULL, ")
                                        + ("".equals(codeEnumColumn) ? "" : codeEnumColumn + " VARCHAR(32) NOT NULL, ")
                                        + ("PRIMARY KEY (" + (ordinal ? idEnumColumn : codeEnumColumn) + ")")
                                        + (oneFieldMapping ? "" : (", CONSTRAINT " + enumTable + "_" + (ordinal ? codeEnumColumn : idEnumColumn) + "_UNIQUE UNIQUE (" + (ordinal ? codeEnumColumn : idEnumColumn) + ")"))
                                        + ");";// create table enum_table (id int not null, code varchar(32) not null, primary key(id), CONSTRAINT enum_table_code_UNIQUE UNIQUE (code));

                                try {
                                        ut.begin();
                                        entityManager.joinTransaction();
                                        entityManager.createNativeQuery(createTableQuery).executeUpdate();
                                        ut.commit();
                                        createdTable = true;
                                } catch (Exception ex) {
                                        try {
                                                ut.rollback();
                                        } catch (Exception ignore) {
                                        }
                                        createdTable = false;
                                        logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_CREATION_ERROR, enumTable));
                                        return false;
                                }
                        }

                        if (!existsHistoryTable && !"".equals(historyTable)) {
                                String createHistoryTableQuery = "CREATE TABLE " + historyTable + " ("
                                        + ("".equals(idEnumColumn) ? "" : idEnumColumn + " INT NOT NULL, ")
                                        + ("".equals(codeEnumColumn) ? "" : codeEnumColumn + " VARCHAR(32) NOT NULL, ")
                                        + ("PRIMARY KEY (" + (oneFieldMapping ? (ordinal ? idEnumColumn : codeEnumColumn) : idEnumColumn + ", " + codeEnumColumn) + ")")
                                        + ");";// create table enum_history_table (id int not null, code varchar(32) not null, primary key(id, code));

                                try {
                                        ut.begin();
                                        entityManager.joinTransaction();
                                        entityManager.createNativeQuery(createHistoryTableQuery).executeUpdate();
                                        ut.commit();
                                } catch (Exception ex) {
                                        try {
                                                ut.rollback();
                                        } catch (Exception ignore) {
                                        }
                                        logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_CREATION_ERROR, historyTable));
                                        return false;
                                }
                        }

                        // Read table data.
                        EnumData databaseData = new EnumData(ordinal, oneFieldMapping, enumeratedType);
                        if (existsTable && !createdTable) {
                                try {
                                        String getDatabaseDataQuery = "SELECT " + (ordinal ? "et." + idEnumColumn : "et." + codeEnumColumn)
                                                + (oneFieldMapping ? "" : (ordinal ? ", et." + codeEnumColumn : ", et." + idEnumColumn))
                                                + " FROM " + enumTable + " et"
                                                + ("".equals(deletedColumn) ? ";" : " WHERE et." + deletedColumn + " = 0;");// select et.id, et.code from enum_table et where et.deleted = 0;
                                        List<Object> databaseValues = entityManager.createNativeQuery(getDatabaseDataQuery).getResultList();

                                        for (Object object : databaseValues) {
                                                if (oneFieldMapping) {
                                                        if (ordinal) {
                                                                databaseData.addDataElement((Integer) object);
                                                        } else {
                                                                databaseData.addDataElement((String) object);
                                                        }
                                                } else {
                                                        Object[] array = (Object[]) object;
                                                        Integer integer = (Integer) (ordinal ? array[0] : array[1]);
                                                        String string = (String) (ordinal ? array[1] : array[0]);
                                                        databaseData.addDataElement(integer, string);
                                                }
                                        }
                                } catch (Exception ex) {
                                        logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_READ_ERROR, enumTable,
                                                 oneFieldMapping ? "" : "s",
                                                 oneFieldMapping ? ordinal ? "Integer" : "String" : "Integer, String",
                                                 oneFieldMapping ? "" : "s",
                                                 oneFieldMapping ? ordinal ? idEnumColumn : codeEnumColumn : idEnumColumn + ", " + codeEnumColumn));
                                        return false;
                                }
                        }

                        // Read enum data.
                        EnumData enumData = new EnumData(ordinal, oneFieldMapping, enumeratedType);
                        Field secondaryEnumField = secondaryEnumFieldOptional.orElse(null);
                        if (!oneFieldMapping) {
                                if (!secondaryEnumFieldOptional.isPresent()) {
                                        logger.log(WARNING, () -> format(LOG_WARNING_INVALID_ENUM_FIELD_NAME, ordinal ? codeFieldName : idFieldName, enumeratedType));
                                        return false;
                                }

                                boolean validSecondaryFieldType = (ordinal ? secondaryEnumField.getType() == String.class : (secondaryEnumField.getType() == Integer.class || secondaryEnumField.getType() == int.class));
                                if (!validSecondaryFieldType) {
                                        logger.log(WARNING, () -> format(LOG_WARNING_INVALID_ENUM_FIELD_TYPE, enumeratedType, secondaryEnumField.getName(), ordinal ? "String" : "Integer", secondaryEnumField.getType()));
                                        return false;
                                }
                        }

                        Arrays.asList(enumeratedType.getEnumConstants()).stream()
                                .filter(Objects::nonNull)
                                .forEach(enumConstant -> {
                                        int id = enumConstant.ordinal();
                                        String code = enumConstant.name();
                                        if (oneFieldMapping) {
                                                // Internal enum values are already modified, so we don't need to use fields.
                                                if (ordinal) {
                                                        enumData.addDataElement(id);
                                                } else {
                                                        enumData.addDataElement(code);
                                                }
                                        } else {
                                                // We need to read the other field in case of two-field mapping.
                                                Field field = secondaryEnumField;
                                                Object value = accessField(enumConstant, field);
                                                if (ordinal) {
                                                        code = (String) value;
                                                } else {
                                                        id = (int) value;
                                                }
                                                enumData.addDataElement(id, code);
                                        }
                                });

                        // Compare two datasets.
                        Set<EnumData.EnumDataEntry> common = new LinkedHashSet<>();
                        Set<EnumData.EnumDataEntry> absentInBase = new LinkedHashSet<>();
                        Set<EnumData.EnumDataEntry> absentInTarget = new LinkedHashSet<>();
                        Map<EnumData.EnumDataEntry, EnumData.EnumDataEntry> different = new LinkedHashMap<>();
                        EnumData.compareEnumData(enumData, databaseData, common, absentInBase, absentInTarget, different);

                        // Issue warnings in case differences between enum and table are detected.
                        if (!absentInBase.isEmpty()) {
                                logger.log(WARNING, () -> format(LOG_WARNING_DIFFERENCE_IN_ENUM_AND_TABLE_DATA, enumeratedType, absentInBase, "absent", "enum", ""));
                        }
                        if (!absentInTarget.isEmpty()) {
                                logger.log(WARNING, () -> format(LOG_WARNING_DIFFERENCE_IN_ENUM_AND_TABLE_DATA, enumeratedType, absentInTarget, "absent", "table", ""));
                        }
                        if (!different.isEmpty()) {
                                logger.log(WARNING, () -> format(LOG_WARNING_DIFFERENCE_IN_ENUM_AND_TABLE_DATA, enumeratedType, different.keySet(), "different", "enum, values in table are ", different.values()));
                        }

                        // Act according to the collected data.
                        boolean mustUpdateSecondaryField = false; // If the other internal field must be updated as well.
                        List<Enum<?>> newValuesField = null; // List of new enum constants in case table takes precedence.
                        int numDeletes = 0, numInserts = 0; // Placeholder for enum modifications.

                        if (enumPrecedence) {
                                // Update table rows.
                                if (doDeletes && !absentInBase.isEmpty()) {
                                        // Delete database table rows.
                                        StringBuilder deleteQuery = new StringBuilder();
                                        for (Iterator<EnumData.EnumDataEntry> iter = absentInBase.iterator(); iter.hasNext();) {
                                                EnumData.EnumDataEntry ede = iter.next();
                                                deleteQuery.append("DELETE FROM " + enumTable + " WHERE "
                                                        + (ordinal ? idEnumColumn : codeEnumColumn) + " = "
                                                        + (ordinal ? ede.getId() : "'" + ede.getCode() + "'")
                                                        + (iter.hasNext() ? "; " : ";"));// delete from enum_table where id = 1;
                                        }

                                        try {
                                                ut.begin();
                                                entityManager.joinTransaction();
                                                int deletes = numDeletes = entityManager.createNativeQuery(deleteQuery.toString()).executeUpdate();
                                                logger.log(INFO, () -> format(LOG_INFO_ENUM_MAPPING_TABLE_MODIFIED, enumTable, enumeratedType.getSimpleName(), deletes, "deletes"));
                                                ut.commit();
                                        } catch (Exception ex) {
                                                try {
                                                        ut.rollback();
                                                } catch (Exception ignore) {
                                                }
                                                logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, enumTable, "delete", absentInBase.size(), "delete", ""));
                                                return false;
                                        }

                                        if (doSoftDeletes) {
                                                // Insert new data into history table.
                                                String insertHistoryQueryBase = "INSERT INTO " + historyTable + " ("
                                                        + (ordinal ? idEnumColumn : codeEnumColumn)
                                                        + (oneFieldMapping ? "" : (ordinal ? ", " + codeEnumColumn : ", " + idEnumColumn)) + ")"
                                                        + " VALUES ";// insert into enum_table_history (id, code) values (1, 'code');
                                                int inserts = 0;
                                                for (Iterator<EnumData.EnumDataEntry> iter = absentInBase.iterator(); iter.hasNext();) {
                                                        EnumData.EnumDataEntry ede = iter.next();
                                                        String insertHistoryQuery = insertHistoryQueryBase + "("
                                                                + (ordinal ? ede.getId() : "'" + ede.getCode() + "'")
                                                                + (oneFieldMapping ? "" : (ordinal ? ", " + "'" + ede.getCode() + "'" : ", " + ede.getId())) + ");";

                                                        try {
                                                                ut.begin();
                                                                entityManager.joinTransaction();
                                                                inserts += entityManager.createNativeQuery(insertHistoryQuery).executeUpdate();
                                                                ut.commit();
                                                        } catch (Exception ex) {
                                                                try {
                                                                        ut.rollback();
                                                                } catch (Exception ignore) {
                                                                }
                                                                logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, historyTable, "insert", 1, "insert", ", possible duplicate entry with key "
                                                                        + (ordinal ? ede.getId() : "'" + ede.getCode() + "'")));
                                                        }
                                                }

                                                int number = inserts;
                                                if (inserts > 0) {
                                                        logger.log(INFO, () -> format(LOG_INFO_ENUM_MAPPING_TABLE_MODIFIED, historyTable, enumeratedType.getSimpleName(), number, "inserts in history table"));
                                                }
                                                if (inserts != absentInBase.size()) {
                                                        logger.log(WARNING, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, historyTable, "insert", absentInBase.size(), "insert", ", but only " + number + " of inserts was successful"));
                                                }
                                        }
                                }

                                if (doInserts && !absentInTarget.isEmpty()) {
                                        // Insert new data into table.
                                        StringBuilder insertQuery = new StringBuilder("INSERT INTO " + enumTable + " ("
                                                + (ordinal ? idEnumColumn : codeEnumColumn)
                                                + (oneFieldMapping ? "" : (ordinal ? ", " + codeEnumColumn : ", " + idEnumColumn)) + ")"
                                                + " VALUES ");// insert into enum_table (id, code) values (1, 'code');
                                        for (Iterator<EnumData.EnumDataEntry> iter = absentInTarget.iterator(); iter.hasNext();) {
                                                EnumData.EnumDataEntry ede = iter.next();
                                                insertQuery.append("("
                                                        + (ordinal ? ede.getId() : "'" + ede.getCode() + "'")
                                                        + (oneFieldMapping ? "" : (ordinal ? ", " + "'" + ede.getCode() + "'" : ", " + ede.getId()))
                                                        + (iter.hasNext() ? "), " : ");"));
                                        }

                                        try {
                                                ut.begin();
                                                entityManager.joinTransaction();
                                                int inserts = numInserts = entityManager.createNativeQuery(insertQuery.toString()).executeUpdate();
                                                logger.log(INFO, () -> format(LOG_INFO_ENUM_MAPPING_TABLE_MODIFIED, enumTable, enumeratedType.getSimpleName(), inserts, "inserts"));
                                                ut.commit();
                                        } catch (Exception ex) {
                                                try {
                                                        ut.rollback();
                                                } catch (Exception ignore) {
                                                }
                                                logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, enumTable, "insert", absentInTarget.size(), "insert", ""));
                                                return false;
                                        }
                                }

                                if (!different.isEmpty()) {
                                        // Update database table with respect to the alternate key.
                                        StringBuilder updateQuery = new StringBuilder();
                                        for (Iterator<EnumData.EnumDataEntry> iter = different.keySet().iterator(); iter.hasNext();) {
                                                EnumData.EnumDataEntry ede = iter.next();
                                                updateQuery.append("UPDATE " + enumTable + " SET "
                                                        + (ordinal ? codeEnumColumn : idEnumColumn) + " = "
                                                        + (ordinal ? "'" + ede.getCode() + "'" : ede.getId())
                                                        + " WHERE " + (ordinal ? idEnumColumn : codeEnumColumn) + " = "
                                                        + (ordinal ? ede.getId() : "'" + ede.getCode() + "'")
                                                        + (iter.hasNext() ? "; " : ";"));// update enum_table set code = 'code' where id = 1;
                                        }

                                        try {
                                                ut.begin();
                                                entityManager.joinTransaction();
                                                int updates = entityManager.createNativeQuery(updateQuery.toString()).executeUpdate();
                                                logger.log(INFO, () -> format(LOG_INFO_ENUM_MAPPING_TABLE_MODIFIED, enumTable, enumeratedType.getSimpleName(), updates, "updates"));
                                                ut.commit();
                                        } catch (Exception ex) {
                                                try {
                                                        ut.rollback();
                                                } catch (Exception ignore) {
                                                }
                                                logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, enumTable, "update", different.size(), "update", ""));
                                                return false;
                                        }
                                }

                                // Mark enum class for update of the secondary field.
                                mustUpdateSecondaryField = !oneFieldMapping;
                        } else {
                                // Update enum values.
                                List<Enum<?>> newValues = Arrays.asList(enumeratedType.getEnumConstants())
                                        .stream().filter(Objects::nonNull).collect(toList());

                                if (doDeletes && !absentInTarget.isEmpty()) {
                                        // Remove values from enum.
                                        numDeletes = absentInTarget.stream()
                                                .mapToInt(del -> {
                                                        Optional<Enum<?>> enumToDeleteOptional = newValues.stream()
                                                                .filter(enumConstant -> Objects.equals(ordinal ? del.id : del.code, ordinal ? enumConstant.ordinal() : enumConstant.name()))
                                                                .findFirst();
                                                        if (enumToDeleteOptional.isPresent()) {
                                                                Enum<?> enumToDelete = enumToDeleteOptional.get();
                                                                newValues.remove(enumToDelete);
                                                                Optional<Field> staticEnumConstantOptional = Arrays.asList(enumeratedType.getDeclaredFields()).stream()
                                                                        .filter(field -> {
                                                                                try {
                                                                                        return field.isEnumConstant() && field.get(null) == enumToDelete;
                                                                                } catch (Exception ignore) {
                                                                                        return false;
                                                                                }
                                                                        }).findFirst();
                                                                if (staticEnumConstantOptional.isPresent()) {
                                                                        try {
                                                                                modifyField(null, staticEnumConstantOptional.get(), null);
                                                                        } catch (Exception ex) {
                                                                                logger.log(WARNING, ex, () -> format(LOG_WARNING_UNMODIFIABLE_ENUM_FIELD, staticEnumConstantOptional.get().getName(), enumeratedType));
                                                                        }
                                                                }
                                                                return 1;
                                                        }
                                                        return 0;
                                                }).sum();

                                        // Try to add a deleted row to the database table.
                                        if (doSoftDeletes) {
                                                // Update history data into enum table.
                                                StringBuilder insertHistoryQueryBase = new StringBuilder("INSERT INTO " + enumTable + " ("
                                                        + ("".equals(idEnumColumn) ? "" : idEnumColumn)
                                                        + ("".equals(codeEnumColumn) ? "" : (oneFieldMapping ? "" : ", ") + codeEnumColumn)
                                                        + (", " + deletedColumn + ")")
                                                        + " VALUES ");// insert into enum_table (id, code, deleted) values (1, 'code', 1);
                                                int inserts = 0;

                                                for (Iterator<EnumData.EnumDataEntry> iter = absentInTarget.iterator(); iter.hasNext();) {
                                                        EnumData.EnumDataEntry ede = iter.next();
                                                        String insertHistoryQuery = insertHistoryQueryBase + "("
                                                                + ("".equals(idEnumColumn) ? "" : ede.getId())
                                                                + ("".equals(codeEnumColumn) ? "" : (oneFieldMapping ? "" : ", ") + "'" + ede.getCode() + "'")
                                                                + ", 1);";

                                                        try {
                                                                ut.begin();
                                                                entityManager.joinTransaction();
                                                                inserts += entityManager.createNativeQuery(insertHistoryQuery).executeUpdate();
                                                                ut.commit();
                                                        } catch (Exception ex) {
                                                                try {
                                                                        ut.rollback();
                                                                } catch (Exception ignore) {
                                                                }
                                                                logger.log(WARNING, ex, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, enumTable, "insert", 1, "insert", ", possible duplicate entry with key "
                                                                        + (ordinal ? ede.getId() : ede.getCode())));
                                                        }
                                                }

                                                int number = inserts;
                                                if (inserts > 0) {
                                                        logger.log(INFO, () -> format(LOG_INFO_ENUM_MAPPING_TABLE_MODIFIED, enumTable, enumeratedType.getSimpleName(), number, "number of inserts in history table"));
                                                }
                                                if (inserts != absentInTarget.size()) {
                                                        logger.log(WARNING, () -> format(LOG_WARNING_ENUM_MAPPING_TABLE_MODIFICATION_ERROR, enumTable, "insert", absentInBase.size(), "number of inserts in history table", ", but only " + number + " number of inserts was successful"));
                                                }
                                        }

                                        if (numDeletes != absentInTarget.size()) {
                                                logger.log(WARNING, () -> format(LOG_WARNING_CANNOT_MODIFY_ENUM_DATA, enumeratedType, "removal of enum values"));
                                                return false;
                                        }

                                        newValuesField = newValues;
                                }

                                if (doInserts && !absentInBase.isEmpty()) {
                                        // Add new values to enum.
                                        Optional<Object> constructorAccessorOptional = getEnumConstructorAccessor(enumeratedType);
                                        if (!constructorAccessorOptional.isPresent()) {
                                                logger.log(WARNING, () -> format(LOG_WARNING_CANNOT_ACCESS_ENUM_CONSTRUCTOR, enumeratedType));
                                                return false;
                                        }
                                        Object constructorAccessor = constructorAccessorOptional.get();

                                        Optional<Method> newInstanceMethodOptional = getEnumNewInstanceMethod(enumeratedType, constructorAccessor);
                                        if (!newInstanceMethodOptional.isPresent()) {
                                                logger.log(WARNING, () -> format(LOG_WARNING_CANNOT_ACCESS_ENUM_CONSTRUCTOR, enumeratedType));
                                                return false;
                                        }
                                        Method newInstanceMethod = newInstanceMethodOptional.get();

                                        boolean hasErrorsWhileCreatingEnums = (numInserts = absentInBase.stream()
                                                .mapToInt(insert -> {
                                                        int id = insert.id == null ? newValues.isEmpty() ? 0 : newValues.stream()
                                                                .map(enumConstant -> enumConstant.ordinal())
                                                                .mapToInt(Integer::intValue).max().getAsInt() + 1 : insert.id;
                                                        String code = insert.code == null ? "DEFAULT_" + id : insert.code;

                                                        try {
                                                                Enum<?> newEnum = enumeratedType.cast(newInstanceMethod.invoke(constructorAccessor, new Object[]{new Object[]{code, id}}));
                                                                newValues.add(newEnum);

                                                                // Modify enum fields as well to keep everything consistent.
                                                                if (idEnumFieldOptional.isPresent()) {
                                                                        modifyField(newEnum, idEnumFieldOptional.get(), id);
                                                                }
                                                                if (codeEnumFieldOptional.isPresent()) {
                                                                        modifyField(newEnum, codeEnumFieldOptional.get(), code);
                                                                }
                                                        } catch (Exception ignore) {
                                                                return 0;
                                                        }
                                                        return 1;
                                                }).sum()) != absentInBase.size();

                                        if (hasErrorsWhileCreatingEnums) {
                                                logger.log(WARNING, () -> format(LOG_WARNING_CANNOT_INSTANTIATE_NEW_ENUM, enumeratedType));
                                                return false;
                                        }

                                        newValuesField = newValues;
                                }

                                if (!different.isEmpty()) {
                                        // Update secondary enum field.
                                        boolean hasErrorsWhileModifyingEnums = different.entrySet().stream()
                                                .map(entry -> {
                                                        EnumData.EnumDataEntry target = entry.getValue();
                                                        Object primaryValue = ordinal ? target.id : target.code;
                                                        Object secondaryValue = ordinal ? target.code : target.id;
                                                        Optional<Enum<?>> enumToModifyOptional = newValues.stream()
                                                                .filter(enumConstant -> {
                                                                        return Objects.equals(primaryValue, ordinal ? enumConstant.ordinal() : enumConstant.name());
                                                                }).findFirst();
                                                        if (enumToModifyOptional.isPresent()) {
                                                                try {
                                                                        modifyField(enumToModifyOptional.get(), secondaryEnumField, secondaryValue);
                                                                } catch (Exception ex) {
                                                                        logger.log(WARNING, ex, () -> format(LOG_WARNING_UNMODIFIABLE_ENUM_FIELD, secondaryEnumField.getName(), enumeratedType));
                                                                        return true;
                                                                }
                                                        }
                                                        return false;
                                                }).anyMatch(e -> e);

                                        if (hasErrorsWhileModifyingEnums) {
                                                logger.log(WARNING, () -> format(LOG_WARNING_CANNOT_MODIFY_ENUM_DATA, enumeratedType, "modification of secondary values"));
                                                return false;
                                        }
                                }

                                if (!different.isEmpty() || (!oneFieldMapping && !common.isEmpty())) {
                                        // Mark enum class for update of the secondary field.
                                        mustUpdateSecondaryField = true;
                                }
                        }

                        // Update enum superclass internal fields.
                        List<Enum<?>> valuesToUpdate = newValuesField == null ? Arrays.asList(enumeratedType.getEnumConstants())
                                .stream().filter(Objects::nonNull).collect(toList()) : newValuesField;
                        if (mustUpdateSecondaryField) {
                                Field targetSecondaryField = findField(enumeratedType.getSuperclass(), ordinal ? "name" : "ordinal").get();
                                boolean hasErrorsWhileModifyingEnums = valuesToUpdate.stream()
                                        .map(constant -> {
                                                try {
                                                        Object value = accessField(constant, secondaryEnumField);
                                                        modifyField(constant, targetSecondaryField, value);
                                                } catch (Exception ex) {
                                                        logger.log(WARNING, ex, () -> format(LOG_WARNING_UNMODIFIABLE_ENUM_FIELD, targetSecondaryField.getName(), enumeratedType));
                                                        return true;
                                                }
                                                return false;
                                        }).anyMatch(e -> e);

                                if (hasErrorsWhileModifyingEnums) {
                                        logger.log(WARNING, () -> format(LOG_WARNING_CANNOT_MODIFY_ENUM_DATA, enumeratedType, "modification of " + (ordinal ? "name values" : "ordinal values")));
                                        return false;
                                } else {
                                        logger.log(INFO, () -> format(LOG_INFO_ENUM_DATA_MODIFIED, enumeratedType, valuesToUpdate.size(), "modification of " + (ordinal ? "name values" : "ordinal values")));
                                }
                        }

                        // Update enum values.
                        if (newValuesField != null || (mustUpdateSecondaryField && !ordinal)) {
                                try {
                                        // Get maximum new ordinal value.
                                        int maxOrdinal = valuesToUpdate.stream().mapToInt(Enum::ordinal).max().orElse(0);

                                        // Create arrays of new Enum mappings.
                                        Object values = Array.newInstance(enumeratedType, maxOrdinal + 1);
                                        valuesToUpdate.forEach(constant -> Array.set(values, constant.ordinal(), constant));
                                        Object nonBlankValues = Array.newInstance(enumeratedType, valuesToUpdate.size());
                                        int i = 0;
                                        for (Enum<?> e : valuesToUpdate) {
                                                Array.set(nonBlankValues, i++, e);
                                        }

                                        // Replace internal Enum values representation.
                                        Field enumValues = enumeratedType.getDeclaredField("$VALUES");
                                        modifyField(null, enumValues, values);

                                        // Rebuild internal cache that persistence providers are using under the covers.
                                        Field enumConstants = findField(Class.class, "enumConstants").get();
                                        modifyField(enumeratedType, enumConstants, null);
                                        Enum<?>[] constants = enumeratedType.getEnumConstants();

                                        Field enumConstantDirectory = findField(Class.class, "enumConstantDirectory").get();
                                        modifyField(enumeratedType, enumConstantDirectory, Arrays.asList(constants).stream().filter(Objects::nonNull).collect(Collectors.toMap(Enum::name, Function.identity())));

                                        // Return back the values array so that direct usage in code yields predictable behaviour.
                                        modifyField(null, enumValues, nonBlankValues);

                                        if (!enumPrecedence && numInserts > 0) {
                                                int number = numInserts;
                                                logger.log(INFO, () -> format(LOG_INFO_ENUM_DATA_MODIFIED, enumeratedType, number, "inserts"));
                                        }
                                        if (!enumPrecedence && numDeletes > 0) {
                                                int number = numDeletes;
                                                logger.log(INFO, () -> format(LOG_INFO_ENUM_DATA_MODIFIED, enumeratedType, number, "deletes"));
                                        }
                                } catch (Exception ex) {
                                        logger.log(WARNING, ex, () -> format(LOG_WARNING_CANNOT_MODIFY_ENUM_DATA, enumeratedType, "replacement of enum values"));
                                        return false;
                                }
                        }

                } finally {
                        entityManager.close();
                }

                return true;
        }

    	static boolean modifyEnumMapping(Class<? extends Enum<?>> enumeratedType) {
    		EnumMapping enumMapping = enumeratedType.getAnnotation(EnumMapping.class);
    		boolean mappedByOrdinal = (enumMapping.type() == ORDINAL);
    		String fieldName = "".equals(enumMapping.fieldName()) ? (mappedByOrdinal ? ID_FIELD_NAME : CODE_FIELD_NAME) : enumMapping.fieldName();
    		Optional<Field> optionalField = findField(enumeratedType, fieldName);

    		if (!optionalField.isPresent()) {
    			logger.log(WARNING, () -> format(LOG_WARNING_INVALID_ENUM_FIELD_NAME, enumeratedType, fieldName));
    			return false;
    		}

    		Field field = optionalField.get();
    		boolean validFieldType = (mappedByOrdinal ? (field.getType() == Integer.class || field.getType() == int.class) : field.getType() == String.class);

    		if (!validFieldType) {
    			logger.log(WARNING, () -> format(LOG_WARNING_INVALID_ENUM_FIELD_TYPE, enumeratedType, field.getName(), mappedByOrdinal ? "Integer" : "String", field.getType()));
    			return false;
    		}

    		Field targetField = findField(enumeratedType.getSuperclass(), mappedByOrdinal ? "ordinal" : "name").get();
    		List<Enum<?>> constants = asList(enumeratedType.getEnumConstants());

    		// Replace default enum mappings.
    		int maxOrdinal = constants.stream().map(constant -> {
    			Object value = accessField(constant, field);
    			modifyField(constant, targetField, value);
    			return mappedByOrdinal ? (int) value : 0;
    		}).mapToInt(Integer::intValue).max().orElse(0);

    		if (mappedByOrdinal) { // If ordinal type is used we need to update internal Enum representation as well.
    			try {
    				// Create array of new Enum mappings.
    				Object values = Array.newInstance(enumeratedType, maxOrdinal + 1);
    				constants.forEach(constant -> Array.set(values, constant.ordinal(), constant));

    				// Replace internal Enum values representation.
    				Field enumValues = findField(enumeratedType, "$VALUES").get();
    				Object oldValues = modifyField(null, enumValues, values);

    				// Rebuild internal cache that persistence providers are using under the covers.
    				Field enumConstants = findField(Class.class, "enumConstants").get();
    				modifyField(enumeratedType, enumConstants, null);
                                    Enum<?>[] enumeratedConstants = enumeratedType.getEnumConstants();

                                    Field enumConstantDirectory = findField(Class.class, "enumConstantDirectory").get();
                                    modifyField(enumeratedType, enumConstantDirectory, asList(enumeratedConstants).stream().filter(Objects::nonNull).collect(toMap(Enum::name, Function.identity())));

    				// Return back the values array so that direct usage in code yields predictable behaviour.
    				modifyField(null, enumValues, oldValues);
    			}
    			catch (Exception ex) {
    				logger.log(WARNING, ex, () -> format(LOG_WARNING_UNMODIFIABLE_ENUM_FIELD, enumeratedType, fieldName));
    				return false;
    			}
    		}

    		return true;
    	}

        // TODO refactor to Omniutils.
        private static String toSnakeCase(String camelCase) {
                if (camelCase == null) {
                        return null;
                }
                return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
        }

        // TODO refactor to Omniutils.
        public static Optional<Object> getEnumConstructorAccessor(Class<? extends Enum<?>> enumeratedType) {
                Object constructorAccessor = null;

                try {
                        // Enums that wish to implement such functionality must provide defult no-argument constructor.
                        Constructor<?> noArgumentEnumConstructor = enumeratedType.getDeclaredConstructor(String.class, int.class);

                        Method acquireConstructorAccessorMethod = Arrays.asList(noArgumentEnumConstructor.getClass().getDeclaredMethods()).stream()
                                .filter(method -> method.getName().equals("acquireConstructorAccessor"))
                                .findFirst().orElseThrow(NoSuchMethodException::new);
                        acquireConstructorAccessorMethod.setAccessible(true);
                        acquireConstructorAccessorMethod.invoke(noArgumentEnumConstructor, new Object[0]);

                        Field constructorAccessorField = Arrays.asList(noArgumentEnumConstructor.getClass().getDeclaredFields()).stream()
                                .filter(field -> field.getName().equals("constructorAccessor"))
                                .findFirst().orElseThrow(NoSuchFieldException::new);
                        constructorAccessorField.setAccessible(true);
                        constructorAccessor = constructorAccessorField.get(noArgumentEnumConstructor);

                        return Optional.of(constructorAccessor);
                } catch (Exception ignore) {
                        return Optional.empty();
                }
        }

        // TODO refactor to Omniutils.
        private Optional<Method> getEnumNewInstanceMethod(Class<? extends Enum<?>> enumeratedType, Object constructorAccessor) {
                try {
                        Method newInstanceMethod = constructorAccessor.getClass().getMethod("newInstance", new Class[]{Object[].class});
                        newInstanceMethod.setAccessible(true);

                        return Optional.of(newInstanceMethod);
                } catch (Exception ignore) {
                        return Optional.empty();
                }
        }

}
