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
package org.omnifaces.persistence.test.model.enums;

import org.omnifaces.persistence.model.EnumMapping;
import org.omnifaces.persistence.model.EnumMappingTable;

@EnumMapping(fieldName = "id", enumMappingTable = @EnumMappingTable(mappingType = EnumMappingTable.MappingType.TABLE, oneFieldMapping = false, deleteType = EnumMappingTable.DeleteAction.SOFT_DELETE))
public enum SoftDeleteIdTable {

        FIRST(1, "1st"), TENTH(10, "10th"), TWENTIETH(20, "20th");

        private int id;
        private String code;

        private SoftDeleteIdTable() {

        }

        private SoftDeleteIdTable(int id, String code) {
                this.id = id;
                this.code = code;
        }

        public int getId() {
                return id;
        }

        public String getCode() {
                return code;
        }

}
