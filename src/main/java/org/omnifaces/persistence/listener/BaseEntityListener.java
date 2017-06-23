package org.omnifaces.persistence.listener;

import static org.omnifaces.utils.annotation.Annotations.createAnnotationInstance;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

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
 */
public class BaseEntityListener {

	@Inject
	private BeanManager beanManager;

	@PostPersist
	public void onPostPersist(BaseEntity<?> entity) {
		beanManager.fireEvent(entity, createAnnotationInstance(Created.class));
	}

	@PostUpdate
	public void onPostUpdate(BaseEntity<?> entity) {
		beanManager.fireEvent(entity, createAnnotationInstance(Updated.class));
	}

	@PostRemove
	public void onPostRemove(BaseEntity<?> entity) {
		beanManager.fireEvent(entity, createAnnotationInstance(Deleted.class));
	}

}
