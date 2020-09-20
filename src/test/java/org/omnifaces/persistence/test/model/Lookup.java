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
package org.omnifaces.persistence.test.model;

import static org.omnifaces.persistence.model.SoftDeletable.Type.ACTIVE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.SoftDeletable;

@Entity
public class Lookup extends BaseEntity<String> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(length = 2, nullable = false, unique = true, name = "code")
	private String id;

	@SoftDeletable(type = ACTIVE)
	private boolean active = true;

	public Lookup() {
		//
	}

	public Lookup(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
