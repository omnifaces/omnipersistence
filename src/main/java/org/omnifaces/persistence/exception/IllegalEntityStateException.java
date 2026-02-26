/*
 * Copyright OmniFaces
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
package org.omnifaces.persistence.exception;

import org.omnifaces.persistence.model.BaseEntity;

/**
 * Thrown when an entity is in an illegal state for the requested operation. For example, when trying to
 * {@link org.omnifaces.persistence.service.BaseEntityService#persist(BaseEntity) persist} an entity that is already
 * persisted, or when trying to {@link org.omnifaces.persistence.service.BaseEntityService#update(BaseEntity) update}
 * an entity that has no ID.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see BaseEntityException
 */
public class IllegalEntityStateException extends BaseEntityException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new illegal entity state exception for the given entity and message.
     * @param entity The entity which is in an illegal state.
     * @param message The detail message.
     */
    public IllegalEntityStateException(BaseEntity<?> entity, String message) {
        super(entity, message);
    }

}