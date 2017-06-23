package org.omnifaces.persistence.model;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.omnifaces.persistence.exception.NonDeletableEntityException;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * When put on a {@link BaseEntity}, then any attempt to {@link BaseEntityService#delete(BaseEntity)} will throw
 * {@link NonDeletableEntityException}.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface NonDeletable {
	//
}