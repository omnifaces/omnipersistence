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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.inject.Provider;

/**
 * <p>
 * CDI extension which works around OpenWebBeans being unable to match an injection point against the producer of {@link EntityRepositoryProducer}, because the
 * type parameter of {@link EntityRepository} has a recursive bound.
 * <p>
 * On OpenWebBeans only, it vetoes that producer and registers instead a bean with concrete type arguments for each default {@link EntityRepository} injection
 * point of a bean. A programmatic lookup of an {@link EntityRepository} is therefore not supported on OpenWebBeans. On other CDI implementations this extension
 * does nothing.
 *
 * @author Bauke Scholtz
 * @since 1.3
 */
public final class EntityRepositoryExtension implements Extension {

    private static final String OPENWEBBEANS_PACKAGE = "org.apache.webbeans.";

    private boolean openWebBeans;
    private final Map<List<Class<?>>, Type> repositoryTypes = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     */
    public EntityRepositoryExtension() {
    }

    void detectOpenWebBeans(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
        openWebBeans = beanManager.getClass().getName().startsWith(OPENWEBBEANS_PACKAGE);
    }

    void vetoProducer(@Observes ProcessBeanAttributes<?> event) {
        if (
            openWebBeans && event.getAnnotated() instanceof AnnotatedMethod<?> method
                && method.getJavaMember().getDeclaringClass() == EntityRepositoryProducer.class
        ) {
            event.veto();
        }
    }

    void collectRepositoryTypes(@Observes ProcessInjectionPoint<?, ?> event) {
        var injectionPoint = event.getInjectionPoint();

        if (!openWebBeans || !injectionPoint.getQualifiers().contains(Default.Literal.INSTANCE)) {
            return;
        }

        var type = unwrapProvider(injectionPoint.getType());

        if (rawTypeOf(type) == EntityRepository.class) {
            try {
                repositoryTypes.putIfAbsent(List.of(EntityRepositoryProducer.resolveTypeArguments(type)), type);
            }
            catch (IllegalArgumentException e) {
                event.addDefinitionError(e);
            }
        }
    }

    void registerRepositories(@Observes AfterBeanDiscovery event) {
        repositoryTypes.forEach(
            (typeArguments, type) -> event.addBean()
                .id(EntityRepository.class.getName() + typeArguments)
                .beanClass(EntityRepository.class)
                .types(type, Object.class)
                .scope(Dependent.class)
                .produceWith(instance -> EntityRepositoryProducer.create(type))
        );

        repositoryTypes.clear();
    }

    private static Type unwrapProvider(Type type) {
        if (type instanceof ParameterizedType parameterizedType && Provider.class.isAssignableFrom(rawTypeOf(type))) {
            return parameterizedType.getActualTypeArguments()[0];
        }

        return type;
    }

    private static Class<?> rawTypeOf(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getRawType();
        }

        return type instanceof Class<?> rawType ? rawType : null;
    }

}
