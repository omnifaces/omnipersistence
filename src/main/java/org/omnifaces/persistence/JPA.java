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

import static java.util.Optional.ofNullable;
import static org.omnifaces.persistence.Database.DB2;
import static org.omnifaces.persistence.Database.POSTGRESQL;
import static org.omnifaces.persistence.Database.SQLSERVER;
import static org.omnifaces.persistence.Provider.HIBERNATE;
import static org.omnifaces.persistence.service.BaseEntityService.getCurrentBaseEntityService;
import static org.omnifaces.utils.reflect.Reflections.findMethod;
import static org.omnifaces.utils.reflect.Reflections.invokeMethod;
import static org.omnifaces.utils.stream.Collectors.toMap;
import static org.omnifaces.utils.stream.Streams.stream;

import java.lang.reflect.AnnotatedElement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.enterprise.inject.Typed;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

import org.omnifaces.persistence.criteria.Criteria.ParameterBuilder;
import org.omnifaces.persistence.criteria.Numeric;
import org.omnifaces.persistence.service.BaseEntityService;

/**
 * <p>
 * Jakarta Persistence utilities for common operations such as getting optional/first results, converting results to maps, counting foreign key references, and
 * building criteria expressions.
 * <p>
 * Usage examples:
 *
 * <pre>
 *
 * // Get an optional single result from a typed query.
 * Optional&lt;Foo&gt; foo = JPA.findSingleResult(typedQuery);
 *
 * // Get first result, ignoring duplicates.
 * Optional&lt;Foo&gt; foo = JPA.findFirstResult(typedQuery);
 *
 * // Convert result list to a map.
 * Map&lt;Long, Foo&gt; fooById = JPA.getResultMap(typedQuery, Foo::getId);
 *
 * // Count all foreign key references to an entity.
 * long refs = JPA.countForeignKeyReferences(entityManager, Foo.class, Long.class, fooId);
 * </pre>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see BaseEntityService
 */
@Typed
public final class JPA {

    // Public constants -------------------------------------------------------------------------------------------------------------------

    /** Returns the Jakarta Persistence-specific "load graph" query hint constant: {@value}. */
    public static final String QUERY_HINT_LOAD_GRAPH = "jakarta.persistence.loadgraph";
    /** Returns the Jakarta Persistence-specific "fetch graph" query hint constant: {@value}. */
    public static final String QUERY_HINT_FETCH_GRAPH = "jakarta.persistence.fetchgraph";
    /** Returns the Jakarta Persistence-specific "2nd level cache store mode" query hint constant: {@value}. */
    public static final String QUERY_HINT_CACHE_STORE_MODE = "jakarta.persistence.cache.storeMode"; // USE | BYPASS | REFRESH
    /** Returns the Jakarta Persistence-specific "2nd level cache retrieve mode" query hint constant: {@value}. */
    public static final String QUERY_HINT_CACHE_RETRIEVE_MODE = "jakarta.persistence.cache.retrieveMode"; // USE | BYPASS
    /** Returns the Jakarta Persistence-specific "validation mode" persistence.xml property: {@value}. */
    public static final String PROPERTY_VALIDATION_MODE = "jakarta.persistence.validation.mode"; // AUTO | CALLBACK | NONE

    /**
     * The escape character of {@link #escapeLike(String)}, of {@link #escapeLikePattern(String)} and of <code>Like.pattern(String)</code>: {@value}.
     *
     * @since 2.0
     */
    public static final char LIKE_ESCAPE = '\\';

    // Private constants ------------------------------------------------------------------------------------------------------------------

    private static final String LIKE_SPECIAL_CHARACTERS = "%_" + LIKE_ESCAPE;
    private static final String DATE_PATTERN = "YYYY-MM-DD";
    private static final String TIME_PATTERN = "HH24:MI:SS";
    private static final String DATE_TIME_PATTERN = DATE_PATTERN + " " + TIME_PATTERN;
    private static final String ERROR_NO_CURRENT_BASE_ENTITY_SERVICE = "JPA#%s() must be invoked during a public method of a BaseEntityService.";

    // Constructors -----------------------------------------------------------------------------------------------------------------------

    private JPA() {
        throw new AssertionError();
    }

    // Configuration utils ----------------------------------------------------------------------------------------------------------------

    /**
     * Returns the currently configured bean validation mode for given entity manager. This consults the {@value #PROPERTY_VALIDATION_MODE} property in
     * <code>persistence.xml</code>.
     *
     * @param entityManager The involved entity manager.
     * @return The currently configured bean validation mode.
     */
    public static ValidationMode getValidationMode(EntityManager entityManager) {
        var validationMode = entityManager.getEntityManagerFactory().getProperties().get(PROPERTY_VALIDATION_MODE);
        return validationMode != null ? ValidationMode.valueOf(validationMode.toString().toUpperCase()) : ValidationMode.AUTO;
    }

    // Query utils ------------------------------------------------------------------------------------------------------------------------

    /**
     * Returns single result of given typed query as {@link Optional}.
     *
     * @param <T> The generic result type.
     * @param typedQuery The involved typed query.
     * @return Single result of given typed query as {@link Optional}.
     * @throws NonUniqueResultException When there is no unique result.
     */
    public static <T> Optional<T> findSingleResult(TypedQuery<T> typedQuery) {
        return ofNullable(getSingleResultOrNull(typedQuery));
    }

    /**
     * Returns single result of given query as {@link Optional}.
     *
     * @param <T> The expected result type.
     * @param query The involved query.
     * @return Single result of given query as {@link Optional}.
     * @throws NonUniqueResultException When there is no unique result.
     * @throws ClassCastException When <code>T</code> is of wrong type.
     */
    public static <T> Optional<T> findSingleResult(Query query) {
        return ofNullable(getSingleResultOrNull(query));
    }

    /**
     * Returns single result of given typed query, or <code>null</code> if there is none.
     *
     * @param <T> The generic result type.
     * @param typedQuery The involved typed query.
     * @return Single result of given typed query, or <code>null</code> if there is none.
     * @throws NonUniqueResultException When there is no unique result.
     */
    @Nullable
    public static <T> T getSingleResultOrNull(TypedQuery<T> typedQuery) {
        try {
            return typedQuery.getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Returns single result of given query, or <code>null</code> if there is none.
     *
     * @param <T> The expected result type.
     * @param query The involved query.
     * @return Single result of given query, or <code>null</code> if there is none.
     * @throws NonUniqueResultException When there is no unique result.
     * @throws ClassCastException When <code>T</code> is of wrong type.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getSingleResultOrNull(Query query) {
        try {
            return (T) query.getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Returns first result of given typed query as {@link Optional}. The difference with {@link #findSingleResult(TypedQuery)} is that it doesn't throw
     * {@link NonUniqueResultException} when there are multiple matches.
     *
     * @param <T> The generic result type.
     * @param typedQuery The involved typed query.
     * @return First result of given typed query as {@link Optional}.
     */
    public static <T> Optional<T> findFirstResult(TypedQuery<T> typedQuery) {
        typedQuery.setMaxResults(1);
        return typedQuery.getResultList().stream().findFirst();
    }

    /**
     * Returns first result of given query as {@link Optional}. The difference with {@link #findSingleResult(Query)} is that it doesn't throw
     * {@link NonUniqueResultException} when there are multiple matches.
     *
     * @param <T> The expected result type.
     * @param query The involved query.
     * @return First result of given query as {@link Optional}.
     * @throws ClassCastException When <code>T</code> is of wrong type.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> findFirstResult(Query query) {
        query.setMaxResults(1);
        return query.getResultList().stream().findFirst();
    }

    /**
     * Returns first result of given typed query, or <code>null</code> if there is none. The difference with {@link #getSingleResultOrNull(TypedQuery)} is that
     * it doesn't throw {@link NonUniqueResultException} when there are multiple matches.
     *
     * @param <T> The generic result type.
     * @param typedQuery The involved typed query.
     * @return First result of given typed query, or <code>null</code> if there is none.
     */
    @Nullable
    public static <T> T getFirstResultOrNull(TypedQuery<T> typedQuery) {
        return findFirstResult(typedQuery).orElse(null);
    }

    /**
     * Returns first result of given query, or <code>null</code> if there is none. The difference with {@link #getSingleResultOrNull(Query)} is that it doesn't
     * throw {@link NonUniqueResultException} when there are multiple matches.
     *
     * @param <T> The expected result type.
     * @param query The involved query.
     * @return First result of given query, or <code>null</code> if there is none.
     * @throws ClassCastException When <code>T</code> is of wrong type.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getFirstResultOrNull(Query query) {
        return (T) findFirstResult(query).orElse(null);
    }

    /**
     * Returns the result list of given typed query as a map mapped by the given key mapper.
     *
     * @param <K> The generic map key type.
     * @param <T> The generic result type, also map value type.
     * @param typedQuery The involved typed query.
     * @param keyMapper The key mapper.
     * @return The result list of given typed query as a map mapped by the given key mapper.
     */
    public static <K, T> Map<K, T> getResultMap(TypedQuery<T> typedQuery, Function<? super T, ? extends K> keyMapper) {
        return typedQuery.getResultList().stream().collect(toMap(keyMapper));
    }

    /**
     * Returns the result list of given typed query as a map mapped by the given key and value mappers.
     *
     * @param <K> The generic map key type.
     * @param <T> The generic result type.
     * @param <V> The generic map value type.
     * @param typedQuery The involved typed query.
     * @param keyMapper The key mapper.
     * @param valueMapper The value mapper.
     * @return The result list of given typed query as a map mapped by the given key and value mappers.
     */
    public static <K, T, V> Map<K, V> getResultMap(
        TypedQuery<T> typedQuery, Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper
    )
    {
        return typedQuery.getResultList().stream().collect(Collectors.toMap(keyMapper, valueMapper));
    }

    // Entity utils -----------------------------------------------------------------------------------------------------------------------

    /**
     * Returns count of all foreign key references to entity of given entity type with given ID of given identifier type. This is particularly useful in case
     * you intend to check if the given entity is still referenced elsewhere in database.
     * <p>
     * A reference is an association whose target is the given entity type or a supertype of it, also a singular association nested in an embeddable, but not a
     * plural association nested in an embeddable. Each association counts once per referencing row, also in an entity hierarchy, and a plural association
     * counts each row of its join. Give the actual class of the entity, because an association to a subclass of the given entity type is not counted.
     *
     * @param <T> The generic result type.
     * @param <I> The generic identifier type.
     * @param entityManager The involved entity manager.
     * @param entityType Entity type.
     * @param identifierType Identifier type.
     * @param id Entity ID.
     * @return Count of all foreign key references to entity of given entity type with given ID of given identifier type.
     */
    public static <T, I> long countForeignKeyReferences(EntityManager entityManager, Class<T> entityType, Class<I> identifierType, I id) {
        var metamodel = entityManager.getMetamodel();
        var idName = metamodel.entity(entityType).getId(identifierType).getName();
        var count = 0L;

        for (var entity : metamodel.getEntities()) {
            for (var reference : findReferences(entity, entityType)) {
                count += countReferences(entityManager, entity, reference, idName, id);
            }
        }

        return count;
    }

    /**
     * Returns the attribute paths of given entity which reference given entity type, skipping those which the query on an entity supertype already counts.
     */
    private static List<List<Attribute<?, ?>>> findReferences(EntityType<?> entity, Class<?> entityType) {
        var references = new ArrayList<List<Attribute<?, ?>>>();

        for (var attribute : entity.getAttributes()) {
            if (!isInheritedFromEntity(entity, attribute)) {
                collectReferences(List.of(attribute), entityType, references);
            }
        }

        return references;
    }

    private static boolean isInheritedFromEntity(EntityType<?> entity, Attribute<?, ?> attribute) {
        for (IdentifiableType<?> supertype = entity.getSupertype(); supertype != null; supertype = supertype.getSupertype()) {
            if (supertype instanceof EntityType && supertype.getAttributes().stream().anyMatch(inherited -> inherited.getName().equals(attribute.getName()))) {
                return true;
            }
        }

        return false;
    }

    private static void collectReferences(List<Attribute<?, ?>> path, Class<?> entityType, List<List<Attribute<?, ?>>> references) {
        var attribute = path.get(path.size() - 1);
        var target = attribute instanceof PluralAttribute<?, ?, ?> plural ? plural.getElementType() : ((SingularAttribute<?, ?>) attribute).getType();

        if (target instanceof EntityType && target.getJavaType().isAssignableFrom(entityType)) {
            references.add(path);
        }
        else if (target instanceof EmbeddableType<?> embeddable && attribute instanceof SingularAttribute) {
            for (var nested : embeddable.getAttributes()) {
                if (nested instanceof SingularAttribute) {
                    var nestedPath = new ArrayList<>(path);
                    nestedPath.add(nested);
                    collectReferences(nestedPath, entityType, references);
                }
            }
        }
    }

    private static long countReferences(EntityManager entityManager, EntityType<?> entity, List<Attribute<?, ?>> reference, String idName, Object id) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var query = criteriaBuilder.createQuery(Long.class);
        var root = query.from(entity);
        var last = reference.size() - 1;
        Path<?> referenced;

        if (last == 0) {
            referenced = root.join(reference.get(last).getName());
        }
        else {
            Path<?> embedded = root;

            for (var attribute : reference.subList(0, last)) {
                embedded = embedded.get(attribute.getName());
            }

            referenced = embedded.get(reference.get(last).getName());
        }

        query.select(criteriaBuilder.count(root)).where(criteriaBuilder.equal(referenced.get(idName), id));
        return entityManager.createQuery(query).getSingleResult();
    }

    // Criteria utils ---------------------------------------------------------------------------------------------------------------------

    /**
     * Returns a SQL CONCAT(...) of given expressions or strings.
     *
     * @param builder The involved criteria builder.
     * @param expressionsOrStrings Expressions or Strings.
     * @return A SQL CONCAT(...) of given expressions or strings.
     * @throws IllegalArgumentException When there are less than 2 expressions or strings. There's no point of concat then.
     */
    public static Expression<String> concat(CriteriaBuilder builder, Object... expressionsOrStrings) {
        if (expressionsOrStrings.length < 2) {
            throw new IllegalArgumentException("There must be at least 2 expressions or strings");
        }

        List<Expression<String>> expressions = stream(expressionsOrStrings).map(expressionOrString -> {
            if (expressionOrString instanceof Expression<?> expression) {
                return castAsString(builder, expression);
            }
            else {
                return builder.literal(expressionOrString.toString());
            }
        }).toList();

        Expression<String> result = expressions.get(0);

        for (int i = 1; i < expressions.size(); i++) {
            result = builder.concat(result, expressions.get(i));
        }

        return result;
    }

    /**
     * Returns given value with the characters which <code>LIKE</code> interprets as wildcard escaped by {@link #LIKE_ESCAPE}, so that it matches literally when
     * passed with {@link #LIKE_ESCAPE} as escape character, e.g. to {@link CriteriaBuilder#like(Expression, Expression, char)} or to
     * <code>LIKE ... ESCAPE</code> in JPQL. These are <code>%</code>, <code>_</code> and {@link #LIKE_ESCAPE} itself, and on SQL Server also <code>[</code>.
     * The database is that of the {@link BaseEntityService#getCurrentBaseEntityService()}.
     *
     * @param value The value to be escaped.
     * @return The escaped value.
     * @throws IllegalStateException When not invoked during a public method of a {@link BaseEntityService}.
     * @since 2.0
     */
    public static String escapeLike(String value) {
        return escape(value, LIKE_SPECIAL_CHARACTERS + getDatabaseLikeSpecialCharacters("escapeLike"));
    }

    /**
     * Returns given <code>LIKE</code> pattern escaped so that it means the same on every database when passed with {@link #LIKE_ESCAPE} as escape character:
     * <code>%</code> and <code>_</code> are wildcards, {@link #LIKE_ESCAPE} escapes the next character, a trailing {@link #LIKE_ESCAPE} matches itself, and any
     * other character matches itself, also <code>[</code> on SQL Server. The database is that of the {@link BaseEntityService#getCurrentBaseEntityService()}.
     *
     * @param pattern The pattern to be escaped.
     * @return The escaped pattern.
     * @throws IllegalStateException When not invoked during a public method of a {@link BaseEntityService}.
     * @since 2.0
     */
    public static String escapeLikePattern(String pattern) {
        var specialCharacters = getDatabaseLikeSpecialCharacters("escapeLikePattern");
        var escapedCharacters = LIKE_SPECIAL_CHARACTERS + specialCharacters;
        var escaped = new StringBuilder(pattern.length());

        for (var i = 0; i < pattern.length(); i++) {
            var character = pattern.charAt(i);

            if (character == LIKE_ESCAPE) {
                appendEscapedIfNecessary(escaped, i + 1 < pattern.length() ? pattern.charAt(++i) : LIKE_ESCAPE, escapedCharacters);
            }
            else {
                appendEscapedIfNecessary(escaped, character, specialCharacters);
            }
        }

        return escaped.toString();
    }

    private static String getDatabaseLikeSpecialCharacters(String methodName) {
        return requireCurrentBaseEntityService(methodName).getDatabase().getLikeSpecialCharacters();
    }

    private static void appendEscapedIfNecessary(StringBuilder escaped, char character, String charactersToEscape) {
        if (charactersToEscape.indexOf(character) >= 0) {
            escaped.append(LIKE_ESCAPE);
        }

        escaped.append(character);
    }

    /**
     * Returns a new predicate which matches nothing. It compares a literal with a parameter, so that every provider renders it in every clause.
     *
     * @param builder The involved criteria builder.
     * @param parameterBuilder The involved parameter builder.
     * @return A new predicate which matches nothing.
     * @since 2.0
     */
    public static Predicate matchNothing(CriteriaBuilder builder, ParameterBuilder parameterBuilder) {
        return builder.notEqual(builder.literal(1), parameterBuilder.create(1));
    }

    /**
     * Returns a new expression which applies the SQL <code>LOWER</code> function on given expression. This also works on an element of an
     * <code>@ElementCollection</code> in a correlated subquery on OpenJPA, which {@link CriteriaBuilder#lower(Expression)} does not.
     *
     * @param builder The involved criteria builder.
     * @param expression Expression to be lowercased.
     * @return A new expression wherein given expression is lowercased.
     * @since 2.0
     */
    public static Expression<String> lower(CriteriaBuilder builder, Expression<String> expression) {
        return builder.function("LOWER", String.class, expression);
    }

    /**
     * Returns a new expression wherein given expression is cast as String. This covers known problems with certain providers and/or databases, which are those
     * of the {@link BaseEntityService#getCurrentBaseEntityService()}.
     *
     * @param builder The involved criteria builder.
     * @param expression Expression to be cast as String.
     * @return A new expression wherein given expression is cast as String.
     * @throws IllegalStateException When not invoked during a public method of a {@link BaseEntityService}.
     */
    @SuppressWarnings("unchecked")
    public static Expression<String> castAsString(CriteriaBuilder builder, Expression<?> expression) {

        // TODO: Improvement for all providers is expected in JPA 3.2 with new Expression#cast() API.

        var service = requireCurrentBaseEntityService("castAsString");
        var type = expression.getJavaType();

        // DB2 casts a temporal value as "2026-11-14-13.14.15.000000", whatever the provider.
        if (service.getDatabase() == DB2) {
            var pattern = getDB2TemporalPattern(type);

            if (pattern != null) {
                var timestamp = isTimeType(type) ? asDB2Timestamp(builder, expression) : expression;
                return builder.function("VARCHAR_FORMAT", String.class, timestamp, builder.literal(pattern));
            }
        }

        if (service.getProvider() == HIBERNATE) {
            var cast = findMethod(expression, "cast", Class.class);

            if (cast.isPresent()) { // Hibernate 6.6.0.
                return invokeMethod(expression, cast.get(), String.class); // https://hibernate.atlassian.net/browse/HHH-18710
            }
            else {
                return expression.as(String.class);
            }
        }

        // EclipseLink and OpenJPA have a broken Expression#as() implementation, need to delegate to DB specific function.

        // PostgreSQL is quite strict in string casting, it has to be performed explicitly.
        if (service.getDatabase() == POSTGRESQL) {
            if (Numeric.is(type) || OffsetTime.class.isAssignableFrom(type)) {
                return builder.function("TEXT", String.class, expression); // PostgreSQL has no TO_CHAR(timetz), and TO_CHAR(numeric) needs a fixed scale.
            }

            var pattern = getPostgreSQLPattern(type);

            if (pattern != null) {
                return builder.function("TO_CHAR", String.class, expression, builder.literal(pattern));
            }
        }

        // SQL Server casts a DATETIME as "Nov 14 2026 1:14PM".
        if (service.getDatabase() == SQLSERVER && LocalDateTime.class.isAssignableFrom(type)) {
            return builder.function("FORMAT", String.class, expression, builder.literal("yyyy-MM-dd HH:mm:ss"));
        }

        // H2 and MySQL are more lenient in this, they can do implicit casting with sane defaults, so no custom function call necessary.
        return (Expression<String>) expression;
    }

    /**
     * Returns given time expression as a DB2 timestamp on the current date, because DB2 has no <code>VARCHAR_FORMAT(TIME)</code>. DB2 resolves
     * <code>TIMESTAMP(DATE, TIME)</code> only with typed arguments, so the date is <code>CURRENT_DATE</code>. The <code>TIME</code> function also accepts a
     * <code>TIMESTAMP</code> column, as mapped for <code>OffsetTime</code> by some providers.
     *
     * @param builder The involved criteria builder.
     * @param expression Time expression to be converted.
     * @return Given time expression as a DB2 timestamp on the current date.
     */
    private static Expression<?> asDB2Timestamp(CriteriaBuilder builder, Expression<?> expression) {
        return builder.function("TIMESTAMP", LocalDateTime.class, builder.currentDate(), builder.function("TIME", LocalTime.class, expression));
    }

    @Nullable
    private static String getDB2TemporalPattern(Class<?> type) {
        if (isTimeType(type)) {
            return TIME_PATTERN;
        }
        else if (LocalDateTime.class.isAssignableFrom(type) || OffsetDateTime.class.isAssignableFrom(type)) {
            return DATE_TIME_PATTERN;
        }
        else {
            return null;
        }
    }

    private static boolean isTimeType(Class<?> type) {
        return LocalTime.class.isAssignableFrom(type) || OffsetTime.class.isAssignableFrom(type);
    }

    @Nullable
    private static String getPostgreSQLPattern(Class<?> type) {
        if (LocalDate.class.isAssignableFrom(type)) {
            return DATE_PATTERN;
        }
        else if (LocalTime.class.isAssignableFrom(type)) {
            return TIME_PATTERN;
        }
        else if (LocalDateTime.class.isAssignableFrom(type)) {
            return DATE_TIME_PATTERN;
        }
        else if (OffsetDateTime.class.isAssignableFrom(type)) {
            return DATE_TIME_PATTERN + "OF";
        }
        else {
            return null;
        }
    }

    /**
     * Returns whether given path is {@link Enumerated} by {@link EnumType#ORDINAL}. This is also the case for an enum attribute, or a collection of enums,
     * without {@link Enumerated} and without {@link Convert}, because {@link EnumType#ORDINAL} is the default of {@link Enumerated}. An enum attribute
     * converted by an auto applied {@link Converter} is not detected as such, so it needs an explicit {@link Convert}. EclipseLink maps a collection of enums
     * without {@link Enumerated} to a <code>VARCHAR</code> column while it binds the ordinal, so it needs an explicit {@link Enumerated} there.
     *
     * @param path Path of interest.
     * @return Whether given path is {@link Enumerated} by {@link EnumType#ORDINAL}.
     */
    public static boolean isEnumeratedByOrdinal(Path<?> path) {
        Bindable<?> model = path.getModel();

        if (model instanceof Attribute<?, ?> attribute && attribute.getJavaMember() instanceof AnnotatedElement member) {
            var enumerated = findEnumerated(path);

            if (enumerated != null) {
                return enumerated.value() == EnumType.ORDINAL;
            }

            var type = attribute instanceof PluralAttribute<?, ?, ?> plural ? plural.getElementType().getJavaType() : attribute.getJavaType();
            return type.isEnum() && member.getAnnotation(Convert.class) == null;
        }

        return false;
    }

    /**
     * Returns whether given path is {@link Enumerated} by {@link EnumType#STRING}, so that its column holds the name of the enum constant.
     *
     * @param path Path of interest.
     * @return Whether given path is {@link Enumerated} by {@link EnumType#STRING}.
     * @since 2.0
     */
    public static boolean isEnumeratedByName(Path<?> path) {
        var enumerated = findEnumerated(path);
        return enumerated != null && enumerated.value() == EnumType.STRING;
    }

    private static Enumerated findEnumerated(Path<?> path) {
        return path.getModel() instanceof Attribute<?, ?> attribute && attribute.getJavaMember() instanceof AnnotatedElement member
            ? member.getAnnotation(Enumerated.class)
            : null;
    }

    // Helpers ----------------------------------------------------------------------------------------------------------------------------

    private static BaseEntityService<?, ?> requireCurrentBaseEntityService(String method) {
        var service = getCurrentBaseEntityService();

        if (service == null) {
            throw new IllegalStateException(String.format(ERROR_NO_CURRENT_BASE_ENTITY_SERVICE, method));
        }

        return service;
    }

    private static String escape(String value, String specialCharacters) {
        var escaped = new StringBuilder(value.length());

        for (var character : value.toCharArray()) {
            appendEscapedIfNecessary(escaped, character, specialCharacters);
        }

        return escaped.toString();
    }

}
