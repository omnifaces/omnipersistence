/*
 * Copyright 2019 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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
