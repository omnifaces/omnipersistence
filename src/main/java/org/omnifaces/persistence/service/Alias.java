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

import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import org.omnifaces.persistence.Provider;

/**
 * Helper class of {@link BaseEntityService}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
class Alias {

    private static final String AS = "as_";
    private static final String WHERE = "where_";
    private static final String HAVING = "having_";

    private String value;

    private Alias(String alias) {
        this.value = alias;
    }

    public static Selection<?> as(Entry<String, Expression<?>> mappingEntry) {
        Selection<?> selection = mappingEntry.getValue();
        return selection.getAlias() != null ? selection : selection.alias(AS + mappingEntry.getKey().replace('.', '$'));
    }

    public static Alias create(Provider provider, Expression<?> expression, String field) {
        return new Alias((provider.isAggregation(expression) ? HAVING : WHERE) + field.replace('.', '$'));
    }

    /**
     * Puts the count directly after the prefix: a field name cannot start with a digit, so {@link #isIn(Predicate)} cannot mistake a field name for it.
     */
    public void in(int count) {
        var fieldStart = fieldStart(value);
        value = value.substring(0, fieldStart) + count + "_" + value.substring(fieldStart);
    }

    public void set(Predicate predicate) {
        predicate.alias(value);
    }

    public static boolean isWhere(Predicate predicate) {
        return predicate.getAlias().startsWith(WHERE);
    }

    public static boolean isIn(Predicate predicate) {
        var alias = predicate.getAlias();
        var fieldStart = fieldStart(alias);
        return fieldStart < alias.length() && Character.isDigit(alias.charAt(fieldStart));
    }

    public static boolean isHaving(Predicate predicate) {
        return predicate.getAlias().startsWith(HAVING);
    }

    public static Entry<String, Long> getFieldAndCount(Predicate inPredicate) {
        var alias = inPredicate.getAlias();
        var countAndField = alias.substring(fieldStart(alias));
        var countEnd = countAndField.indexOf('_');
        var count = Long.parseLong(countAndField.substring(0, countEnd));
        var field = countAndField.substring(countEnd + 1).replace('$', '.');
        return new SimpleEntry<>(field, count);
    }

    public static void setHaving(Predicate inPredicate, Predicate countPredicate) {
        var alias = inPredicate.getAlias();
        countPredicate.alias(HAVING + alias.substring(fieldStart(alias)));
    }

    private static int fieldStart(String alias) {
        return alias.indexOf('_') + 1;
    }

}
