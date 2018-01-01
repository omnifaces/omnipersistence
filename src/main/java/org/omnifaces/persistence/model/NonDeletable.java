/*
 * Copyright 2018 OmniFaces
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