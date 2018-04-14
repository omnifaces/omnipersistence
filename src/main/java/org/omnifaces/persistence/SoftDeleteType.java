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
package org.omnifaces.persistence;

import org.omnifaces.persistence.model.SoftDeletable;

/**
 * Defines the types of the soft delete column.
 *
 * @see SoftDeletable
 *
 * @author Sergey Kuntsel
 */
public enum SoftDeleteType {

	/**
	 * Indicates that the associated column is a column holding deleted state.
	 * All entities that haven't been soft deleted will thus have false
	 * in the soft delete column, assuming it was mapped as <code>boolean</code>.
	 * This is the default type.
	 */
	DELETED,

	/**
	 * Indicates that the associated column is a column holding active state.
	 * All entities that haven't been soft deleted will thus have true
	 * in the soft delete column, assuming it was mapped as <code>boolean</code>.
	 */
	ACTIVE

}
