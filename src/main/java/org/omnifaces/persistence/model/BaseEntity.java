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

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;

import org.omnifaces.persistence.listener.BaseEntityListener;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Let all your entities that don't auto-generate ids extend from this. Then you can make use of {@link BaseEntityService}.
 * Entity Id field and its accessors must be implemented in subclasses. It can also be used as a base class to provide for
 * <code>@GeneratedValue(strategy = SEQUENCE)</code> (i.e. other than IDENTITY functionality).
 * <p>
 * See {@link BaseAutoIdEntity} class for a base to extend the entities with Id to be set automatically.
 *
 * @param <I> The generic ID type, usually {@link String}.
 * @author Bauke Scholtz
 */
@MappedSuperclass
@EntityListeners(BaseEntityListener.class)
public abstract class BaseEntity<I extends Comparable<I> & Serializable> implements Comparable<BaseEntity<I>>, Identifiable<I>, Serializable {

	private static final long serialVersionUID = 1L;

        @Override
	public abstract I getId();

        @Override
	public abstract void setId(I id);

	/**
	 * Hashes by default the classname and ID.
	 */
	@Override
    public int hashCode() {
        return (getId() != null)
        	? Objects.hash(getClass().getSimpleName(), getId())
        	: super.hashCode();
    }

	/**
	 * Compares by default by entity class (proxies taken into account) and ID.
	 */
	@Override
    public boolean equals(Object other) {
        return (other != null && getId() != null && other.getClass().isAssignableFrom(getClass()) && getClass().isAssignableFrom(other.getClass()))
            ? getId().equals(((BaseEntity<?>) other).getId())
            : (other == this);
    }

	/**
	 * Orders by default with "nulls last".
	 */
	@Override
	public int compareTo(BaseEntity<I> other) {
		return (other == null)
			? -1
			: (getId() == null)
				? (other.getId() == null ? 0 : 1)
				: getId().compareTo(other.getId());
	}

	/**
	 * The default format is <code>ClassName[id={id}]</code>.
	 */
	@Override
	public String toString() {
		return String.format("%s[id=%d]", getClass().getSimpleName(), getId());
	}

}
