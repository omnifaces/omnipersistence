package org.omnifaces.persistence;

import static java.util.Arrays.stream;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.omnifaces.utils.Lang.startsWithOneOf;

import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.enterprise.inject.spi.CDI;
import javax.persistence.EntityManagerFactory;

public enum Database {

	H2,

	MYSQL("MARIA"),

	POSTGRESQL("POSTGRES"),

	UNKNOWN;

	private static final Logger logger = Logger.getLogger(Database.class.getName());

	private String[] names;

	private Database(String... aliases) {
		this.names = concat(Stream.of(name()), stream(aliases)).collect(toList()).toArray(new String[0]);
	}

	public static Database of(Provider provider, EntityManagerFactory entityManagerFactory) {
		try {
			String uppercasedDialectName = provider.getDialectName(entityManagerFactory).toUpperCase();

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

	public static boolean is(Database database) {
		return current() == database;
	}

	public static Database current() {
		return CDI.current().select(Database.class).get();
	}

}
