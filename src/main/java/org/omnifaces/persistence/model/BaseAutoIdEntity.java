/*
 * Copyright 2018 OmniFaces.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.omnifaces.persistence.model;

import static javax.persistence.GenerationType.IDENTITY;

import java.io.Serializable;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Let all your entities that autogenerate their Ids extend from this. Then you can make use of {@link BaseEntityService}. This mapped superclass
 * already automatically takes care of the <code>id</code> column.
 * <p>
 * There are two more mapped superclasses which may also be of interest.
 * <ul>
 * <li>{@link TimestampedEntity} - extends {@link BaseAutoIdEntity} with <code>created</code> and <code>lastModified</code>
 * columns and automatically takes care of them.
 * <li>{@link VersionedEntity} - extends {@link TimestampedEntity} with a <code>@Version</code> column.
 * </ul>
 *
 * @param <I> The generic ID type, usually {@link Long}.
 * @author Bauke Scholtz
 */

@MappedSuperclass
public abstract class BaseAutoIdEntity<I extends Comparable<I> & Serializable> extends BaseEntity<I> {
    
	private static final long serialVersionUID = 1L;

	@Id @GeneratedValue(strategy = IDENTITY)
	private I id;

	@Override
	public I getId() {
		return id;
	}

	@Override
	public void setId(I id) {
		this.id = id;
	}

}
