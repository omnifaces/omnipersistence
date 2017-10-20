package org.omnifaces.persistence.event;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import org.omnifaces.persistence.listener.BaseEntityListener;

/**
 * Fired by {@link BaseEntityListener} when an entity is created.
 * @see BaseEntityListener
 */
@Qualifier
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface Created {
	//
}
