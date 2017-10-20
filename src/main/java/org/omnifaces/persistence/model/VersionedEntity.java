package org.omnifaces.persistence.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Version;

/**
 * <p>
 * Mapped superclass for versioned entity. It specifies a {@link Version} column, named "version".
 * On pre persist, JPA will automatically set version to 0.
 * On pre update, JPA will automatically increment version with 1.
 *
 * @author Bauke Scholtz
 */
@MappedSuperclass
public abstract class VersionedEntity<I extends Comparable<I> & Serializable> extends TimestampedEntity<I> implements Versioned {

	private static final long serialVersionUID = 1L;

	@Version
	@Column(nullable = false)
	private Long version;

	@Override
	public Long getVersion() {
		return version;
	}

	// No setter! JPA takes care of this.
}