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

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.omnifaces.persistence.query.Select;
import org.omnifaces.persistence.service.BaseEntityService.CriteriaQueryBuilder;

/**
 * Unit tests for {@link Select}.
 */
public class SelectTest {

    @Nested
    class Sources {

        @Test
        void jpqlWithPositionalParameters() {
            var select = Select.jpql("WHERE e.id = ?1", 1L).build();
            assertEquals("WHERE e.id = ?1", select.getJpql());
            assertArrayEquals(new Object[] { 1L }, select.getPositionalParameters());
            assertNull(select.getMappedParameters());
            assertNull(select.getCriteriaQueryBuilder());
            assertFalse(select.isAll());
        }

        @Test
        void jpqlWithMappedParameters() {
            var select = Select.jpql("WHERE e.id = :id", Map.of("id", 1L)).build();
            assertEquals("WHERE e.id = :id", select.getJpql());
            assertEquals(Map.of("id", 1L), select.getMappedParameters());
            assertNull(select.getPositionalParameters());
            assertNull(select.getCriteriaQueryBuilder());
            assertFalse(select.isAll());
        }

        @Test
        void criteria() {
            CriteriaQueryBuilder<Object> queryBuilder = (criteriaBuilder, query, root) -> query.distinct(true);
            var select = Select.criteria(queryBuilder, Map.of("id", 1L)).build();
            assertSame(queryBuilder, select.getCriteriaQueryBuilder());
            assertEquals(Map.of("id", 1L), select.getMappedParameters());
            assertNull(select.getJpql());
            assertNull(select.getPositionalParameters());
            assertFalse(select.isAll());
        }

        @Test
        void all() {
            var select = Select.all().build();
            assertTrue(select.isAll());
            assertNull(select.getJpql());
            assertNull(select.getPositionalParameters());
            assertNull(select.getMappedParameters());
            assertNull(select.getCriteriaQueryBuilder());
        }

        @Test
        void nullSourceIsRejected() {
            assertThrows(NullPointerException.class, () -> Select.jpql(null, 1L));
            assertThrows(NullPointerException.class, () -> Select.jpql("WHERE e.id = :id", (Map<String, Object>) null));
            assertThrows(NullPointerException.class, () -> Select.criteria(null, Map.of()));
        }

    }

    @Nested
    class Cacheable {

        @Test
        void unsetIsNull() {
            assertNull(Select.all().build().getCacheable());
        }

        @Test
        void trueIsTrue() {
            assertEquals(Boolean.TRUE, Select.all().cacheable(true).build().getCacheable());
        }

        @Test
        void falseIsFalse() {
            assertEquals(Boolean.FALSE, Select.all().cacheable(false).build().getCacheable());
        }

    }

    @Nested
    class ToStringFormat {

        @Test
        void jpqlWithPositionalParameters() {
            assertEquals("Select[WHERE e.id = ?1,[1],null]", Select.jpql("WHERE e.id = ?1", 1L).build().toString());
        }

        @Test
        void jpqlWithMappedParameters() {
            assertEquals("Select[WHERE e.id = :id,{id=1},null]", Select.jpql("WHERE e.id = :id", Map.of("id", 1L)).build().toString());
        }

        @Test
        void criteria() {
            CriteriaQueryBuilder<Object> queryBuilder = (criteriaBuilder, query, root) -> query.distinct(true);
            assertEquals("Select[criteria,{id=1},null]", Select.criteria(queryBuilder, Map.of("id", 1L)).build().toString());
        }

        @Test
        void all() {
            assertEquals("Select[all,null,null]", Select.all().build().toString());
        }

        @Test
        void cacheableIsIncluded() {
            assertEquals("Select[all,null,true]", Select.all().cacheable(true).build().toString());
            assertEquals("Select[all,null,false]", Select.all().cacheable(false).build().toString());
        }

    }

    @Nested
    class WithoutParameters {

        @Test
        void jpqlWithoutPositionalParametersHasEmptyArray() {
            var select = Select.jpql("WHERE e.id > 0").build();
            assertEquals("WHERE e.id > 0", select.getJpql());
            assertArrayEquals(new Object[0], select.getPositionalParameters());
            assertNull(select.getMappedParameters());
            assertEquals("Select[WHERE e.id > 0,[],null]", select.toString());
        }

        @Test
        void jpqlWithEmptyMappedParametersHasEmptyMap() {
            var select = Select.jpql("WHERE e.id > 0", Map.of()).build();
            assertEquals(Map.of(), select.getMappedParameters());
            assertNull(select.getPositionalParameters());
            assertEquals("Select[WHERE e.id > 0,{},null]", select.toString());
        }

        @Test
        void criteriaWithEmptyMappedParametersHasEmptyMap() {
            CriteriaQueryBuilder<Object> queryBuilder = (criteriaBuilder, query, root) -> query.distinct(true);
            var select = Select.criteria(queryBuilder, Map.of()).build();
            assertEquals(Map.of(), select.getMappedParameters());
            assertEquals("Select[criteria,{},null]", select.toString());
        }

    }

    @Nested
    class Immutability {

        @Test
        void positionalParametersAreCopiedOnBuild() {
            var parameters = new Object[] { 1L };
            var select = Select.jpql("WHERE e.id = ?1", parameters).build();
            parameters[0] = 2L;
            assertArrayEquals(new Object[] { 1L }, select.getPositionalParameters());
        }

        @Test
        void positionalParametersAreCopiedOnGet() {
            var select = Select.jpql("WHERE e.id = ?1", 1L).build();
            select.getPositionalParameters()[0] = 2L;
            assertArrayEquals(new Object[] { 1L }, select.getPositionalParameters());
        }

        @Test
        void mappedParametersAreCopiedOnBuild() {
            var parameters = new HashMap<String, Object>(Map.of("id", 1L));
            var select = Select.jpql("WHERE e.id = :id", parameters).build();
            parameters.put("id", 2L);
            assertEquals(Map.of("id", 1L), select.getMappedParameters());
        }

        @Test
        void mappedParametersAreUnmodifiable() {
            var select = Select.jpql("WHERE e.id = :id", Map.of("id", 1L)).build();
            var mappedParameters = select.getMappedParameters();
            assertThrows(UnsupportedOperationException.class, () -> mappedParameters.put("id", 2L));
        }

        @Test
        void mappedParametersMayHoldNullValue() {
            var select = Select.jpql("WHERE e.name = :name", singletonMap("name", null)).build();
            assertEquals(singletonMap("name", null), select.getMappedParameters());
        }

    }

}
