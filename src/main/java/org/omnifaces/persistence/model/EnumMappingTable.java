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
package org.omnifaces.persistence.model;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.omnifaces.persistence.model.EnumMapping.CODE_FIELD_NAME;
import static org.omnifaces.persistence.model.EnumMapping.ID_FIELD_NAME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.persistence.EnumType;

/**
 * Enhanced annotation to fine-tune the default behaviour of
 * {@link EnumMapping}.
 * <p>
 * {@link EnumMapping} changes the default mechanism of persisting enums, making
 * persistence of enums steadier and more controllable. However, it doesn't
 * offer any functionality to have a mapping to a database table that holds all
 * of the possible values of an enum. With this annotation it is possible to
 * have a database table that will serve either as a source of enum values, that
 * will be incorporated by the application, or as a collection of all currently
 * possible enum values used in the application, with a possibility to track
 * historical values of previously used enums with either option.
 * <p>
 * Using this annotation allows to keep all possible enum values in a separate
 * database table and to differentiate between discontinued enum values that
 * were used in the past, but are not now supported by enforcing the established
 * correspondence mapping between java enum and database table representations
 * that can be further fine-tuned by using methods defined herewith.
 * <p>
 * When using this annotation the default mapping can be changed and one-to-one
 * correspondence between java enum and database table representations can be
 * achieved, with the additional possibility to track historical values of enum
 * constants used before.
 * <p>
 * To enable this behaviour one must supply an {@link EnumMappingTable} instance
 * to the {@link EnumMapping} annotation declaration with a specified
 * {@link #mappingType} that doesn't evaluate to {@link MappingType#NO_ACTION}.
 * The possible options are:
 * <ul>
 * <li> {@link MappingType#TABLE}. In this case the database table data takes
 * precedence over the java enum and the data of the specified table will be
 * used to adjust java enum constants accordingly. Database table must already
 * be in place to use this mapping type. <b>It is of utmost importance to
 * provide a private no-argument constructor to the enum that would use the
 * {@link MappingType#TABLE} mechanism to update enum constant values so that
 * new entries could be successfully added to the enum constant values.</b>
 * </li>
 * <li> {@link MappingType#ENUM}. In this case the java enum takes precedence
 * over the database table data and the structure of the specified enum
 * constants will be used to update database table data accordingly. If the
 * database table is not yet created it will be created by the application.
 * </li>
 * </ul>
 * <p>
 * The mapping between the enum and the database can be fine-tuned by using the
 * following settings:
 * <ul>
 * <li> {@link #oneFieldMapping} (defaults to true) is a flag that indicates
 * that only one field will be used to map the enum, the one that is specified
 * in {@link EnumMapping#fieldName}. If this flag is set to false, then two
 * fields will be persisted, both integer (holding the id) and string (holding
 * the code), basing on the specified settings.
 * </li>
 * <li> {@link #deleteType} (defaults to {@link DeleteAction#HARD_DELETE}) is a
 * setting that specifies the behaviour when an enum constant must be deleted
 * either from the database table, or from the java enum constants. If it
 * evaluates to {@link DeleteAction#HARD_DELETE} then only the currently used
 * enum data will be available in the database table, with all other data
 * deleted from the table. If it evaluates to {@link DeleteAction#SOFT_DELETE}
 * then all currently unused enum data will be also available in the database,
 * either in a separate history table (for {@link MappingType#ENUM} mapping
 * type), or in an enum data table (for {@link MappingType#TABLE} mapping type)
 * that must have a special soft delete column of INT-compatible type declared
 * that will hold values other than <code>0</code> for soft-deleted enum values.
 * The column name for the latter can be specified via the
 * {@link #deletedColumnName} that defaults to "deleted".
 * </li>
 * <li> {@link #doInserts} (defaults to true) is a flag that indicates that
 * insert statements will be performed in the database table, or new java enum
 * constants will be added to the java enum values, basing on
 * {@link #mappingType}.
 * </li>
 * </ul>
 * Persistent enum fields are indicated by the {@link #ordinalFieldName} (for
 * the integer id of the enum) and by the {@link #stringFieldName} (for the code
 * string value of the enum). The primary mapping key specified by the
 * {@link EnumMapping#fieldName} will overwrite the setting of this annotation.
 * Note that both the id and the code values must be unique, and it is a
 * developer's responsibility to enforce uniqueness in case of changes (either
 * in the database table, or in the java enum, basing on {@link #mappingType}).
 * <p>
 * The associated database table counterparts are:
 * <ul>
 * <li> {@link #tableName} for the name of the database table. In case it's not
 * supplied it will default to the lower- and snakecased simple name of enum
 * class with an added "_info" string.
 * </li>
 * <li> {@link #ordinalColumnName} for the integer-type column name holding the
 * id value of the enum.
 * </li>
 * <li> {@link #stringColumnName} for the string-type column name holding the
 * code value of the enum.
 * </li>
 * <li> {@link #deletedColumnName} for the integer-type column name holding the
 * deleted flag of the soft-deletable enums that's only applicable to mapping
 * type of {@link MappingType#TABLE}. In case of {@link MappingType#ENUM} an
 * additional table with the name equal to {@link #tableName} with an added
 * "_history" string will be created, or used, to hold the soft deleted enums.
 * </li>
 * </ul>
 *
 * <p>
 * Usage of this annotation is as follows. The first example illustrates how to
 * keep the enum id-code values in-sync from the java enum. It doesn't need to
 * create any database tables in advance, as the source of information will come
 * from the java enum and its modifications upon restart. So, for the following
 * enum:
 * <pre>
 *{@literal @}EnumMapping(enumMappingTable = {@literal @}EnumMappingTable(mappingType = EnumMappingTable.MappingType.ENUM,
 * oneFieldMapping = false, deleteType = EnumMappingTable.DeleteAction.SOFT_DELETE))
 * public enum UserRole {
 *
 *     USER(1, "USR"), EMPLOYEE(2, "EMP"), MANAGER(3, "MGR");
 *
 *     private String code;
 *     private int id;
 *
 *     private UserRole(int id, String code) {
 *         this.id = id;
 *         this.code = code;
 *     }
 *
 *     public String getCode() {
 *         return code;
 *     }
 *
 *     public int getId() {
 *         return id;
 *     }
 *
 * }
 * </pre> a table will be generated with the following content (an existing
 * table can also be used:
 * <pre>
 * CREATE TABLE user_role_info (id INT NOT NULL, code VARCHAR(32) NOT NULL,
 * PRIMARY KEY (id), CONSTRAINT user_role_info_code_UNIQUE UNIQUE (code));
 * INSERT INTO user_role_info (id, code) values (1, 'USR'), (2, 'EMP'), (3, 'MGR');
 * </pre> If at one moment the enum will be modified and the
 * <code>EMPLOYEE</code> enum constant will be removed with two new constants
 * added to the following:
 * <pre>
 *{@literal @}EnumMapping(enumMappingTable = {@literal @}EnumMappingTable(mappingType = EnumMappingTable.MappingType.ENUM,
 * oneFieldMapping = false, deleteType = EnumMappingTable.DeleteAction.SOFT_DELETE))
 * public enum UserRole {
 *
 *     USER(1, "USR"), MANAGER(3, "MGR"), PART_TIME_EMPLOYEE(4, "PTE"), FULL_TIME_EMPLOYEE(5, "FTE");
 *     // Old values: EMPLOYEE(2, "EMP")
 *     ...
 *
 * }
 * </pre> then the history table, <code>user_role_info_history</code>, will be
 * updated with the <code>2, 'EMP'</code> values and those values will be
 * removed from the <code>user_role_info</code> table with two new values added.
 * Unique key updates will also be performed if unique key changes, i.e. if
 * <code>EMPLOYEE(2, "EMP")</code> is changed at some point to
 * <code>EMPLOYEE(2, "EMPL")</code> then these changes will be reflected in the
 * table as well. Note that it is a developer responsibility not to introduce
 * colliding primary/unique keys so that the mapping can be performed
 * successfully.
 * <p>
 * Another example is a database-driven enum. All possible values are declared
 * in a database table, possibly keeping unused values with a soft deleted flag.
 * The application will keep the java enums in line with the database table and
 * update the enum class accordingly. It will also add currently declared in
 * enum class constants, but absent in the data store, as soft deleted rows. In
 * this case a database table must exist beforehand and could have the following
 * structure:
 * <pre>
 * CREATE TABLE user_role_info (id INT NOT NULL, code VARCHAR(32) NOT NULL,
 * deleted INT DEFAULT 0 NOT NULL, PRIMARY KEY (code), CONSTRAINT user_role_info_code_UNIQUE UNIQUE  (id, deleted));
 * INSERT INTO user_role_info (id, code, deleted) values (1, 'USR', 0), (2, 'EMP', 0), (3, 'MGR', 0);
 * </pre> The mapping will not be performed if the database table doesn't exist
 * at application startup. With the following table and the configured
 * annotation shown below the enum, no matter what values it had at compile
 * time, will have the values corresponding to the table data. So, for the enum:
 * <pre>
 *{@literal @}EnumMapping(type = EnumType.STRING, enumMappingTable = {@literal @}EnumMappingTable(mappingType = EnumMappingTable.MappingType.TABLE,
 * oneFieldMapping = false, deleteType = EnumMappingTable.DeleteAction.SOFT_DELETE))
 * public enum UserRole {
 *
 *     USER(6, "USR"), EMPLOYEE(7, "EMP"), MANAGER(8, "MGR");
 *
 *     private String code;
 *     private int id;
 *
 *     private UserRole() {} // Note that this no-argument constructor is obligatory with this setup.
 *
 *     private UserRole(int id, String code) {
 *         this.id = id;
 *         this.code = code;
 *     }
 *
 *     public String getCode() {
 *         return code;
 *     }
 *
 *     public int getId() {
 *         return id;
 *     }
 *
 * }
 * </pre> the identifiers of enums will be replaced with the data in the table,
 * i.e. <code>1, 2, 3</code> accordingly. If at one moment the table will be
 * modified and two new rows are added, i.e.
 * <code>(4, 'PTE', 0), (5, 'FTE', 0)</code>, and one row with id
 * <code>'EMP'</code> is hard-deleted, then at application startup the enum
 * constants will be replaced with the ones essentially equal to
 * <code>USER(1, "USR"), MANAGER(3, "MGR")</code> (the existing ones) and
 * <code>NEW_CONSTANT_1(4, "PTE"), NEW_CONSTANT_2(5, "FTE")</code> (the new
 * ones). The value <code>EMPLOYEE(7, "EMP")</code> will be removed and a
 * soft-deleted row <code>(7, 'EMP', 1)</code> will be added to the database.
 * Note that it would be still possible to refer to the existing enum constants
 * via the traditional methods, i.e. <code>UserRole.USER</code>, but the deleted
 * constant will be unavailable, i.e. <code>UserRole.EMPLOYEE</code> will hold
 * <code>null</code> value, and the freshly added ones will be unavailable via
 * static fields, but only via <code>UserRole.values()</code> call, or 
 * <code>UserRole.valueOf("PTE")</code> call, and the valuesthat will hold
 * correct set of enum constants. Also note that it is a developer
 * responsibility not to introduce tables with colliding primary/unique keys so
 * that the mapping can be performed successfully.
 *
 * <p>
 * Although the mapping will be correctly performed in any case, if no errors
 * are encountered, the best practice is to keep enum constants in line with the
 * database table and treat such functionality as a reminder to do the
 * associated updates. To achieve this goal it is important to look at the
 * generated warnings in log files at application startup stating that
 * difference in enum class and table data was detected and correct the code
 * accordingly. It is especially important for database-driven enums, as in the
 * case of enum-driven tables the database table will be corrected after the
 * first startup. So, these logs should serve as a reminder to cleanup the code.
 * <p>
 * The concluding remark about the internal functionality of the mapping is that
 * for the enum data to be treated correctly within the application the
 * <code>id</code> values will be used to modify {@link Enum#ordinal} and
 * <code>code</code> values will be used to modify {@link Enum#name}.
 *
 * @see EnumMapping
 * @author Sergey Kuntsel
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface EnumMappingTable {

        public static final String ID_COLUMN_NAME = "id";
        public static final String CODE_COLUMN_NAME = "code";
        public static final String DELETED_COLUMN_NAME = "deleted";
        public static final String DEFAULT_ENUM_TABLE_POSTFIX = "_info";
        public static final String DEFAULT_ENUM_HISTORY_TABLE_POSTFIX = "_history";

        /**
         * Defines the types of mapping between java enum and database table.
         */
        public enum MappingType {

                /**
                 * Indicates that the no special action will be taken to ensure
                 * integrity between java enum and database table
                 * representations. Standard {@link EnumMapping} rules will be
                 * applied to persist given values to the database. In order to
                 * fine-tune the mapping behaviour, one of two other types must
                 * be chosen. This is the default type.
                 */
                NO_ACTION,
                /**
                 * Indicates that the associated database table has a leading
                 * status. Database table must ensure the integrity of changes
                 * made to the enum constants. Information in the associated
                 * java enum will be updated accordingly.
                 */
                TABLE,
                /**
                 * Indicates that the associated java enum has a leading status.
                 * Enum class must ensure the integrity of changes made to the
                 * database table. Information in the associated database table
                 * will be updated accordingly.
                 */
                ENUM;

        }

        /**
         * Defines the types of the delete actions to be performed during the
         * mapping.
         */
        public enum DeleteAction {

                /**
                 * Indicates that the deletes will not be performed, neither to
                 * the database table, nor to the java enum.
                 */
                NO_ACTION,
                /**
                 * Indicates that the soft deletes will be performed to the
                 * database table rows. If {@link #mappingType} of this
                 * annotation evaluates to {@link MappingType#TABLE} then
                 * special column with the name specified in
                 * {@link #deletedColumnName} of <code>INT</code>-compatible
                 * type must be holding the soft deleted state that isn't equals
                 * to <code>0</code> if the row is deleted. Enum constants will
                 * be hard deleted in this case nonetheless and the deleted
                 * enums will be persisted as soft deleted ones to the database
                 * table. If {@link #mappingType} of this annotation evaluates
                 * to {@link MappingType#ENUM} then an additional database table
                 * with the name equal to {@link #tableName} with an added
                 * "_history" string will be created that will be holding all
                 * deleted instances of java enums if they are encountered in a
                 * corresponding table. This helps keep java enum and database
                 * table representations in line with each other and track
                 * historical values of java enum constants used previously.
                 */
                SOFT_DELETE,
                /**
                 * Indicates that the hard deletes will be performed, either to
                 * the database table rows, or to the java enum constants,
                 * basing on {@link #mappingType} of this annotation. This helps
                 * keep java enum and database table representations in line
                 * with each other without tracking historical values of java
                 * enum constants used previously. This is the default type.
                 */
                HARD_DELETE;

        }

        /**
         * Type of mapping between java enum and database table.
         *
         * @return Type of mapping between java enum and database table.
         */
        public MappingType mappingType() default MappingType.NO_ACTION;

        /**
         * Corresponding database table name of java enum representation.
         * Defaults to lower- and snakecased simple name of enum class with an
         * added "_info" string. The default value must be overridden in case
         * enums with equal simple class names are used in the application.
         *
         * @return Database table name of java enum representation.
         */
        public String tableName() default "";

        /**
         * Integer column name of the database table associated with the java
         * enum that will be treated as the unique id of the enum. If
         * {@link EnumMapping#type} evaluates to {@link EnumType#STRING} and
         * {@link #oneFieldMapping} evaluates to <code>true</code> then this
         * value will not be used while applying the mapping. Defaults to "id".
         *
         * @return Id column name of database table.
         */
        public String ordinalColumnName() default ID_COLUMN_NAME;

        /**
         * String column name of the database table associated with the java
         * enum that will be treated as the unique code name of the enum. If
         * {@link EnumMapping#type} evaluates to {@link EnumType#ORDINAL} and
         * {@link #oneFieldMapping} evaluates to <code>true</code> then this
         * value will not be used while applying the mapping. Defaults to
         * "code".
         *
         * @return Code column name of database table.
         */
        public String stringColumnName() default CODE_COLUMN_NAME;

        /**
         * Integer field name of the java enum that will be treated as its
         * unique id. The {@link EnumMapping#fieldName} value will be used
         * instead if {@link EnumMapping#type} evaluates to
         * {@link EnumType#ORDINAL}. Additionally, if {@link EnumMapping#type}
         * evaluates to {@link EnumType#STRING} and {@link #oneFieldMapping}
         * evaluates to <code>true</code> then this value will not be used while
         * applying the mapping. Defaults to "id".
         *
         * @return Id field name of java enum.
         */
        public String ordinalFieldName() default ID_FIELD_NAME;

        /**
         * String field name of the java enum that will be treated as its unique
         * code name. The {@link EnumMapping#fieldName} value will be used
         * instead if {@link EnumMapping#type} evaluates to
         * {@link EnumType#STRING}. Additionally, if {@link EnumMapping#type}
         * evaluates to {@link EnumType#ORDINAL} and {@link #oneFieldMapping}
         * evaluates to <code>true</code> then this value will not be used while
         * applying the mapping. Defaults to "code".
         *
         * @return Code field name of java enum.
         */
        public String stringFieldName() default CODE_FIELD_NAME;

        /**
         * Delete action type to be applied while mapping between java enum and
         * database table.
         *
         * @return Delete action type to be applied.
         */
        public DeleteAction deleteType() default DeleteAction.HARD_DELETE;

        /**
         * Deleted column name of the database table associated with the java
         * enum. It will be used only when {@link #mappingType} of this
         * annotation evaluates to {@link MappingType#TABLE} to mark deleted
         * rows in the database table representation. This column type must be
         * <code>INT</code>-compatible and its value different from
         * <code>0</code> indicates that the table row is soft-deleted. Defaults
         * to "deleted".
         *
         * @return Deleted column name of database table.
         */
        public String deletedColumnName() default DELETED_COLUMN_NAME;

        /**
         * A flag indicating that insert statements will be performed in the
         * database table or in the java enum, basing on
         * {@link EnumMapping#type}. Defaults to true.
         *
         * @return Insert statements flag.
         */
        public boolean doInserts() default true;

        /**
         * A flag indicating that only one field will be used to map the enum,
         * the one that is specified in {@link EnumMapping#fieldName}. If this
         * flag is set to false, then two fields will be persisted, both integer
         * (holding the id) and string (holding the code), basing on the
         * specified settings. Defaults to true.
         *
         * @return One field mapping flag.
         */
        public boolean oneFieldMapping() default true;

}
