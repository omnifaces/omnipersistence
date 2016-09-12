package org.omnifaces.persistence.model;

import java.time.Instant;

public interface Timestamped {

	void setCreated(Instant created);
	Instant getCreated();

	void setLastModified(Instant lastModified);
	Instant getLastModified();

}