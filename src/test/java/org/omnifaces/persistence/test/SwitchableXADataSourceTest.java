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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.XADataSource;

import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.datasource.SwitchableXADataSource;

/**
 * Unit tests for {@link SwitchableXADataSource}. Each connection method must delegate to the same method of the wrapped data source, with the same arguments.
 */
class SwitchableXADataSourceTest {

    @Test
    void getXAConnectionWithCredentialsPassesCredentialsToWrappedDataSource() throws Exception {
        var invocation = new AtomicReference<String>();
        var arguments = new AtomicReference<Object[]>();
        var wrapped = (XADataSource) Proxy.newProxyInstance(
            SwitchableXADataSourceTest.class.getClassLoader(), new Class<?>[] { XADataSource.class }, (proxy, method, args) -> {
                invocation.set(method.getName());
                arguments.set(args);
                return null;
            }
        );
        var dataSource = new SwitchableXADataSource();
        dataSource.initDataSource(wrapped);

        dataSource.getXAConnection("user", "password");

        assertEquals("getXAConnection", invocation.get());
        assertArrayEquals(new Object[] { "user", "password" }, arguments.get());
    }

}
