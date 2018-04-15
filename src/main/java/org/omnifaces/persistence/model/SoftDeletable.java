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
package org.omnifaces.persistence.model;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * When put on a field of {@link BaseEntity}, then the special methods of
 * {@link BaseEntityService} will allow to soft-delete the entity and later
 * soft-undelete it. It will also allow to get all entities that are
 * soft-deleted and/or active in the data store. Calling those methods from a
 * service for an entity that doesn't have such column will throw will throw
 * {@link NonSoftDeletableEntityException}.
 *
 * @author Sergey Kuntsel
 */
@Target(value = { METHOD, FIELD })
@Retention(RUNTIME)
public @interface SoftDeletable {

	/**
	 * Defines the types of the soft delete column.
	 *
	 * @see SoftDeletable
	 *
	 * @author Sergey Kuntsel
	 */
	public enum Type {

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

	public Type type() default Type.DELETED;

}
