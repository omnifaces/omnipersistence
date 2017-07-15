package org.omnifaces.persistence;

import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;

@ApplicationScoped
public class Environment {

	private static final Logger logger = Logger.getLogger(Environment.class.getName());

	private Provider provider;
	private Database database;

	@PersistenceContext
	private EntityManager entityManager;

	@PersistenceUnit
	private EntityManagerFactory entityManagerFactory;

	@PostConstruct
	public void init() {
		provider = Provider.of(entityManager);
		database = Database.of(provider, entityManagerFactory);

		logger.info("Using OmniPersistence with provider " + provider + " and database " + database);
	}

	@Produces
	public Provider getProvider() {
		return provider;
	}

	@Produces
	public Database getDatabase() {
		return database;
	}

}
