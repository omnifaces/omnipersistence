/*
 * Copyright 2020 OmniFaces
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
package org.omnifaces.persistence.test.model.enums;

import org.omnifaces.persistence.model.EnumMapping;
import org.omnifaces.persistence.model.EnumMappingTable;

@EnumMapping(fieldName = "identifier", enumMappingTable = @EnumMappingTable(mappingType = EnumMappingTable.MappingType.ENUM, oneFieldMapping = false, deleteType = EnumMappingTable.DeleteAction.SOFT_DELETE,
        stringFieldName = "codeValue", ordinalColumnName = "table_id", stringColumnName = "table_code", tableName = "table_for_another_enum"))
public enum IdCodeEnumWithoutTable {

        FIRST(1, "1st"), TENTH(10, "10th"), TWENTIETH(20, "20th");

        private int identifier;
        private String codeValue;

        private IdCodeEnumWithoutTable() {

        }

        private IdCodeEnumWithoutTable(int id, String code) {
                this.identifier = id;
                this.codeValue = code;
        }

        public int getIdentifier() {
                return identifier;
        }

        public String getCodeValue() {
                return codeValue;
        }

}
