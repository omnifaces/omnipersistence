package org.omnifaces.persistence.model;

import java.io.Serializable;

/**
 * <p>
 * Base interface for something identifiable.
 *
 * @param <I> The generic ID type, usually {@link Long}.
 * @author Bauke Scholtz
 */
public interface Identifiable<I extends Comparable<I> & Serializable> {

	String ID = "id";
	I getId();
	void setId(I id);

}
