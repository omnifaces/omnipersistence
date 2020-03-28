--
-- Copyright 2020 OmniFaces
--
-- Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
-- the License. You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
-- an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.
--

CREATE TABLE hard_delete_id_enum_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, PRIMARY KEY (id), CONSTRAINT hard_delete_id_enum_info_code_UNIQUE UNIQUE (code));
CREATE TABLE soft_delete_id_enum_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, PRIMARY KEY (id), CONSTRAINT soft_delete_id_enum_info_code_UNIQUE UNIQUE (code));
CREATE TABLE hard_delete_id_table_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, PRIMARY KEY (id), CONSTRAINT hard_delete_id_table_info_code_UNIQUE UNIQUE (code));
CREATE TABLE soft_delete_id_table_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, deleted INT DEFAULT 0 NOT NULL, PRIMARY KEY (id), CONSTRAINT soft_delete_id_table_info_code_UNIQUE UNIQUE (code, deleted));

CREATE TABLE hard_delete_code_enum_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, PRIMARY KEY (code), CONSTRAINT hard_delete_code_enum_info_id_UNIQUE UNIQUE (id));
CREATE TABLE soft_delete_code_enum_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, PRIMARY KEY (code), CONSTRAINT soft_delete_code_enum_info_id_UNIQUE UNIQUE (id));
CREATE TABLE hard_delete_code_table_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, PRIMARY KEY (code), CONSTRAINT hard_delete_code_table_info_id_UNIQUE UNIQUE (id));
CREATE TABLE soft_delete_code_table_info (id INT NOT NULL, code VARCHAR(32) NOT NULL, deleted INT DEFAULT 0 NOT NULL, PRIMARY KEY (code), CONSTRAINT soft_delete_code_table_info_id_UNIQUE UNIQUE (id, deleted));

CREATE TABLE hard_delete_only_id_enum_info (id INT NOT NULL, PRIMARY KEY (id));
CREATE TABLE soft_delete_only_id_enum_info (id INT NOT NULL, PRIMARY KEY (id));
CREATE TABLE hard_delete_only_id_table_info (id INT NOT NULL, PRIMARY KEY (id));
CREATE TABLE soft_delete_only_id_table_info (id INT NOT NULL, deleted INT DEFAULT 0 NOT NULL, PRIMARY KEY (id));

CREATE TABLE hard_delete_only_code_enum_info (code VARCHAR(32) NOT NULL, PRIMARY KEY (code));
CREATE TABLE soft_delete_only_code_enum_info (code VARCHAR(32) NOT NULL, PRIMARY KEY (code));
CREATE TABLE hard_delete_only_code_table_info (code VARCHAR(32) NOT NULL, PRIMARY KEY (code));
CREATE TABLE soft_delete_only_code_table_info (code VARCHAR(32) NOT NULL, deleted INT DEFAULT 0 NOT NULL, PRIMARY KEY (code));

CREATE TABLE yet_another_table_for_another_enum (enum_id INT NOT NULL, enum_code VARCHAR(32) NOT NULL, non_active INT DEFAULT 0 NOT NULL, PRIMARY KEY (enum_id), CONSTRAINT yet_another_table_for_another_enum_id_UNIQUE UNIQUE (enum_code, non_active));
