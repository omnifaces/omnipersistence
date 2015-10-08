package org.omnifaces.persistence.listener;

import static org.omnifaces.utils.annotation.AnnotationUtils.createAnnotationInstance;

import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PreUpdate;

import org.omnifaces.persistence.event.Created;
import org.omnifaces.persistence.event.Deleted;
import org.omnifaces.persistence.event.Updated;

public class PersistenceEventEntityListener {

	@Inject
	private BeanManager beanManager;

	@PostPersist
	public void onPostPersist(Object entity) {
		beanManager.fireEvent(entity, createAnnotationInstance(Created.class));
	}

	@PreUpdate
	public void onPreUpdate(Object entity) {
		beanManager.fireEvent(entity, createAnnotationInstance(Updated.class));
	}

	@PostRemove
	public void onPostRemove(Object entity) {
		// Listen to PostRemove instead of PreRemove as the latter is sometimes fired when the entity will not be removed at all
		beanManager.fireEvent(entity, createAnnotationInstance(Deleted.class));
	}

}
