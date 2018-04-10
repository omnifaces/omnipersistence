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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import org.omnifaces.persistence.SoftDeleteType;
import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * When put on a field of {@link BaseEntity}, then the special methods of {@link BaseEntityService}
 * will allow to soft-delete the entity and later soft-undelete it. 
 * It will also allow to get all entities that are soft-deleted and/or active in the data store.
 * Calling those methods from a service for an entity that doesn't have such column
 * will throw will throw {@link NonSoftDeletableEntityException}.
 * 
 * @author Sergey Kuntsel
 */
@Target(value = {METHOD, FIELD})
@Retention(RUNTIME)
public @interface SoftDeletable {
    
    public SoftDeleteType type() default SoftDeleteType.ACTIVE;
    
}
