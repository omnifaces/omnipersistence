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
package org.omnifaces.persistence.test.model;

import static javax.persistence.EnumType.STRING;

import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;

import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.GeneratedIdEntity;

@Entity
public class Phone extends GeneratedIdEntity<Long> {

	private static final long serialVersionUID = 1L;

	public enum Type {
		MOBILE,
		HOME,
		WORK;
	}

	private @NotNull @Enumerated(STRING) Type type;
	private @NotNull String number;

	@ManyToOne(optional=false, fetch=FetchType.LAZY)
	private @NotNull Person owner;

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public Person getOwner() {
		return owner;
	}

	public void setOwner(Person owner) {
		this.owner = owner;
	}

	@Transient
	public String getEmail() {
		return getOwner().getEmail();
	}

	@Override
	public int hashCode() {
		return hashCode(Phone::getType, Phone::getNumber);
	}

	@Override
	public boolean equals(Object other) {
		return equals(other, Phone::getType, Phone::getNumber);
	}

	@Override
	public int compareTo(BaseEntity<Long> other) {
		return compareTo(other, Phone::getType, Phone::getNumber);
	}

	@Override
	public String toString() {
		return toString(Phone::getType, Phone::getNumber);
	}

}
