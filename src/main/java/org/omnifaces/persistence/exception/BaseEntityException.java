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
package org.omnifaces.persistence.exception;

import javax.ejb.ApplicationException;
import javax.persistence.PersistenceException;

import org.omnifaces.persistence.model.BaseEntity;

@ApplicationException(rollback = true)
public abstract class BaseEntityException extends PersistenceException {

	private static final long serialVersionUID = 1L;

	private BaseEntity<?> entity;

	public BaseEntityException(BaseEntity<?> entity, String message) {
		super(message);
		this.entity = entity;
	}

	@SuppressWarnings("unchecked")
	public <E extends BaseEntity<?>> E getEntity() {
		return (E) entity;
	}

}