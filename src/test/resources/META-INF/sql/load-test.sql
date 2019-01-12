--
-- Copyright 2019 OmniFaces
--
-- Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
-- the License. You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
-- an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.
--

INSERT INTO hard_delete_id_enum_info (id, code) VALUES (1, 'ABC'), (2, 'DEF');
INSERT INTO soft_delete_id_enum_info (id, code) VALUES (1, 'ABC'), (2, 'DEF');
INSERT INTO hard_delete_id_table_info (id, code) VALUES (1, 'ABC'), (2, 'DEF');
INSERT INTO soft_delete_id_table_info (id, code, deleted) VALUES (1, 'ABC', 0), (2, 'DEF', 0);

INSERT INTO hard_delete_code_enum_info (id, code) VALUES (1, 'ABC'), (2, 'DEF');
INSERT INTO soft_delete_code_enum_info (id, code) VALUES (1, 'ABC'), (2, 'DEF');
INSERT INTO hard_delete_code_table_info (id, code) VALUES (1, 'ABC'), (2, 'DEF');
INSERT INTO soft_delete_code_table_info (id, code, deleted) VALUES (1, 'ABC', 0), (2, 'DEF', 0);

INSERT INTO hard_delete_only_id_enum_info (id) VALUES (1), (2);
INSERT INTO soft_delete_only_id_enum_info (id) VALUES (1), (2);
INSERT INTO hard_delete_only_id_table_info (id) VALUES (1), (2);
INSERT INTO soft_delete_only_id_table_info (id, deleted) VALUES (1, 0), (2, 0);

INSERT INTO hard_delete_only_code_enum_info (code) VALUES ('ABC'), ('DEF');
INSERT INTO soft_delete_only_code_enum_info (code) VALUES ('ABC'), ('DEF');
INSERT INTO hard_delete_only_code_table_info (code) VALUES ('ABC'), ('DEF');
INSERT INTO soft_delete_only_code_table_info (code, deleted) VALUES ('ABC', 0), ('DEF', 0);

INSERT INTO yet_another_table_for_another_enum (enum_id, enum_code, non_active) VALUES (1, 'ABC', 0), (2, 'DEF', 0);
