package org.omnifaces.persistence.exception;

import org.omnifaces.persistence.model.BaseEntity;

public class NonDeletableEntityException extends BaseEntityException {

	private static final long serialVersionUID = 1L;

	public NonDeletableEntityException(BaseEntity<?> entity) {
		super(entity, null);
	}

}