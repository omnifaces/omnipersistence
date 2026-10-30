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

import static java.lang.String.format;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.reflect.Reflections.invokeSetter;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;

import org.omnifaces.persistence.model.Identifiable;

/**
 * Helper class of {@link BaseEntityService}. Holds the single ID attribute of an entity type as mapped by the metamodel, so that entities need not implement
 * {@link Identifiable} for their ID to be named, read and assigned. An entity which does implement {@link Identifiable} is assigned via its methods.
 */
final class IdAttribute {

    private static final String ERROR_UNSUPPORTED_ID = "%s must have a single @Id or @EmbeddedId attribute of a non-primitive type, e.g. Long instead"
        + " of long; @IdClass is not supported";

    private final String name;
    private final Member member;

    private IdAttribute(String name, Member member) {
        this.name = name;
        this.member = member;
    }

    /**
     * Returns the ID attribute of the given entity type.
     *
     * @param entityType The metamodel of the entity type.
     * @return The ID attribute of the given entity type.
     * @throws IllegalArgumentException When the entity type has an @IdClass, or an ID of a primitive type.
     */
    static IdAttribute of(EntityType<?> entityType) {
        var attribute = entityType.hasSingleIdAttribute()
            ? entityType.getSingularAttributes().stream().filter(SingularAttribute::isId).findFirst().orElse(null)
            : null;

        if (attribute == null || attribute.getJavaType().isPrimitive()) {
            throw new IllegalArgumentException(format(ERROR_UNSUPPORTED_ID, entityType.getJavaType()));
        }

        var member = attribute.getJavaMember();

        if (member instanceof Field field) {
            field.setAccessible(true);
        }

        return new IdAttribute(attribute.getName(), member);
    }

    String getName() {
        return name;
    }

    /**
     * Returns whether the ID is accessed via its getter and setter rather than via its field.
     *
     * @return Whether the ID is accessed via its getter and setter rather than via its field.
     */
    boolean isPropertyAccess() {
        return member instanceof Method;
    }

    /**
     * Returns the ID of the given entity, which may not be a proxy.
     *
     * @param <I> The generic ID type.
     * @param entity The entity to get the ID of.
     * @return The ID of the given entity, or <code>null</code> when it has none.
     */
    @SuppressWarnings("unchecked")
    <I> I get(Object entity) {
        if (member instanceof Method getter) {
            return invokeMethod(entity, getter);
        }

        try {
            return (I) ((Field) member).get(entity);
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets the ID of the given entity, which may not be a proxy unless it implements {@link Identifiable} or the ID is accessed via its getter and setter.
     *
     * @param entity The entity to set the ID on.
     * @param id The ID to set, may be <code>null</code>.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void set(Object entity, Object id) {
        if (entity instanceof Identifiable identifiable) {
            identifiable.setId((Comparable) id);
        }
        else if (member instanceof Method) {
            invokeSetter(entity, name, id);
        }
        else {
            try {
                ((Field) member).set(entity, id);
            }
            catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
