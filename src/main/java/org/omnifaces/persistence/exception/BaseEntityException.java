package org.omnifaces.persistence.exception;

import javax.ejb.ApplicationException;
import javax.persistence.PersistenceException;

import org.omnifaces.persistence.model.BaseEntity;

@ApplicationException(rollback = true)
public abstract class BaseEntityException extends PersistenceException {

	private static final long serialVersionUID = 1L;

	private BaseEntity<?> entity;

	public BaseEntityException(BaseEntity<?> entity, String message) {
		super(message);
		this.entity = entity;
	}

	@SuppressWarnings("unchecked")
	public <E extends BaseEntity<?>> E getEntity() {
		return (E) entity;
	}

}