package org.omnifaces.persistence.model;

import java.io.Serializable;

import javax.persistence.MappedSuperclass;
import javax.persistence.Version;

@MappedSuperclass
public abstract class VersionedEntity<I extends Comparable<I> & Serializable> extends TimestampedEntity<I> implements Versioned {

	private static final long serialVersionUID = 1L;

	@Version
	private Long version;

	@Override
	public Long getVersion() {
		return version;
	}

	// No setter! JPA takes care of this.
}