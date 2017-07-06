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

	/**
	 * The string representing the field name <code>"id"</code>.
	 */
	String ID = "id";

	/**
	 * Returns the ID.
	 * @return The ID.
	 */
	I getId();

	/**
	 * Sets the ID.
	 * @param id The ID.
	 */
	void setId(I id);

}
