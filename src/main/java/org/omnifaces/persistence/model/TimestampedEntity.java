package org.omnifaces.persistence.model;

import static java.time.Instant.now;

import java.io.Serializable;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;

@MappedSuperclass
public abstract class TimestampedEntity<I extends Comparable<I> & Serializable> extends BaseEntity<I> implements Timestamped {

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