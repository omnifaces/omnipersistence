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
package org.omnifaces.persistence.service;

import static java.lang.Character.toUpperCase;
import static java.lang.String.format;
import static org.omnifaces.utils.reflect.Reflections.accessField;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedFields;

import java.lang.reflect.Field;
import java.util.List;

import org.omnifaces.persistence.exception.NonSoftDeletableEntityException;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.SoftDeletable;

/**
 * Helper class of {@link BaseEntityService}.
 */
class SoftDeleteData {

    private static final String ERROR_NOT_SOFT_DELETABLE =
        "Entity %s cannot be soft deleted. You need to add a @SoftDeletable field first.";
    private static final String ERROR_ILLEGAL_SOFT_DELETABLE =
        "Entity %s cannot be soft deleted. There should be only one @SoftDeletable field.";

    private Class<?> entityType;
    private final boolean softDeletable;
    private final String fieldName;
    private final String setterName;
    private final boolean typeActive;

    public SoftDeleteData(Class<?> entityType) {
        this.entityType = entityType;
        List<Field> softDeletableFields = listAnnotatedFields(entityType, SoftDeletable.class);

        if (softDeletableFields.isEmpty()) {
            this.softDeletable = false;
            this.fieldName = null;
            this.setterName = null;
            this.typeActive = false;
        }
        else if (softDeletableFields.size() == 1) {
            Field softDeletableField = softDeletableFields.get(0);
            this.softDeletable = true;
            this.fieldName = softDeletableField.getName();
            this.setterName = ("set" + toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            this.typeActive = softDeletableField.getAnnotation(SoftDeletable.class).type() == SoftDeletable.Type.ACTIVE;
        }
        else {
            throw new IllegalStateException(format(ERROR_ILLEGAL_SOFT_DELETABLE, entityType));
        }
    }

    public void checkSoftDeletable() {
        if (!softDeletable) {
            throw new NonSoftDeletableEntityException(null, format(ERROR_NOT_SOFT_DELETABLE, entityType));
        }
    }

    public boolean isSoftDeleted(BaseEntity<?> entity) {
        if (!softDeletable) {
            return false;
        }

        boolean value = accessField(entity, fieldName);
        return typeActive ? !value : value;
    }

    public void setSoftDeleted(BaseEntity<?> entity, boolean deleted) {
        invokeMethod(entity, setterName, typeActive ? !deleted : deleted);
    }

    public String getWhereClause(boolean includeSoftDeleted) {
        if (!softDeletable) {
            return "";
        }

        return (" WHERE e." + fieldName + (includeSoftDeleted ? "=" : "!=") + (typeActive ? "false": "true"));
    }

    @Override
    public String toString() {
        return format("SoftDeleteData[softDeletable=%s, fieldName=%s, setterName=%s, typeActive=%s]", softDeletable, fieldName, setterName, typeActive);
    }
}

