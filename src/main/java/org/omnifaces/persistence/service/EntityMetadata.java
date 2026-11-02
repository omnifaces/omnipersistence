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
import static java.util.Collections.synchronizedMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.omnifaces.utils.reflect.Reflections.listAnnotatedFields;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.model.GeneratedIdEntity;
import org.omnifaces.persistence.model.Identifiable;

/**
 * Helper class of {@link BaseEntityService}. Holds the metadata derived from an entity type, which is identical for every service over that type. The generated
 * ID flag and the soft delete data are derived from the entity class itself. The association mappings are derived from the Jakarta Persistence metamodel, and
 * are therefore resolved lazily on first use, separately per {@link EntityManagerFactory}, because two persistence units or providers may map the same entity
 * class differently. The caches do not keep entity manager factories reachable; the entity classes held per factory are released along with it.
 *
 * @author Bauke Scholtz
 * @since 1.2
 */
final class EntityMetadata {

    private static final Logger logger = Logger.getLogger(BaseEntityService.class.getName());

    private static final String LOG_FINE_COMPUTED_GENERATED_ID_MAPPING = "Computed generated ID mapping for %s: %s";
    private static final String LOG_FINE_COMPUTED_SOFT_DELETE_MAPPING = "Computed soft delete mapping for %s: %s";
    private static final String LOG_FINE_COMPUTED_MANY_OR_ONE_TO_ONE_MAPPING = "Computed @ManyToOne/@OneToOne mapping for %s: %s";

    private static final ClassValue<EntityMetadata> ENTITY_METADATA = new ClassValue<>() {

        @Override
        protected EntityMetadata computeValue(Class<?> entityType) {
            return new EntityMetadata(entityType);
        }

    };

    /**
     * The mappings per entity manager factory, shared by all entity types within it. These depend only on the metamodel and the provider, and one entity
     * manager factory has one provider. The values may not reference the entity manager factory, directly or via metamodel objects, else it would never be
     * released from the weak map.
     */
    private static final Map<EntityManagerFactory, UnitMappings> UNIT_MAPPINGS = synchronizedMap(new WeakHashMap<>());

    private final Class<?> entityType;
    private final boolean generatedId;
    private final SoftDeleteData softDeleteData;
    private final Map<EntityManagerFactory, FactoryMappings> mappingsPerFactory = synchronizedMap(new WeakHashMap<>());

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
        return ENTITY_METADATA.get(entityType);
    }

    boolean isGeneratedId() {
        return generatedId;
    }

    SoftDeleteData getSoftDeleteData() {
        return softDeleteData;
    }

    /**
     * Returns the mappings of this entity type as mapped by the persistence unit of the given entity manager factory. The mappings are resolved lazily, and
     * cached per entity manager factory.
     *
     * @param entityManagerFactory The entity manager factory providing the metamodel.
     * @param provider The provider to determine the association type and to read proxies with.
     * @return The mappings of this entity type as mapped by the persistence unit of the given entity manager factory.
     */
    Mappings getMappings(EntityManagerFactory entityManagerFactory, Provider provider) {
        var metamodel = entityManagerFactory.getMetamodel();
        return new Mappings(
            mappingsPerFactory.computeIfAbsent(entityManagerFactory, k -> new FactoryMappings()),
            UNIT_MAPPINGS.computeIfAbsent(entityManagerFactory, k -> new UnitMappings(metamodel)), metamodel, provider
        );
    }

    /**
     * The mappings of an entity type within one persistence unit: its ID and its associations. Field paths are resolved on demand by walking the metamodel
     * along the path, and the outcome is cached per path. Fields which cannot be resolved are not cached.
     */
    final class Mappings {

        private final FactoryMappings factoryMappings;
        private final UnitMappings unitMappings;
        private final Metamodel metamodel;
        private final Provider provider;

        private Mappings(FactoryMappings factoryMappings, UnitMappings unitMappings, Metamodel metamodel, Provider provider) {
            this.factoryMappings = factoryMappings;
            this.unitMappings = unitMappings;
            this.metamodel = metamodel;
            this.provider = provider;
        }

        /**
         * Returns whether the given type is an entity type of this persistence unit.
         *
         * @param type The type to check.
         * @return Whether the given type is an entity type of this persistence unit.
         */
        boolean isEntity(Class<?> type) {
            return unitMappings.entityTypes.contains(type);
        }

        /**
         * Returns the ID attribute of the given entity type of this persistence unit.
         *
         * @param type The entity type.
         * @return The ID attribute of the given entity type.
         * @throws IllegalArgumentException When the given type is not an entity type, or has an unsupported ID.
         */
        IdAttribute getIdAttribute(Class<?> type) {
            return unitMappings.idAttributes.computeIfAbsent(type, k -> IdAttribute.of(metamodel.entity(k)));
        }

        /**
         * Returns the ID of the given entity of this persistence unit: via the provider when it is a proxy, else via {@link Identifiable#getId()} when it
         * implements it, else via its ID attribute.
         *
         * @param <I> The generic ID type.
         * @param entity The entity, may be a proxy.
         * @return The ID of the given entity, or <code>null</code> when it has none.
         * @throws IllegalArgumentException When the given entity is not an entity of this persistence unit, or has an unsupported ID.
         */
        @SuppressWarnings("unchecked")
        <I> I getId(Object entity) {
            if (provider.isProxy(entity)) {
                return provider.getIdentifier(entity);
            }

            return entity instanceof Identifiable<?> identifiable ? (I) identifiable.getId() : getIdAttribute(provider.getEntityType(entity)).get(entity);
        }

        /**
         * Sets the ID of the given entity of this persistence unit to <code>null</code>: via {@link Identifiable#setId(Comparable)} when it implements it, else
         * via its ID attribute, on the actual implementation when it is a proxy and the ID is accessed via its field.
         *
         * @param entity The entity, may be a proxy.
         * @throws IllegalArgumentException When the given entity is not an entity of this persistence unit, or has an unsupported ID.
         */
        void unsetId(Object entity) {
            var idAttribute = getIdAttribute(provider.getEntityType(entity));
            idAttribute.set(entity instanceof Identifiable || idAttribute.isPropertyAccess() ? entity : provider.dereferenceProxy(entity), null);
        }

        /**
         * Returns whether the given field path ends in an <code>@ElementCollection</code> field, e.g. <code>shippingAddress.lines</code>.
         *
         * @param field The field path.
         * @return Whether the given field path ends in an <code>@ElementCollection</code> field.
         */
        boolean isElementCollection(String field) {
            return getPathMapping(field).elementCollection();
        }

        /**
         * Returns whether the given field path is nested in a <code>@OneToMany</code> field, e.g. <code>phones.number</code> when <code>phones</code> is a
         * <code>@OneToMany</code> field.
         *
         * @param field The field path.
         * @return Whether the given field path is nested in a <code>@OneToMany</code> field.
         */
        boolean isOneToMany(String field) {
            return getPathMapping(field).nestedInOneToMany();
        }

        /**
         * Returns the paths of <code>@ManyToOne</code> and <code>@OneToOne</code> fields, including those of nested entities. Each nested entity type is
         * visited only once, so when it is reachable via more than one path, its fields are listed under only one of them. This is only used as candidate list
         * to guess the path of a <code>@Transient</code> field.
         *
         * @return The paths of <code>@ManyToOne</code> and <code>@OneToOne</code> fields.
         */
        Set<String> getManyOrOneToOnes() {
            var manyOrOneToOnes = factoryMappings.manyOrOneToOnes;

            if (manyOrOneToOnes == null) {
                synchronized (factoryMappings) {
                    manyOrOneToOnes = factoryMappings.manyOrOneToOnes;

                    if (manyOrOneToOnes == null) {
                        manyOrOneToOnes = computeManyOrOneToOneMapping();
                        factoryMappings.manyOrOneToOnes = manyOrOneToOnes;
                    }
                }
            }

            return manyOrOneToOnes;
        }

        private PathMapping getPathMapping(String field) {
            var pathMapping = factoryMappings.paths.get(field);

            if (pathMapping == null) {
                pathMapping = resolvePathMapping(field);

                if (pathMapping.resolved()) {
                    factoryMappings.paths.putIfAbsent(field, pathMapping);
                }
            }

            return pathMapping;
        }

        private PathMapping resolvePathMapping(String field) {
            var attributeNames = field.split("\\.", -1);
            Class<?> type = entityType;
            var lastIndex = attributeNames.length - 1;
            var nestedInOneToMany = false;

            for (var i = 0; i < lastIndex; i++) {
                var attribute = getAttributeMappings(type).get(attributeNames[i]);

                if (attribute == null) {
                    return PathMapping.unresolved(nestedInOneToMany);
                }

                nestedInOneToMany |= attribute.oneToMany();

                if (attribute.nestedType() == null) {
                    return PathMapping.unresolved(nestedInOneToMany);
                }

                type = attribute.nestedType();
            }

            var lastAttribute = getAttributeMappings(type).get(attributeNames[lastIndex]);
            return lastAttribute != null
                ? new PathMapping(true, lastAttribute.elementCollection(), nestedInOneToMany)
                : PathMapping.unresolved(nestedInOneToMany);
        }

        private Map<String, AttributeMapping> getAttributeMappings(Class<?> type) {
            var typeAttributeMappings = unitMappings.attributeMappings.get(type);
            return typeAttributeMappings != null ? typeAttributeMappings : unitMappings.attributeMappings.computeIfAbsent(type, this::computeAttributeMappings);
        }

        private Map<String, AttributeMapping> computeAttributeMappings(Class<?> type) {
            var typeMappings = new LinkedHashMap<String, AttributeMapping>();

            for (var attribute : metamodel.entity(type).getAttributes()) {
                typeMappings.put(
                    attribute.getName(),
                    new AttributeMapping(
                        provider.isElementCollection(attribute), provider.isOneToMany(attribute), provider.isManyOrOneToOne(attribute), getNestedType(attribute)
                    )
                );
            }

            return unmodifiableMap(typeMappings);
        }

        private Class<?> getNestedType(Attribute<?, ?> attribute) {
            var javaType = provider.getBindableJavaType(attribute);
            return isEntity(javaType) ? javaType : null;
        }

        private Set<String> computeManyOrOneToOneMapping() {
            var mapping = new HashSet<String>();
            var visitedTypes = new HashSet<Class<?>>();
            visitedTypes.add(entityType);
            collectManyOrOneToOnes(entityType, "", visitedTypes, mapping);
            logger.log(FINE, () -> format(LOG_FINE_COMPUTED_MANY_OR_ONE_TO_ONE_MAPPING, entityType, mapping));
            return unmodifiableSet(mapping);
        }

        private void collectManyOrOneToOnes(Class<?> type, String basePath, Set<Class<?>> visitedTypes, Set<String> mapping) {
            for (var entry : getAttributeMappings(type).entrySet()) {
                var attribute = entry.getValue();

                if (attribute.manyOrOneToOne()) {
                    mapping.add(basePath + entry.getKey());
                }

                if (attribute.nestedType() != null && visitedTypes.add(attribute.nestedType())) {
                    collectManyOrOneToOnes(attribute.nestedType(), basePath + entry.getKey() + '.', visitedTypes, mapping);
                }
            }
        }

    }

    /**
     * The mappings of one persistence unit, shared by all entity types within it.
     */
    private static final class UnitMappings {

        private final Set<Class<?>> entityTypes;
        private final Map<Class<?>, Map<String, AttributeMapping>> attributeMappings = new ConcurrentHashMap<>();
        private final Map<Class<?>, IdAttribute> idAttributes = new ConcurrentHashMap<>();

        private UnitMappings(Metamodel metamodel) {
            entityTypes = metamodel.getEntities().stream().map(EntityType::getJavaType).collect(toUnmodifiableSet());
        }

    }

    /**
     * The association mappings of an entity type within one entity manager factory. This may not reference the entity manager factory, directly or via
     * metamodel objects, else it would never be released from the weak map.
     */
    private static final class FactoryMappings {

        private final Map<String, PathMapping> paths = new ConcurrentHashMap<>();
        private volatile Set<String> manyOrOneToOnes;

    }

    /**
     * The association mapping of an attribute.
     *
     * @param elementCollection Whether the attribute is an <code>@ElementCollection</code>.
     * @param oneToMany Whether the attribute is a <code>@OneToMany</code>.
     * @param manyOrOneToOne Whether the attribute is a <code>@ManyToOne</code> or <code>@OneToOne</code>.
     * @param nestedType The entity type the attribute refers to, or <code>null</code> if it does not refer to one.
     */
    private record AttributeMapping(boolean elementCollection, boolean oneToMany, boolean manyOrOneToOne, Class<?> nestedType) {
    }

    /**
     * The association mapping of a field path.
     *
     * @param resolved Whether every attribute in the path could be found, and each one but the last refers to an entity type. Only resolved paths are cached,
     * so that unknown field names cannot grow the cache.
     * @param elementCollection Whether the path ends in an <code>@ElementCollection</code> field.
     * @param nestedInOneToMany Whether the path is nested in a <code>@OneToMany</code> field.
     */
    private record PathMapping(boolean resolved, boolean elementCollection, boolean nestedInOneToMany) {

        private static final PathMapping UNRESOLVED = new PathMapping(false, false, false);
        private static final PathMapping UNRESOLVED_IN_ONE_TO_MANY = new PathMapping(false, false, true);

        static PathMapping unresolved(boolean nestedInOneToMany) {
            return nestedInOneToMany ? UNRESOLVED_IN_ONE_TO_MANY : UNRESOLVED;
        }

    }

}
