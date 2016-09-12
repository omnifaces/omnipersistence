package org.omnifaces.persistence.model;

import static java.time.Instant.now;

import java.time.Instant;

import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;

@MappedSuperclass
public abstract class TimestampedEntity<T> extends BaseEntity<T> implements Timestamped {

	private static final long serialVersionUID = 1L;

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

	public void skipAdjustLastModified() {
		this.skipAdjustLastModified = true;
	}

}