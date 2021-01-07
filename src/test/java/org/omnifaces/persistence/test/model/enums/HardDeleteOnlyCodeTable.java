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

import javax.persistence.EnumType;
import org.omnifaces.persistence.model.EnumMapping;
import org.omnifaces.persistence.model.EnumMappingTable;

@EnumMapping(fieldName = "code", type = EnumType.STRING, enumMappingTable = @EnumMappingTable(mappingType = EnumMappingTable.MappingType.TABLE, deleteType = EnumMappingTable.DeleteAction.HARD_DELETE))
public enum HardDeleteOnlyCodeTable {

        FIRST("1st"), TENTH("10th"), TWENTIETH("20th");

        private String code;

        private HardDeleteOnlyCodeTable() {

        }

        private HardDeleteOnlyCodeTable(String code) {
                this.code = code;
        }

        public String getCode() {
                return code;
        }

}
