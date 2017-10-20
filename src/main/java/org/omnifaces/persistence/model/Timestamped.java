package org.omnifaces.persistence.model;

import java.time.Instant;

/**
 * <p>
 * Base interface for timestamped entity.
 *
 * @author Bauke Scholtz
 */
public interface Timestamped {

	void setCreated(Instant created);
	Instant getCreated();

	void setLastModified(Instant lastModified);
	Instant getLastModified();

}