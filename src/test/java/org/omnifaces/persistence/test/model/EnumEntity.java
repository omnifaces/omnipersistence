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
package org.omnifaces.persistence.test.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.test.model.enums.HardDeleteCodeEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteCodeTable;
import org.omnifaces.persistence.test.model.enums.HardDeleteIdEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteIdTable;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyCodeEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyCodeTable;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyIdEnum;
import org.omnifaces.persistence.test.model.enums.HardDeleteOnlyIdTable;
import org.omnifaces.persistence.test.model.enums.IdCodeEnumTableNonDefault;
import org.omnifaces.persistence.test.model.enums.IdCodeEnumWithoutTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteCodeEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteCodeTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteIdEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteIdTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyCodeEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyCodeTable;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyIdEnum;
import org.omnifaces.persistence.test.model.enums.SoftDeleteOnlyIdTable;

@Entity
public class EnumEntity extends GeneratedIdEntity<Long> {

        // two-valued enum table mappings
        @Enumerated
        private HardDeleteIdEnum hardDeleteIdEnum;

        @Enumerated
        private SoftDeleteIdEnum softDeleteIdEnum;

        @Enumerated
        private HardDeleteIdTable hardDeleteIdTable;

        @Enumerated
        private SoftDeleteIdTable softDeleteIdTable;

        @Enumerated(EnumType.STRING)
        private HardDeleteCodeEnum hardDeleteCodeEnum;

        @Enumerated(EnumType.STRING)
        private SoftDeleteCodeEnum softDeleteCodeEnum;

        @Enumerated(EnumType.STRING)
        private HardDeleteCodeTable hardDeleteCodeTable;

        @Enumerated(EnumType.STRING)
        private SoftDeleteCodeTable softDeleteCodeTable;

        // one-valued enum table mappings
        @Enumerated
        private HardDeleteOnlyIdEnum hardDeleteOnlyIdEnum;

        @Enumerated
        private SoftDeleteOnlyIdEnum softDeleteOnlyIdEnum;

        @Enumerated
        private HardDeleteOnlyIdTable hardDeleteOnlyIdTable;

        @Enumerated
        private SoftDeleteOnlyIdTable softDeleteOnlyIdTable;

        @Enumerated(EnumType.STRING)
        private HardDeleteOnlyCodeEnum hardDeleteOnlyCodeEnum;

        @Enumerated(EnumType.STRING)
        private SoftDeleteOnlyCodeEnum softDeleteOnlyCodeEnum;

        @Enumerated(EnumType.STRING)
        private HardDeleteOnlyCodeTable hardDeleteOnlyCodeTable;

        @Enumerated(EnumType.STRING)
        private SoftDeleteOnlyCodeTable softDeleteOnlyCodeTable;

        // non-default enum table mappings
        @Enumerated
        private IdCodeEnumWithoutTable idCodeEnumWithoutTable;

        @Enumerated
        private IdCodeEnumTableNonDefault idCodeEnumTableNonDefault;

        public HardDeleteIdEnum getHardDeleteIdEnum() {
                return hardDeleteIdEnum;
        }

        public void setHardDeleteIdEnum(HardDeleteIdEnum hardDeleteIdEnum) {
                this.hardDeleteIdEnum = hardDeleteIdEnum;
        }

        public SoftDeleteIdEnum getSoftDeleteIdEnum() {
                return softDeleteIdEnum;
        }

        public void setSoftDeleteIdEnum(SoftDeleteIdEnum softDeleteIdEnum) {
                this.softDeleteIdEnum = softDeleteIdEnum;
        }

        public HardDeleteIdTable getHardDeleteIdTable() {
                return hardDeleteIdTable;
        }

        public void setHardDeleteIdTable(HardDeleteIdTable hardDeleteIdTable) {
                this.hardDeleteIdTable = hardDeleteIdTable;
        }

        public SoftDeleteIdTable getSoftDeleteIdTable() {
                return softDeleteIdTable;
        }

        public void setSoftDeleteIdTable(SoftDeleteIdTable softDeleteIdTable) {
                this.softDeleteIdTable = softDeleteIdTable;
        }

        public HardDeleteCodeEnum getHardDeleteCodeEnum() {
                return hardDeleteCodeEnum;
        }

        public void setHardDeleteCodeEnum(HardDeleteCodeEnum hardDeleteCodeEnum) {
                this.hardDeleteCodeEnum = hardDeleteCodeEnum;
        }

        public SoftDeleteCodeEnum getSoftDeleteCodeEnum() {
                return softDeleteCodeEnum;
        }

        public void setSoftDeleteCodeEnum(SoftDeleteCodeEnum softDeleteCodeEnum) {
                this.softDeleteCodeEnum = softDeleteCodeEnum;
        }

        public HardDeleteCodeTable getHardDeleteCodeTable() {
                return hardDeleteCodeTable;
        }

        public void setHardDeleteCodeTable(HardDeleteCodeTable hardDeleteCodeTable) {
                this.hardDeleteCodeTable = hardDeleteCodeTable;
        }

        public SoftDeleteCodeTable getSoftDeleteCodeTable() {
                return softDeleteCodeTable;
        }

        public void setSoftDeleteCodeTable(SoftDeleteCodeTable softDeleteCodeTable) {
                this.softDeleteCodeTable = softDeleteCodeTable;
        }

        public HardDeleteOnlyIdEnum getHardDeleteOnlyIdEnum() {
                return hardDeleteOnlyIdEnum;
        }

        public void setHardDeleteOnlyIdEnum(HardDeleteOnlyIdEnum hardDeleteOnlyIdEnum) {
                this.hardDeleteOnlyIdEnum = hardDeleteOnlyIdEnum;
        }

        public SoftDeleteOnlyIdEnum getSoftDeleteOnlyIdEnum() {
                return softDeleteOnlyIdEnum;
        }

        public void setSoftDeleteOnlyIdEnum(SoftDeleteOnlyIdEnum softDeleteOnlyIdEnum) {
                this.softDeleteOnlyIdEnum = softDeleteOnlyIdEnum;
        }

        public HardDeleteOnlyIdTable getHardDeleteOnlyIdTable() {
                return hardDeleteOnlyIdTable;
        }

        public void setHardDeleteOnlyIdTable(HardDeleteOnlyIdTable hardDeleteOnlyIdTable) {
                this.hardDeleteOnlyIdTable = hardDeleteOnlyIdTable;
        }

        public SoftDeleteOnlyIdTable getSoftDeleteOnlyIdTable() {
                return softDeleteOnlyIdTable;
        }

        public void setSoftDeleteOnlyIdTable(SoftDeleteOnlyIdTable softDeleteOnlyIdTable) {
                this.softDeleteOnlyIdTable = softDeleteOnlyIdTable;
        }

        public HardDeleteOnlyCodeEnum getHardDeleteOnlyCodeEnum() {
                return hardDeleteOnlyCodeEnum;
        }

        public void setHardDeleteOnlyCodeEnum(HardDeleteOnlyCodeEnum hardDeleteOnlyCodeEnum) {
                this.hardDeleteOnlyCodeEnum = hardDeleteOnlyCodeEnum;
        }

        public SoftDeleteOnlyCodeEnum getSoftDeleteOnlyCodeEnum() {
                return softDeleteOnlyCodeEnum;
        }

        public void setSoftDeleteOnlyCodeEnum(SoftDeleteOnlyCodeEnum softDeleteOnlyCodeEnum) {
                this.softDeleteOnlyCodeEnum = softDeleteOnlyCodeEnum;
        }

        public HardDeleteOnlyCodeTable getHardDeleteOnlyCodeTable() {
                return hardDeleteOnlyCodeTable;
        }

        public void setHardDeleteOnlyCodeTable(HardDeleteOnlyCodeTable hardDeleteOnlyCodeTable) {
                this.hardDeleteOnlyCodeTable = hardDeleteOnlyCodeTable;
        }

        public SoftDeleteOnlyCodeTable getSoftDeleteOnlyCodeTable() {
                return softDeleteOnlyCodeTable;
        }

        public void setSoftDeleteOnlyCodeTable(SoftDeleteOnlyCodeTable softDeleteOnlyCodeTable) {
                this.softDeleteOnlyCodeTable = softDeleteOnlyCodeTable;
        }

        public IdCodeEnumWithoutTable getIdCodeEnumWithoutTable() {
                return idCodeEnumWithoutTable;
        }

        public void setIdCodeEnumWithoutTable(IdCodeEnumWithoutTable idCodeEnumWithoutTable) {
                this.idCodeEnumWithoutTable = idCodeEnumWithoutTable;
        }

        public IdCodeEnumTableNonDefault getIdCodeEnumTableNonDefault() {
                return idCodeEnumTableNonDefault;
        }

        public void setIdCodeEnumTableNonDefault(IdCodeEnumTableNonDefault idCodeEnumTableNonDefault) {
                this.idCodeEnumTableNonDefault = idCodeEnumTableNonDefault;
        }

}
