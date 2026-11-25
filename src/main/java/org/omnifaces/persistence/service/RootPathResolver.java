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

import static jakarta.persistence.metamodel.Type.PersistenceType.EMBEDDABLE;
import static java.lang.String.format;
import static java.util.Collections.sort;
import static org.omnifaces.persistence.query.Page.ID;
import static org.omnifaces.utils.reflect.Reflections.findField;
import static org.omnifaces.utils.reflect.Reflections.findGetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.persistence.Transient;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * Helper class of {@link BaseEntityService}.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
class RootPathResolver implements PathResolver {

    private static final String ERROR_AMBIGUOUS_FIELD = "Field %s cannot be resolved on %s, because it is declared by several @ManyToOne/@OneToOne children: %s. Use the path of one of them instead.";
    private static final String ERROR_UNKNOWN_FIELD = "Field %s cannot be found on %s. If this represents a transient field, make sure that it is delegating to @ManyToOne/@OneToOne children.";

    private final Root<?> root;
    private final String idName;
    private final Map<String, Path<?>> joins;
    private final Map<String, Path<?>> paths;
    private final Predicate<String> elementCollections;
    private final Supplier<Set<String>> manyOrOneToOnes;

    public RootPathResolver(Root<?> root, String idName, Predicate<String> elementCollections, Supplier<Set<String>> manyOrOneToOnes) {
        this.root = root;
        this.idName = idName;
        this.joins = getJoins(root);
        this.paths = new HashMap<>();
        this.elementCollections = elementCollections;
        this.manyOrOneToOnes = manyOrOneToOnes;
    }

    @Override
    public Expression<?> get(String field) {
        if (field == null) {
            return root;
        }

        Path<?> path = paths.get(field);

        if (path != null) {
            return path;
        }

        path = root;
        boolean explicitJoin = field.startsWith("@");
        String originalField = explicitJoin ? field.substring(1) : ID.equals(field) ? idName : field;
        String[] attributes = originalField.split("\\.");
        int depth = attributes.length;

        String joinPath = null;

        for (int i = 0; i < depth; i++) {
            String attribute = attributes[i];
            joinPath = toJoinPath(joinPath, attribute);

            if (!explicitJoin && isEmbedded(path, attribute)) {
                path = path.get(attribute);
            }
            else if (i + 1 < depth || elementCollections.test(originalField)) {
                try {
                    path = explicitJoin || !joins.containsKey(joinPath) ? join(path, attribute) : joins.get(joinPath);
                }
                catch (IllegalArgumentException e) {
                    throw new UnknownFieldException(format(ERROR_UNKNOWN_FIELD, field, root.getJavaType()), e);
                }
            }
            else {
                try {
                    path = path.get(attribute);
                }
                catch (IllegalArgumentException e) {
                    if (depth == 1 && isTransient(path.getModel().getBindableJavaType(), attribute)) {
                        path = guessManyOrOneToOnePath(attribute);
                    }
                    else {
                        path = null;
                    }

                    if (depth != 1 || path == null) {
                        throw new UnknownFieldException(format(ERROR_UNKNOWN_FIELD, field, root.getJavaType()), e);
                    }
                }
            }
        }

        paths.put(field, path);
        return path;
    }

    /**
     * Joins given attribute of given parent, with a <code>LEFT JOIN</code> when it is an optional singular association, so that an entity without the
     * association is kept, else with an <code>INNER JOIN</code>. A parent which is not a {@link From}, such as an embeddable, is navigated via a path
     * expression.
     */
    private static Path<?> join(Path<?> path, String attribute) {
        if (path instanceof From<?, ?> from) {
            return from.join(attribute, isOptionalSingularAssociation(from, attribute) ? JoinType.LEFT : JoinType.INNER);
        }

        return path.get(attribute);
    }

    /**
     * Returns whether given attribute of given parent is an embedded value or ID, so that its attributes are reached via a path expression, because an
     * embeddable is not an association.
     */
    static boolean isEmbedded(Path<?> path, String attribute) {
        return findSingularAttribute(path, attribute).filter(singularAttribute -> singularAttribute.getType().getPersistenceType() == EMBEDDABLE).isPresent();
    }

    private static Optional<SingularAttribute<?, ?>> findSingularAttribute(Path<?> path, String attribute) {
        return findSingularAttribute(getManagedType(path.getModel()), attribute);
    }

    private static Optional<SingularAttribute<?, ?>> findSingularAttribute(ManagedType<?> type, String attribute) {
        return findAttribute(type, attribute).filter(SingularAttribute.class::isInstance).map(singularAttribute -> (SingularAttribute<?, ?>) singularAttribute);
    }

    private static Optional<Attribute<?, ?>> findAttribute(ManagedType<?> type, String attribute) {
        try {
            return type != null ? Optional.of(type.getAttribute(attribute)) : Optional.empty();
        }
        catch (IllegalArgumentException e) {
            return Optional.empty(); // The attribute is not declared on the static type, e.g. on a subtype.
        }
    }

    private static boolean isOptionalSingularAssociation(From<?, ?> from, String attribute) {
        return findSingularAttribute(from, attribute).filter(SingularAttribute::isAssociation).filter(SingularAttribute::isOptional).isPresent();
    }

    private static ManagedType<?> getManagedType(Bindable<?> model) {
        if (model instanceof ManagedType<?> managedType) {
            return managedType;
        }
        else if (model instanceof SingularAttribute<?, ?> singularAttribute && singularAttribute.getType() instanceof ManagedType<?> managedType) {
            return managedType;
        }
        else if (model instanceof PluralAttribute<?, ?, ?> pluralAttribute && pluralAttribute.getElementType() instanceof ManagedType<?> managedType) {
            return managedType;
        }
        else {
            return null;
        }
    }

    private static boolean isTransient(Class<?> type, String property) {
        return findField(type, property).map(f -> f.isAnnotationPresent(Transient.class)).orElse(false)
            || findGetter(type, property).map(m -> m.isAnnotationPresent(Transient.class)).orElse(false);
    }

    /**
     * Returns the path of given attribute on the nearest {@code @ManyToOne} or {@code @OneToOne} which declares it, or <code>null</code> when none does. Only a
     * path of singular associations counts, because a transient getter has one value per entity, which a path through a collection does not have. The
     * candidates are checked against the metamodel, so that only the returned path adds joins to the query.
     *
     * @throws UnknownFieldException When several of the nearest ones declare it.
     */
    private Path<?> guessManyOrOneToOnePath(String attribute) {
        var nearest = new ArrayList<String>();
        var nearestDepth = Integer.MAX_VALUE;

        for (String manyOrOneToOne : manyOrOneToOnes.get()) {
            var associations = manyOrOneToOne.split("\\.");
            var depth = associations.length;

            if (depth <= nearestDepth && declares(associations, attribute)) {
                if (depth < nearestDepth) {
                    nearest.clear();
                    nearestDepth = depth;
                }

                nearest.add(manyOrOneToOne);
            }
        }

        if (nearest.size() > 1) {
            sort(nearest);
            throw new UnknownFieldException(format(ERROR_AMBIGUOUS_FIELD, attribute, root.getJavaType(), nearest), null);
        }

        return nearest.isEmpty() ? null : (Path<?>) get(nearest.get(0) + "." + attribute);
    }

    private boolean declares(String[] associations, String attribute) {
        var type = getManagedType(root.getModel());

        for (String association : associations) {
            type = findSingularAttribute(type, association).map(RootPathResolver::getManagedType).orElse(null);
        }

        return findAttribute(type, attribute).isPresent();
    }

    /**
     * Returns the joins and fetches of given root, mapped by their path of attribute names from the root, such as <code>parent.parent</code>.
     */
    private static Map<String, Path<?>> getJoins(From<?, ?> from) {
        Map<String, Path<?>> joins = new HashMap<>();
        collectJoins(from, null, joins);
        return joins;
    }

    private static void collectJoins(Path<?> path, String parentJoinPath, Map<String, Path<?>> joins) {
        var joinPath = parentJoinPath;

        if (path instanceof Join<?, ?> join) {
            joinPath = toJoinPath(parentJoinPath, join.getAttribute().getName());
        }
        else if (path instanceof Fetch<?, ?> fetch) {
            joinPath = toJoinPath(parentJoinPath, fetch.getAttribute().getName());
        }

        var childParentJoinPath = joinPath;

        if (path instanceof From<?, ?> from) {
            from.getJoins().forEach(join -> collectJoins(join, childParentJoinPath, joins));
        }
        if (path instanceof FetchParent<?, ?> fetchParent) {
            try {
                fetchParent.getFetches().stream().filter(Path.class::isInstance).forEach(fetch -> collectJoins((Path<?>) fetch, childParentJoinPath, joins));
            }
            catch (NullPointerException openJPAWillThrowThisOnEmptyFetches) {
                // Ignore and continue.
            }
        }
        if (joinPath != null) {
            joins.put(joinPath, path);
        }
    }

    private static String toJoinPath(String parentJoinPath, String attribute) {
        return parentJoinPath == null ? attribute : parentJoinPath + "." + attribute;
    }

}
