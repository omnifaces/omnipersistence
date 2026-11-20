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
package org.omnifaces.persistence;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.CDIProvider;
import jakarta.enterprise.util.TypeLiteral;

/**
 * A {@link CDIProvider} for unit tests. {@link CDI#current()} throws {@link IllegalStateException}, as outside a CDI container, until it is made available. The
 * events fired via the {@link BeanManager} of its {@link CDI} are recorded, unless they are set to fail as a throwing observer would.
 * {@link CDI#setCDIProvider(CDIProvider)} cannot be undone, so a test sets it back to unavailable when done.
 */
public final class RecordingCDIProvider implements CDIProvider {

    private static final RecordingCDIProvider INSTANCE = new RecordingCDIProvider();

    private final List<Object> firedEvents = new CopyOnWriteArrayList<>();
    private final CDI<Object> cdi = new RecordingCDI();
    private volatile boolean available;
    private volatile Predicate<Object> failing = event -> false;

    private RecordingCDIProvider() {
        // Singleton.
    }

    /**
     * Installs the provider as unavailable and without recorded events.
     *
     * @return The installed provider.
     */
    public static RecordingCDIProvider install() {
        INSTANCE.available = false;
        INSTANCE.failing = event -> false;
        INSTANCE.firedEvents.clear();
        CDI.setCDIProvider(INSTANCE);
        return INSTANCE;
    }

    /**
     * Sets whether {@link CDI#current()} is available.
     *
     * @param available Whether {@link CDI#current()} is available.
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * Sets which fired events throw {@link IllegalStateException}, as a throwing observer would. Those are not recorded.
     *
     * @param failing Which fired events throw.
     */
    public void setFailing(Predicate<Object> failing) {
        this.failing = failing;
    }

    /**
     * Returns the events fired so far.
     *
     * @return The events fired so far.
     */
    public List<Object> getFiredEvents() {
        return firedEvents;
    }

    @Override
    public CDI<Object> getCDI() {
        if (!available) {
            throw new IllegalStateException("CDI is not available.");
        }

        return cdi;
    }

    private class RecordingCDI extends CDI<Object> {

        private final BeanManager beanManager = (BeanManager) Proxy.newProxyInstance(
            BeanManager.class.getClassLoader(), new Class<?>[] { BeanManager.class }, (proxy, method, args) -> {
                if ("getEvent".equals(method.getName())) {
                    return recordingEvent();
                }

                throw new UnsupportedOperationException(method.getName());
            }
        );

        @SuppressWarnings("unchecked")
        private Event<Object> recordingEvent() {
            return (Event<Object>) Proxy.newProxyInstance(Event.class.getClassLoader(), new Class<?>[] { Event.class }, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "select" :
                        return proxy;
                    case "fire" :
                        if (failing.test(args[0])) {
                            throw new IllegalStateException("Observer failed.");
                        }

                        firedEvents.add(args[0]);
                        return null;
                    default :
                        throw new UnsupportedOperationException(method.getName());
                }
            });
        }

        @Override
        public BeanManager getBeanManager() {
            return beanManager;
        }

        @Override
        public Instance<Object> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAmbiguous() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy(Object instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Handle<Object> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<Object>> handles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<Object> iterator() {
            throw new UnsupportedOperationException();
        }

    }

}
