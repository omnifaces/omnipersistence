/*
 * Copyright 2021 OmniFaces
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
package org.omnifaces.persistence.listener;

import static org.omnifaces.utils.annotation.Annotations.createAnnotationInstance;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import org.omnifaces.persistence.event.Created;
import org.omnifaces.persistence.event.Deleted;
import org.omnifaces.persistence.event.Updated;
import org.omnifaces.persistence.model.BaseEntity;

/**
 * <p>
 * This is by default already registered on {@link BaseEntity}. It will fire CDI events {@link Created}, {@link Updated}
 * and {@link Deleted} which you can {@link Observes} on your entity.
 * <p>
 * Usage example:
 * <pre>
 * public void onCreate(&#64;Observes &#64;Created YourEntity yourEntity) {
 *     // ...
 * }
 *
 * public void onUpdate(&#64;Observes &#64;Updated YourEntity yourEntity) {
 *     // ...
 * }
 *
 * public void onDelete(&#64;Observes &#64;Deleted YourEntity yourEntity) {
 *     // ...
 * }
 * </pre>
 *
 * @see Created
 * @see Updated
 * @see Deleted
 */
public class BaseEntityListener {

	@Inject
	private BeanManager beanManager;

	@PostPersist
	public void onPostPersist(BaseEntity<?> entity) {
		getBeanManager().fireEvent(entity, createAnnotationInstance(Created.class));
	}

	@PostUpdate
	public void onPostUpdate(BaseEntity<?> entity) {
		getBeanManager().fireEvent(entity, createAnnotationInstance(Updated.class));
	}

	@PostRemove
	public void onPostRemove(BaseEntity<?> entity) {
		getBeanManager().fireEvent(entity, createAnnotationInstance(Deleted.class));
	}

	private BeanManager getBeanManager() {
		if (beanManager == null) {
			beanManager = CDI.current().getBeanManager(); // Work around for CDI inject not working in JPA EntityListener (as observed in OpenJPA).
		}

		return beanManager;
	}

}
