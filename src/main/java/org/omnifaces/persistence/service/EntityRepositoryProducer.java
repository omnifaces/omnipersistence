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

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.omnifaces.persistence.service.EntityRepository.SerializableSupplier;

/**
 * Produces an {@link EntityRepository} for the type arguments of its injection point, backed by the entity manager of the default persistence unit.
 */
@ApplicationScoped
class EntityRepositoryProducer {

    private static final String ERROR_UNRESOLVABLE_TYPE_ARGUMENTS = "%s must be injected with concrete type arguments, e.g. EntityRepository<Long, Person>,"
        + " but was %s";

    @PersistenceContext
    private EntityManager entityManager;

    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Produces
    @Dependent
    @SuppressWarnings("unchecked")
    static <I extends Comparable<I> & Serializable, E> EntityRepository<I, E> produce(InjectionPoint injectionPoint) {
        var typeArguments = resolveTypeArguments(injectionPoint.getType());
        return new EntityRepository<>(new DefaultEntityManagerSupplier(), (Class<I>) typeArguments[0], (Class<E>) typeArguments[1]);
    }

    static Class<?>[] resolveTypeArguments(Type injectionPointType) {
        if (injectionPointType instanceof ParameterizedType parameterizedType) {
            var typeArguments = parameterizedType.getActualTypeArguments();

            if (Arrays.stream(typeArguments).allMatch(Class.class::isInstance)) {
                return Arrays.copyOf(typeArguments, typeArguments.length, Class[].class);
            }
        }

        throw new IllegalArgumentException(format(ERROR_UNRESOLVABLE_TYPE_ARGUMENTS, EntityRepository.class.getSimpleName(), injectionPointType));
    }

    /**
     * Looks up the producer bean on first use, so that it holds no reference to it across serialization.
     */
    @Vetoed
    private static final class DefaultEntityManagerSupplier implements SerializableSupplier<EntityManager> {

        private static final long serialVersionUID = 1L;

        private transient volatile EntityManager entityManager;

        @Override
        public EntityManager get() {
            if (entityManager == null) {
                entityManager = CDI.current().select(EntityRepositoryProducer.class).get().getEntityManager();
            }

            return entityManager;
        }

    }

}
