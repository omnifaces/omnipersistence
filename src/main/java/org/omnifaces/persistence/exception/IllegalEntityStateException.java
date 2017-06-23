package org.omnifaces.persistence.exception;

import org.omnifaces.persistence.model.BaseEntity;

public class IllegalEntityStateException extends BaseEntityException {

	private static final long serialVersionUID = 1L;

	public IllegalEntityStateException(BaseEntity<?> entity, String message) {
		super(entity, message);
	}

}