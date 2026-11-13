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

import static java.util.Arrays.stream;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Stream.concat;
import static org.omnifaces.utils.Lang.startsWithOneOf;

import java.util.logging.Logger;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;

import org.omnifaces.persistence.service.BaseEntityService;

/**
 * Enumeration of all supported databases. The database is automatically detected from the Jakarta Persistence provider's dialect.
 * <p>
 * Currently supported databases:
 * <ul>
 * <li>{@link #H2}
 * <li>{@link #MYSQL} (also matches MariaDB)
 * <li>{@link #POSTGRESQL}
 * <li>{@link #SQLSERVER}
 * <li>{@link #DB2}
 * </ul>
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see Provider
 * @see BaseEntityService#getDatabase()
 */
public enum Database {

    /**
     * H2 database.
     */
    H2(true),

    /**
     * MySQL database. Also matches MariaDB.
     */
    MYSQL(true, "MARIA"),

    /**
     * PostgreSQL database.
     */
    POSTGRESQL(false, "POSTGRES"),

    /**
     * SQL Server database.
     */
    SQLSERVER(true),

    /**
     * DB2 database.
     */
    DB2(false),

    /**
     * Database is unknown. It is assumed to sort <code>NULL</code> as the smallest value.
     */
    UNKNOWN(true);

    private static final Logger logger = Logger.getLogger(Database.class.getName());

    private final boolean sortsNullsLow;
    private final String[] names;

    /**
     * Internal constructor to define a database and its associated dialect name aliases.
     *
     * @param sortsNullsLow Whether the database sorts <code>NULL</code> as the smallest value.
     * @param aliases Optional aliases that might appear in the Jakarta Persistence dialect name.
     */
    Database(boolean sortsNullsLow, String... aliases) {
        this.sortsNullsLow = sortsNullsLow;
        this.names = concat(Stream.of(name()), stream(aliases)).toArray(String[]::new);
    }

    /**
     * Returns the {@link Database} associated with the given entity manager. This is determined by inspecting the dialect name provided by the underlying
     * Jakarta Persistence {@link Provider}.
     *
     * @param entityManager The entity manager to detect the database for.
     * @return The detected {@link Database}, or {@link #UNKNOWN} if detection fails or is unsupported.
     */
    public static Database of(EntityManager entityManager) {
        var provider = Provider.of(entityManager);
        var entityManagerFactory = entityManager.getEntityManagerFactory();

        try {
            var uppercasedDialectName = provider.getDialectName(entityManagerFactory).toUpperCase();

            for (Database database : values()) {
                if (startsWithOneOf(uppercasedDialectName, database.names)) {
                    return database;
                }
            }
        }
        catch (Exception e) {
            logger.log(WARNING, "Cannot to determine configured Database for " + provider + " by " + entityManagerFactory, e);
        }

        return UNKNOWN;
    }

    /**
     * Returns whether this database sorts <code>NULL</code> as the smallest value, i.e. first in ascending order and last in descending order. Otherwise it
     * sorts <code>NULL</code> as the largest value.
     *
     * @return Whether this database sorts <code>NULL</code> as the smallest value.
     * @since 2.0
     */
    public boolean sortsNullsLow() {
        return sortsNullsLow;
    }

}
