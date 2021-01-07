/*
 * Copyright 2021 OmniFaces
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

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Selection;

import org.omnifaces.persistence.Provider;

/**
 * Helper class of {@link BaseEntityService}.
 */
class Alias {

	private static final String AS = "as_";
	private static final String WHERE = "where_";
	private static final String HAVING = "having_";
	private static final String IN = "_in";

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

	public void in(int count) {
		value += "_" + count + IN;
	}

	public void set(Predicate predicate) {
		predicate.alias(value);
	}

	public static boolean isWhere(Predicate predicate) {
		return predicate.getAlias().startsWith(WHERE);
	}

	public static boolean isIn(Predicate predicate) {
		return predicate.getAlias().endsWith(IN);
	}

	public static boolean isHaving(Predicate predicate) {
		return predicate.getAlias().startsWith(HAVING);
	}

	public static Entry<String, Long> getFieldAndCount(Predicate inPredicate) {
		String alias = inPredicate.getAlias();
		String fieldAndCount = alias.substring(alias.indexOf('_') + 1, alias.lastIndexOf('_'));
		String field = fieldAndCount.substring(0, fieldAndCount.lastIndexOf('_')).replace('$', '.');
		long count = Long.valueOf(fieldAndCount.substring(field.length() + 1));
		return new SimpleEntry<>(field, count);
	}

	public static void setHaving(Predicate inPredicate, Predicate countPredicate) {
		countPredicate.alias(HAVING + inPredicate.getAlias().substring(inPredicate.getAlias().indexOf('_') + 1));
	}

}
