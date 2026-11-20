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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.persistence.Provider.ECLIPSELINK;
import static org.omnifaces.persistence.service.Stubs.stub;

import java.lang.reflect.Proxy;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Alias}: the alias of a criteria predicate carries its field name, and for an IN predicate also its count, whatever the field name.
 */
class AliasTest {

    @Test
    void whereAliasIsNotInWhenFieldNameEndsWithIn() {
        var predicate = aliased("check_in", null);
        assertTrue(Alias.isWhere(predicate));
        assertFalse(Alias.isIn(predicate));
    }

    @Test
    void whereAliasIsNotInWhenFieldNameIsEmpty() {
        var predicate = aliased("", null);
        assertTrue(Alias.isWhere(predicate));
        assertFalse(Alias.isIn(predicate));
    }

    @Test
    void inAliasCarriesFieldAndCount() {
        for (var field : new String[] { "name", "check_in", "first_name", "address.street_in" }) {
            var predicate = aliased(field, 3);
            assertTrue(Alias.isWhere(predicate), field);
            assertTrue(Alias.isIn(predicate), field);
            assertEquals(new SimpleEntry<>(field, 3L), Alias.getFieldAndCount(predicate), field);
        }
    }

    @Test
    void havingAliasOfInKeepsFieldAndCount() {
        var predicate = aliased("check_in", 2);
        var countPredicate = predicate();
        Alias.setHaving(predicate, countPredicate);
        assertTrue(Alias.isHaving(countPredicate));
        assertEquals(new SimpleEntry<>("check_in", 2L), Alias.getFieldAndCount(countPredicate));
    }

    private static Predicate aliased(String field, Integer inCount) {
        var alias = Alias.create(ECLIPSELINK, stub(Expression.class, Map.of()), field);

        if (inCount != null) {
            alias.in(inCount);
        }

        var predicate = predicate();
        alias.set(predicate);
        return predicate;
    }

    private static Predicate predicate() {
        var alias = new String[1];
        return (Predicate) Proxy.newProxyInstance(Predicate.class.getClassLoader(), new Class<?>[] { Predicate.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "alias" :
                    alias[0] = (String) args[0];
                    return proxy;
                case "getAlias" :
                    return alias[0];
                default :
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }

}
