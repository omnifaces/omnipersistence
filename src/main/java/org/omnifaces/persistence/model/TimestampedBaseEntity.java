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
package org.omnifaces.persistence.model;

import static java.time.Instant.now;

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;

/**
 * <p>
 * Mapped superclass for timestamped entity.
 * It extends from {@link BaseEntity}.
 * It specifies two timestamp columns, named "created" and "lastModified".
 * The {@link Id} column needs to be manually taken care of.
 * On pre persist, the both columns will be set to current timestamp.
 * On pre update, the "lastModified" column will be set to current timestamp, unless {@link #skipAdjustLastModified()} is called beforehand.
 *
 * @param <I> The generic ID type.
 * @author Bauke Scholtz
 */
@MappedSuperclass
public abstract class TimestampedBaseEntity<I extends Comparable<I> & Serializable> extends BaseEntity<I> implements Timestamped {

	private static final long serialVersionUID = 1L;

	@Column(nullable = false)
	private Instant created;

	@Column(nullable = false)
	private Instant lastModified;

	@Transient
	private boolean skipAdjustLastModified;

	@PrePersist
	public void onPrePersist() {
		Instant timestamp = now();
		setCreated(timestamp);
		setLastModified(timestamp);
	}

	@PreUpdate
	public void onPreUpdate() {
		if (!skipAdjustLastModified) {
			Instant timestamp = now();
			setLastModified(timestamp);
		}
	}

	/**
	 * Invoke this method if you need to skip adjusting the "last modified" timestamp during any update event on this
	 * instance. In case you intend to reset this later on, simply obtain a new instance from the entity manager.
	 */
	public void skipAdjustLastModified() {
		this.skipAdjustLastModified = true;
	}

	@Override
	public void setCreated(Instant created) {
		this.created = created;
	}

	@Override
	public Instant getCreated() {
		return created;
	}

	@Override
	public void setLastModified(Instant lastModified) {
		this.lastModified = lastModified;
	}

	@Override
	public Instant getLastModified() {
		return lastModified;
	}

}