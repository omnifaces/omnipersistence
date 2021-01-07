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
package org.omnifaces.persistence.test.service;

import java.util.List;

import javax.ejb.Stateless;

import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.test.model.EnumEntity;

@Stateless
public class EnumEntityService extends BaseEntityService<Long, EnumEntity> {

        public List<Object> getHardDeleteIdEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_id_enum_info")
                        .getResultList();
        }

        public List<Object> getHardDeleteIdTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_id_table_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteIdEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_id_enum_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteIdTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_id_table_info WHERE deleted = 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteIdTableHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_id_table_info WHERE deleted != 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteIdEnumHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_id_enum_info_history")
                        .getResultList();
        }

        public List<Object> getHardDeleteCodeEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_code_enum_info")
                        .getResultList();
        }

        public List<Object> getHardDeleteCodeTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_code_table_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteCodeEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_code_enum_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteCodeTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_code_table_info WHERE deleted = 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteCodeTableHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_code_table_info WHERE deleted != 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteCodeEnumHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_code_enum_info_history")
                        .getResultList();
        }

        public List<Object> getHardDeleteOnlyIdEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_only_id_enum_info")
                        .getResultList();
        }

        public List<Object> getHardDeleteOnlyIdTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_only_id_table_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyIdEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_only_id_enum_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyIdTableTable() {
                return getEntityManager().createNativeQuery("SELECT id FROM soft_delete_only_id_table_info WHERE deleted = 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyIdTableHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT id FROM soft_delete_only_id_table_info WHERE deleted != 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyIdEnumHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_only_id_enum_info_history")
                        .getResultList();
        }

        public List<Object> getHardDeleteOnlyCodeEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_only_code_enum_info")
                        .getResultList();
        }

        public List<Object> getHardDeleteOnlyCodeTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM hard_delete_only_code_table_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyCodeEnumTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_only_code_enum_info")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyCodeTableTable() {
                return getEntityManager().createNativeQuery("SELECT code FROM soft_delete_only_code_table_info WHERE deleted = 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyCodeTableHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT code FROM soft_delete_only_code_table_info WHERE deleted != 0")
                        .getResultList();
        }

        public List<Object> getSoftDeleteOnlyCodeEnumHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM soft_delete_only_code_enum_info_history")
                        .getResultList();
        }

        public List<Object> getIdCodeEnumWithoutTableTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM table_for_another_enum")
                        .getResultList();
        }

        public List<Object> getIdCodeEnumWithoutTableHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM table_for_another_enum_history")
                        .getResultList();
        }

        public List<Object> getIdCodeEnumTableNonDefaultTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM yet_another_table_for_another_enum WHERE non_active = 0")
                        .getResultList();
        }

        public List<Object> getIdCodeEnumTableNonDefaultHistoryTable() {
                return getEntityManager().createNativeQuery("SELECT * FROM yet_another_table_for_another_enum WHERE non_active != 0")
                        .getResultList();
        }

}
