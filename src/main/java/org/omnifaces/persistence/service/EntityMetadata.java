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
import static java.util.Collections.unmodifiableSet;
import static java.util.logging.Level.FINE;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedFields;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Logger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.Metamodel;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.model.BaseEntity;
import org.omnifaces.persistence.model.GeneratedIdEntity;

/**
 * Helper class of {@link BaseEntityService}. Holds the metadata derived from an entity type, which is identical for every service over that type. The generated
 * ID flag and the soft delete data are derived from the entity class itself. The association mappings are derived from the Jakarta Persistence metamodel, and
 * are therefore resolved lazily on first use.
 *
 * @author Bauke Scholtz
 * @since 1.2
 */
final class EntityMetadata {

    private static final Logger logger = Logger.getLogger(BaseEntityService.class.getName());

    private static final Map<Class<?>, EntityMetadata> ENTITY_METADATA = new ConcurrentHashMap<>();

    private static final String LOG_FINE_COMPUTED_GENERATED_ID_MAPPING = "Computed generated ID mapping for %s: %s";
    private static final String LOG_FINE_COMPUTED_SOFT_DELETE_MAPPING = "Computed soft delete mapping for %s: %s";
    private static final String LOG_FINE_COMPUTED_ASSOCIATION_MAPPING = "Computed %s mapping for %s: %s";

    private enum Association {

        ELEMENT_COLLECTION("@ElementCollection", Provider::isElementCollection),
        MANY_OR_ONE_TO_ONE("@ManyToOne/@OneToOne", Provider::isManyOrOneToOne),
        ONE_TO_MANY("@OneToMany", Provider::isOneToMany);

        private final String label;
        private final BiPredicate<Provider, Attribute<?, ?>> predicate;

        Association(String label, BiPredicate<Provider, Attribute<?, ?>> predicate) {
            this.label = label;
            this.predicate = predicate;
        }

    }

    private final Class<?> entityType;
    private final boolean generatedId;
    private final SoftDeleteData softDeleteData;
    private final Map<Association, Set<String>> associations = new ConcurrentHashMap<>();

    EntityMetadata(Class<?> entityType) {
        this.entityType = entityType;
        this.generatedId = GeneratedIdEntity.class.isAssignableFrom(entityType) || !listAnnotatedFields(entityType, Id.class, GeneratedValue.class).isEmpty();
        logger.log(FINE, () -> format(LOG_FINE_COMPUTED_GENERATED_ID_MAPPING, entityType, generatedId));
        this.softDeleteData = new SoftDeleteData(entityType);
        logger.log(FINE, () -> format(LOG_FINE_COMPUTED_SOFT_DELETE_MAPPING, entityType, softDeleteData));
    }

    /**
     * Returns the metadata of the given entity type.
     *
     * @param entityType The entity type.
     * @return The metadata of the given entity type.
     */
    static EntityMetadata of(Class<?> entityType) {
        return ENTITY_METADATA.computeIfAbsent(entityType, EntityMetadata::new);
    }

    boolean isGeneratedId() {
        return generatedId;
    }

    SoftDeleteData getSoftDeleteData() {
        return softDeleteData;
    }

    /**
     * Returns the paths of all <code>@ElementCollection</code> fields, including those of nested entities.
     *
     * @param entityManager The entity manager providing the metamodel.
     * @param provider The provider to determine the association type with.
     * @return The paths of all <code>@ElementCollection</code> fields.
     */
    Set<String> getElementCollections(EntityManager entityManager, Provider provider) {
        return getAssociationMapping(Association.ELEMENT_COLLECTION, entityManager, provider);
    }

    /**
     * Returns the paths of all <code>@ManyToOne</code> and <code>@OneToOne</code> fields, including those of nested entities.
     *
     * @param entityManager The entity manager providing the metamodel.
     * @param provider The provider to determine the association type with.
     * @return The paths of all <code>@ManyToOne</code> and <code>@OneToOne</code> fields.
     */
    Set<String> getManyOrOneToOnes(EntityManager entityManager, Provider provider) {
        return getAssociationMapping(Association.MANY_OR_ONE_TO_ONE, entityManager, provider);
    }

    /**
     * Returns whether the given field path is nested in a <code>@OneToMany</code> field, e.g. <code>phones.number</code> when <code>phones</code> is a
     * <code>@OneToMany</code> field.
     *
     * @param field The field path.
     * @param entityManager The entity manager providing the metamodel.
     * @param provider The provider to determine the association type with.
     * @return Whether the given field path is nested in a <code>@OneToMany</code> field.
     */
    boolean isOneToMany(String field, EntityManager entityManager, Provider provider) {
        return getAssociationMapping(Association.ONE_TO_MANY, entityManager, provider).stream().anyMatch(oneToMany -> field.startsWith(oneToMany + '.'));
    }

    private Set<String> getAssociationMapping(Association association, EntityManager entityManager, Provider provider) {
        return associations.computeIfAbsent(association, k -> computeAssociationMapping(association, entityManager.getMetamodel(), provider));
    }

    private Set<String> computeAssociationMapping(Association association, Metamodel metamodel, Provider provider) {
        var nestedTypes = new HashSet<Class<?>>();
        nestedTypes.add(entityType);
        var mapping = computeEntityMapping(metamodel, entityType, "", nestedTypes, attribute -> association.predicate.test(provider, attribute));
        logger.log(FINE, () -> format(LOG_FINE_COMPUTED_ASSOCIATION_MAPPING, association.label, entityType, mapping));
        return mapping;
    }

    private static Set<String> computeEntityMapping(
        Metamodel metamodel, Class<?> type, String basePath, Set<Class<?>> nestedTypes, Predicate<Attribute<?, ?>> attributePredicate
    )
    {
        var entityMapping = new HashSet<String>(2);
        var entity = metamodel.entity(type);

        for (var attribute : entity.getAttributes()) {
            if (attributePredicate.test(attribute)) {
                entityMapping.add(basePath + attribute.getName());
            }

            if (attribute instanceof Bindable<?> bindable) {
                Class<?> nestedType = bindable.getBindableJavaType();

                if (BaseEntity.class.isAssignableFrom(nestedType) && nestedTypes.add(nestedType)) {
                    entityMapping.addAll(computeEntityMapping(metamodel, nestedType, basePath + attribute.getName() + '.', nestedTypes, attributePredicate));
                }
            }
        }

        return unmodifiableSet(entityMapping);
    }

}
