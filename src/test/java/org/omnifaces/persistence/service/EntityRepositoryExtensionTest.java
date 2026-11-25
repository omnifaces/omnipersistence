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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.inject.Provider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.test.model.Person;

/**
 * Unit tests for {@link EntityRepositoryExtension}. The CDI events are stubbed with {@link Proxy} instances which record their invocations. OpenWebBeans
 * detection is set via reflection, because it depends on the package of the bean manager class. Registration of the beans by a real container is covered by the
 * integration tests on OpenWebBeans only.
 */
class EntityRepositoryExtensionTest {

    @SuppressWarnings({ "rawtypes", "unused" })
    static class InjectionPoints {

        EntityRepository<Long, Person> concrete;
        Provider<EntityRepository<Long, Person>> provider;
        EntityRepository raw;
        String other;

        void producerLookalike() {
        }

    }

    @Nested
    class DetectOpenWebBeans {

        @Test
        void otherBeanManagerLeavesExtensionInactive() {
            var extension = new EntityRepositoryExtension();
            extension.detectOpenWebBeans(stub(BeforeBeanDiscovery.class, Map.of(), new ArrayList<>()), stub(BeanManager.class, Map.of(), new ArrayList<>()));
            var calls = new ArrayList<String>();
            extension.collectRepositoryTypes(injectionPointEvent("concrete", Set.of(Default.Literal.INSTANCE), calls));
            assertEquals(List.of(), calls);
            assertEquals(List.of(), registerRepositories(extension));
        }

    }

    @Nested
    class VetoProducer {

        @Test
        void producerMethodIsVetoedOnOpenWebBeans() throws Exception {
            var calls = new ArrayList<String>();
            openWebBeansExtension().vetoProducer(producerEvent(EntityRepositoryProducer.class.getDeclaredMethod("produce", InjectionPoint.class), calls));
            assertEquals(List.of("veto"), calls);
        }

        @Test
        void otherMethodIsNotVetoedOnOpenWebBeans() throws Exception {
            var calls = new ArrayList<String>();
            openWebBeansExtension().vetoProducer(producerEvent(InjectionPoints.class.getDeclaredMethod("producerLookalike"), calls));
            assertEquals(List.of(), calls);
        }

        @Test
        void nonMethodIsNotVetoedOnOpenWebBeans() throws Exception {
            var calls = new ArrayList<String>();
            var annotated = stub(AnnotatedType.class, Map.of(), new ArrayList<>());
            ProcessBeanAttributes<?> event = stub(ProcessBeanAttributes.class, Map.of("getAnnotated", annotated), calls);
            openWebBeansExtension().vetoProducer(event);
            assertEquals(List.of(), calls);
        }

        @Test
        void producerMethodIsNotVetoedOnOtherCdiImplementations() throws Exception {
            var calls = new ArrayList<String>();
            new EntityRepositoryExtension()
                .vetoProducer(producerEvent(EntityRepositoryProducer.class.getDeclaredMethod("produce", InjectionPoint.class), calls));
            assertEquals(List.of(), calls);
        }

    }

    @Nested
    class CollectRepositoryTypes {

        @Test
        void defaultInjectionPointRegistersBeanWithConcreteTypeArguments() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("concrete", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));

            var calls = registerRepositories(extension).stream().map(call -> call.startsWith("produceWith") ? "produceWith" : call).toList();
            assertEquals(
                List.of(
                    "id[" + EntityRepository.class.getName() + List.of(Long.class, Person.class) + "]", "beanClass[" + EntityRepository.class + "]",
                    "types[" + typeOf("concrete") + ", " + Object.class + "]", "scope[" + Dependent.class + "]", "produceWith"
                ),
                calls
            );
        }

        @Test
        void providerInjectionPointIsUnwrapped() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("provider", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));

            assertEquals(typeOf("concrete") + ", " + Object.class, typesOf(registerRepositories(extension)));
        }

        @Test
        void sameTypeArgumentsRegisterOneBean() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("concrete", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));
            extension.collectRepositoryTypes(injectionPointEvent("provider", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));

            assertEquals(1, registerRepositories(extension).stream().filter(call -> call.startsWith("id[")).count());
        }

        @Test
        void qualifiedInjectionPointIsIgnored() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("concrete", Set.of(Any.Literal.INSTANCE), new ArrayList<>()));

            assertEquals(List.of(), registerRepositories(extension));
        }

        @Test
        void injectionPointOfOtherTypeIsIgnored() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("other", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));

            assertEquals(List.of(), registerRepositories(extension));
        }

        @Test
        void unresolvableTypeArgumentsAreDefinitionError() throws Exception {
            var extension = openWebBeansExtension();
            var calls = new ArrayList<String>();
            extension.collectRepositoryTypes(injectionPointEvent("raw", Set.of(Default.Literal.INSTANCE), calls));

            assertEquals(1, calls.size());
            assertTrue(calls.get(0).startsWith("addDefinitionError[java.lang.IllegalArgumentException"), calls.get(0));
            assertEquals(List.of(), registerRepositories(extension));
        }

    }

    @Nested
    class RegisterRepositories {

        @Test
        void collectedTypesAreRegisteredOnce() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("concrete", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));

            assertEquals(5, registerRepositories(extension).size());
            assertEquals(List.of(), registerRepositories(extension));
        }

        @Test
        void beanProducesEntityRepository() throws Exception {
            var extension = openWebBeansExtension();
            extension.collectRepositoryTypes(injectionPointEvent("concrete", Set.of(Default.Literal.INSTANCE), new ArrayList<>()));
            var producers = new ArrayList<Object>();
            var configurator = recorder(BeanConfigurator.class, Map.of(), new ArrayList<>(), producers);

            extension.registerRepositories(stub(AfterBeanDiscovery.class, Map.of("addBean", configurator), new ArrayList<>()));

            @SuppressWarnings("unchecked")
            var producer = (Function<Object, Object>) producers.get(0);
            assertInstanceOf(EntityRepository.class, producer.apply(null));
        }

    }

    private static EntityRepositoryExtension openWebBeansExtension() throws Exception {
        var extension = new EntityRepositoryExtension();
        var field = EntityRepositoryExtension.class.getDeclaredField("openWebBeans");
        field.setAccessible(true);
        field.setBoolean(extension, true);
        return extension;
    }

    private static List<String> registerRepositories(EntityRepositoryExtension extension) {
        var calls = new ArrayList<String>();
        var configurator = recorder(BeanConfigurator.class, Map.of(), calls, new ArrayList<>());
        extension.registerRepositories(stub(AfterBeanDiscovery.class, Map.of("addBean", configurator), new ArrayList<>()));
        return calls;
    }

    private static String typesOf(List<String> calls) {
        return calls.stream().filter(call -> call.startsWith("types[")).map(call -> call.substring("types[".length(), call.length() - 1)).findFirst()
            .orElseThrow();
    }

    private static ProcessBeanAttributes<?> producerEvent(Method method, List<String> calls) {
        var annotated = stub(AnnotatedMethod.class, Map.of("getJavaMember", method), new ArrayList<>());
        return stub(ProcessBeanAttributes.class, Map.of("getAnnotated", annotated), calls);
    }

    private static ProcessInjectionPoint<?, ?> injectionPointEvent(String field, Set<Annotation> qualifiers, List<String> calls) {
        var injectionPoint = stub(InjectionPoint.class, Map.of("getQualifiers", qualifiers, "getType", typeOf(field)), new ArrayList<>());
        return stub(ProcessInjectionPoint.class, Map.of("getInjectionPoint", injectionPoint), calls);
    }

    private static Type typeOf(String field) {
        try {
            return InjectionPoints.class.getDeclaredField(field).getGenericType();
        }
        catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<?> type, Map<String, Object> returnValues, List<String> calls) {
        return (T) recorder(type, returnValues, calls, new ArrayList<>());
    }

    /**
     * Returns a stub of the given interface which returns the given value per method name, or itself for a method returning the interface (a fluent API), and
     * records each other invocation as <code>name[arguments]</code> in the given calls, or just <code>name</code> without arguments. The argument of a
     * <code>produceWith</code> invocation is added to the given producers.
     */
    private static Object recorder(Class<?> type, Map<String, Object> returnValues, List<String> calls, List<Object> producers) {
        return Proxy.newProxyInstance(EntityRepositoryExtensionTest.class.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (returnValues.containsKey(method.getName())) {
                return returnValues.get(method.getName());
            }

            if (method.getName().equals("produceWith")) {
                producers.add(args[0]);
            }

            var arguments = args == null
                ? ""
                : "[" + Arrays.stream(args)
                    .map(arg -> arg instanceof Object[] array ? String.join(", ", Arrays.stream(array).map(String::valueOf).toList()) : String.valueOf(arg))
                    .reduce((a, b) -> a + ", " + b).orElse("") + "]";
            calls.add(method.getName() + arguments);
            return method.getReturnType() == type ? proxy : null;
        });
    }

}
