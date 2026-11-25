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
package org.omnifaces.persistence.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.datasource.SwitchableXADataSource;

/**
 * Unit tests for {@link SwitchableXADataSource}. Each connection method must delegate to the same method of the wrapped data source, with the same arguments.
 */
class SwitchableXADataSourceTest {

    @Test
    void getXAConnectionWithoutCredentialsDelegatesToWrappedDataSource() throws Exception {
        var invocations = new ArrayList<String>();
        var arguments = new AtomicReference<Object[]>();
        var dataSource = initialized(invocations, arguments);

        dataSource.getXAConnection();

        assertEquals(List.of("getXAConnection"), invocations);
        assertNull(arguments.get());
    }

    @Test
    void getXAConnectionWithCredentialsPassesCredentialsToWrappedDataSource() throws Exception {
        var invocations = new ArrayList<String>();
        var arguments = new AtomicReference<Object[]>();
        var dataSource = initialized(invocations, arguments);

        dataSource.getXAConnection("user", "password");

        assertEquals(List.of("getXAConnection"), invocations);
        assertArrayEquals(new Object[] { "user", "password" }, arguments.get());
    }

    @Test
    void getXAConnectionReturnsConnectionOfWrappedDataSource() throws Exception {
        var connection = (XAConnection) Proxy
            .newProxyInstance(getClass().getClassLoader(), new Class<?>[] { XAConnection.class }, (proxy, method, args) -> null);
        var wrapped = (XADataSource) Proxy
            .newProxyInstance(getClass().getClassLoader(), new Class<?>[] { XADataSource.class }, (proxy, method, args) -> connection);
        var dataSource = new SwitchableXADataSource();
        dataSource.initDataSource(wrapped);

        assertSame(connection, dataSource.getXAConnection());
        assertSame(connection, dataSource.getXAConnection("user", "password"));
    }

    @Test
    void getWrappedReturnsXADataSource() {
        var wrapped = (XADataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { XADataSource.class }, (proxy, method, args) -> null);
        var dataSource = new SwitchableXADataSource();
        dataSource.initDataSource(wrapped);

        assertSame(wrapped, dataSource.getWrapped());
    }

    private static SwitchableXADataSource initialized(List<String> invocations, AtomicReference<Object[]> arguments) {
        var wrapped = (XADataSource) Proxy.newProxyInstance(
            SwitchableXADataSourceTest.class.getClassLoader(), new Class<?>[] { XADataSource.class }, (proxy, method, args) -> {
                invocations.add(method.getName());
                arguments.set(args);
                return null;
            }
        );
        var dataSource = new SwitchableXADataSource();
        dataSource.initDataSource(wrapped);
        return dataSource;
    }

}
