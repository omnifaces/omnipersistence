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
package org.omnifaces.persistence.query;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.Nullable;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.Root;

import org.omnifaces.persistence.Provider;
import org.omnifaces.persistence.service.BaseEntityService;
import org.omnifaces.persistence.service.BaseEntityService.CriteriaQueryBuilder;

/**
 * <p>
 * This class defines a selection of entities based on a Java Persistence Query Language statement, a {@link CriteriaQueryBuilder} or all entities, along with
 * query options. This is used by {@link BaseEntityService#find(Select)}, {@link BaseEntityService#findFirst(Select)} and
 * {@link BaseEntityService#list(Select)}.
 * <p>
 * The soft deleted entities are handled the same as in the corresponding <code>find</code>, <code>findFirst</code> and <code>list</code> methods: a
 * {@link #jpql(String, Object...)} selection does not exclude them, a {@link #criteria(CriteriaQueryBuilder, Map)} selection and an {@link #all()} selection
 * do.
 * <p>
 * Unlike {@link Page}, this class is not serializable, because the criteria query builder is usually a lambda.
 * <p>
 * Usage examples:
 *
 * <pre>
 *
 * List&lt;Foo&gt; foos = list(Select.jpql("WHERE e.bar = ?1 AND e.baz = ?2", bar, baz).cacheable(true).build());
 * Optional&lt;Foo&gt; foo = find(Select.jpql("WHERE e.bar = :bar", Map.of("bar", bar)).cacheable(true).build());
 * Optional&lt;Foo&gt; first = findFirst(Select.criteria((criteriaBuilder, query, root) -&gt; {
 *     query.where(criteriaBuilder.equal(root.get("type"), criteriaBuilder.parameter(Type.class, "type")));
 * }, Map.of("type", Type.FOO)).cacheable(true).build());
 * List&lt;Foo&gt; all = list(Select.all().cacheable(true).build());
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 2.0
 * @see BaseEntityService
 */
public final class Select { // This class MAY NOT be mutable!

    // Properties -----------------------------------------------------------------------------------------------------

    private final String jpql;
    private final Object[] positionalParameters;
    private final Map<String, Object> mappedParameters;
    private final CriteriaQueryBuilder<?> criteriaQueryBuilder;
    private final Boolean cacheable;

    // Constructors ---------------------------------------------------------------------------------------------------

    private Select(Builder builder) {
        jpql = builder.jpql;
        positionalParameters = builder.positionalParameters;
        mappedParameters = builder.mappedParameters;
        criteriaQueryBuilder = builder.criteriaQueryBuilder;
        cacheable = builder.cacheable;
    }

    // Getters --------------------------------------------------------------------------------------------------------

    /**
     * Returns the Java Persistence Query Language statement, or <code>null</code> when this is a criteria or all selection.
     *
     * @return The Java Persistence Query Language statement, or <code>null</code> when this is a criteria or all selection.
     */
    @Nullable
    public String getJpql() {
        return jpql;
    }

    /**
     * Returns a copy of the positional query parameters, or <code>null</code> when this is not a selection by Java Persistence Query Language statement and
     * positional parameters.
     *
     * @return A copy of the positional query parameters, or <code>null</code> when this is not a selection by Java Persistence Query Language statement and
     * positional parameters.
     */
    @Nullable
    public Object[] getPositionalParameters() {
        return positionalParameters == null ? null : positionalParameters.clone();
    }

    /**
     * Returns the unmodifiable mapped query parameters, or <code>null</code> when this is a selection by positional parameters or all.
     *
     * @return The unmodifiable mapped query parameters, or <code>null</code> when this is a selection by positional parameters or all.
     */
    @Nullable
    public Map<String, Object> getMappedParameters() {
        return mappedParameters;
    }

    /**
     * Returns the criteria query builder, or <code>null</code> when this is a Java Persistence Query Language or all selection.
     *
     * @return The criteria query builder, or <code>null</code> when this is a Java Persistence Query Language or all selection.
     */
    @Nullable
    public CriteriaQueryBuilder<?> getCriteriaQueryBuilder() {
        return criteriaQueryBuilder;
    }

    /**
     * Returns whether this is a selection of all entities.
     *
     * @return Whether this is a selection of all entities.
     */
    public boolean isAll() {
        return jpql == null && criteriaQueryBuilder == null;
    }

    /**
     * Returns whether the results should be cacheable, or <code>null</code> when the 2nd level cache settings of the persistence unit apply. See
     * {@link Builder#cacheable(boolean)}.
     *
     * @return Whether the results should be cacheable, or <code>null</code> when the 2nd level cache settings of the persistence unit apply.
     */
    @Nullable
    public Boolean getCacheable() {
        return cacheable;
    }

    // Object overrides -----------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder("Select[")
            .append(source()).append(",")
            .append(positionalParameters != null ? Arrays.toString(positionalParameters) : mappedParameters).append(",")
            .append(cacheable)
            .append("]").toString();
    }

    private String source() {
        if (isAll()) {
            return "all";
        }

        return jpql != null ? jpql : "criteria";
    }

    // Builder --------------------------------------------------------------------------------------------------------

    /**
     * Use this if you want to select entities by the given Java Persistence Query Language statement and positional parameters, if any. Short JPQL without
     * <code>SELECT</code> and <code>FROM</code> clauses is also supported, the entity is then available as <code>e</code>.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The positional query parameters, if any.
     * @return A new select builder.
     */
    public static Builder jpql(String jpql, Object... parameters) {
        var builder = new Builder();
        builder.jpql = requireNonNull(jpql, "jpql");
        builder.positionalParameters = requireNonNull(parameters, "parameters").clone();
        return builder;
    }

    /**
     * Use this if you want to select entities by the given Java Persistence Query Language statement and mapped parameters. Short JPQL without
     * <code>SELECT</code> and <code>FROM</code> clauses is also supported, the entity is then available as <code>e</code>.
     *
     * @param jpql The Java Persistence Query Language statement.
     * @param parameters The mapped query parameters.
     * @return A new select builder.
     */
    public static Builder jpql(String jpql, Map<String, Object> parameters) {
        var builder = new Builder();
        builder.jpql = requireNonNull(jpql, "jpql");
        builder.mappedParameters = unmodifiableMap(new LinkedHashMap<>(requireNonNull(parameters, "parameters")));
        return builder;
    }

    /**
     * Use this if you want to select entities by the given {@link CriteriaQueryBuilder} and mapped parameters. The type of the {@link Root} in the criteria
     * query builder is inferred as {@link Object}, unless given via an explicit type argument such as <code>Select.&lt;Foo&gt;criteria(...)</code> or an
     * explicitly typed lambda parameter. It must match the entity type of the service which executes the selection.
     *
     * @param <E> The generic entity type.
     * @param queryBuilder This creates the Jakarta Persistence criteria query.
     * @param parameters The mapped query parameters.
     * @return A new select builder.
     */
    public static <E> Builder criteria(CriteriaQueryBuilder<E> queryBuilder, Map<String, Object> parameters) {
        var builder = new Builder();
        builder.criteriaQueryBuilder = requireNonNull(queryBuilder, "queryBuilder");
        builder.mappedParameters = unmodifiableMap(new LinkedHashMap<>(requireNonNull(parameters, "parameters")));
        return builder;
    }

    /**
     * Use this if you want to select all entities, ordered by ID, descending. This does not include soft deleted entities.
     *
     * @return A new select builder.
     */
    public static Builder all() {
        return new Builder();
    }

    /**
     * The select builder. Use any of {@link Select#jpql(String, Object...)}, {@link Select#jpql(String, Map)},
     * {@link Select#criteria(CriteriaQueryBuilder, Map)} or {@link Select#all()} to get started.
     *
     * @author Bauke Scholtz
     * @since 2.0
     */
    public static final class Builder {

        private String jpql;
        private Object[] positionalParameters;
        private Map<String, Object> mappedParameters;
        private CriteriaQueryBuilder<?> criteriaQueryBuilder;
        private Boolean cacheable;

        private Builder() {
        }

        /**
         * Set whether the results should be cacheable. The query is then configured via {@link Provider#configureSecondLevelCache(Query, boolean)}: when
         * <code>true</code>, results are read from and stored in the 2nd level cache; when <code>false</code>, results are read from the DB and the cache is
         * force-refreshed. When not set, the 2nd level cache settings of the persistence unit apply.
         *
         * @param cacheable Whether the results should be cacheable.
         * @return This builder.
         */
        public Builder cacheable(boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        /**
         * Build the select.
         *
         * @return The built select.
         */
        public Select build() {
            return new Select(this);
        }

    }

}
