[![Maven](https://img.shields.io/maven-metadata/v/https/repo.maven.apache.org/maven2/org/omnifaces/omnipersistence/maven-metadata.xml.svg)](https://repo.maven.apache.org/maven2/org/omnifaces/omnipersistence/)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/omnipersistence.svg)](https://javadoc.io/doc/org.omnifaces/omnipersistence)
[![Tests](https://github.com/omnifaces/omnipersistence/actions/workflows/develop.maven.yml/badge.svg)](https://github.com/omnifaces/omnipersistence/actions)
[![License](https://img.shields.io/:license-apache-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

# OmniPersistence

Utilities for Jakarta Persistence, JDBC and DataSources.

OmniPersistence reduces boilerplate in the JPA persistence layer by providing a rich base service class, declarative soft-delete, auditing, structured pagination with search criteria, and flexible datasource configuration — compatible with Hibernate, EclipseLink and OpenJPA on any Jakarta EE 10 runtime.

```xml
<dependency>
    <groupId>org.omnifaces</groupId>
    <artifactId>omnipersistence</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires a _minimum_ of **Java 17** and **Jakarta EE 10**.

---

## Table of contents

1. [Entity model hierarchy](#1-entity-model-hierarchy)
2. [BaseEntityService](#2-baseentityservice)
3. [Pagination with Page](#3-pagination-with-page)
4. [Search criteria](#4-search-criteria)
5. [Soft delete](#5-soft-delete)
6. [Non-deletable entities](#6-non-deletable-entities)
7. [Auditing](#7-auditing)
8. [Timestamps and optimistic locking](#8-timestamps-and-optimistic-locking)
9. [Provider and database detection](#9-provider-and-database-detection)
10. [JPA utilities](#10-jpa-utilities)
11. [Switchable DataSource](#11-switchable-datasource)

---

## 1. Entity model hierarchy

Pick the base class that matches what your entity needs:

| Class | Generated ID | Timestamps | Optimistic lock |
|---|---|---|---|
| `BaseEntity<I>` | — | — | — |
| `GeneratedIdEntity<I>` | ✅ | — | — |
| `TimestampedBaseEntity<I>` | — | ✅ | — |
| `TimestampedEntity<I>` | ✅ | ✅ | — |
| `VersionedBaseEntity<I>` | — | ✅ | ✅ |
| `VersionedEntity<I>` | ✅ | ✅ | ✅ |

All of them provide correct implementations of `equals`, `hashCode`, `compareTo` and `toString` based on entity ID out of the box. To base identity on specific fields instead, override the single `identity()` method — all four methods pick it up automatically:

```java
@Entity
public class Phone extends GeneratedIdEntity<Long> {

    private Type type;
    private String number;
    private Person owner;

    // identity and ordering based on type + number rather than database ID
    @Override
    protected Object[] identity() {
        return new Object[]{ getType(), getNumber() };
    }
}
```

The ID type `I` can be any `Comparable & Serializable` — `Long`, `String`, `UUID`, etc.

---

## 2. BaseEntityService

Extend `BaseEntityService<I, E>` to get a full CRUD service for your entity. Works as both an EJB (`@Stateless`) and a CDI bean (`@ApplicationScoped`). The default persistence unit is injected automatically — no boilerplate needed:

```java
@Stateless  // or @ApplicationScoped
public class PersonService extends BaseEntityService<Long, Person> {
    // nothing required — default @PersistenceContext is injected by BaseEntityService
}
```

Override `getEntityManager()` only when you need a non-default persistence unit:

```java
@Stateless
public class PersonService extends BaseEntityService<Long, Person> {

    @PersistenceContext(unitName = "secondary")
    private EntityManager em;

    @Override
    protected EntityManager getEntityManager() { return em; }
}
```

### Inherited methods at a glance

**Lookup**

```java
Optional<Person> person   = personService.findById(id);
Person           person   = personService.getById(id);            // null if absent
Person           person   = personService.getByIdWithLoadGraph(id, "Person.withPhones");
List<Person>     persons  = personService.getByIds(List.of(1L, 2L, 3L));
List<Person>     persons  = personService.list();
Optional<Person> person    = personService.findFirst("WHERE email = ?1", email);
```

The entity graph name passed to `getByIdWithLoadGraph` refers to a `@NamedEntityGraph` declared on the entity:

```java
@Entity
@NamedEntityGraph(name = "Person.withPhones", attributeNodes = @NamedAttributeNode("phones"))
public class Person extends GeneratedIdEntity<Long> { … }
```

**CRUD**

```java
Long   id      = personService.persist(newPerson);
Person updated = personService.update(person);
Person saved   = personService.save(person);           // persist-or-update
personService.delete(person);
personService.reset(detachedPerson);                   // reload from DB into same reference
```

**Batch**

```java
List<Person> updated = personService.update(List.of(a, b, c));
personService.delete(List.of(a, b, c));
```

**JPQL shortcuts** — short JPQL is auto-expanded to `SELECT e FROM EntityName e <your fragment>`, so the alias `e` is predefined:

```java
// these two are equivalent
Optional<Person> p = personService.find("SELECT p FROM Person p WHERE p.email = ?1", email);
Optional<Person> p = personService.find("WHERE e.email = ?1", email);

List<Person> list  = personService.list("WHERE e.gender = :g", params -> params.put("g", MALE));
int affected       = personService.update("SET e.active = false WHERE e.lastLogin < ?1", cutoff);
```

**Lazy collections**

```java
Person p = personService.fetchLazyCollections(person, Person::getPhones, Person::getGroups);
Person p = personService.fetchLazyBlobs(person);
```

---

## 3. Pagination with Page

`Page` is an immutable value object that bundles offset, limit, ordering and search criteria. Pass it to `getPage()` and get back a `PartialResultList` that also carries the total count when requested.

```java
Page page = Page.with()
    .range(0, 10)                        // offset 0, limit 10
    .orderBy("lastName", true)           // ASC
    .orderBy("firstName", true)          // ASC
    .orderBy("dateOfBirth", false)       // DESC
    .allMatch(requiredCriteria)          // AND conditions
    .anyMatch(optionalCriteria)          // OR conditions
    .build();

PartialResultList<Person> result = personService.getPage(page, true /* count */);
result.getEstimatedTotalNumberOfResults(); // total matching rows
```

**DTO projection** — map results to a different type directly in the query. The DTO must extend the entity class and provide a constructor whose parameters match the mapped expressions in order:

```java
// DTO — extends Person so getPage() accepts it as a result type
public class PersonCard extends Person {

    private final String addressString;
    private final Long totalPhones;

    public PersonCard(Long id, String email, String addressString, Long totalPhones) {
        setId(id);
        setEmail(email);
        this.addressString = addressString;
        this.totalPhones   = totalPhones;
    }

    public String getAddressString() { return addressString; }
    public Long   getTotalPhones()   { return totalPhones; }
}
```

```java
PartialResultList<PersonCard> cards = personService.getPage(
    page, true, PersonCard.class,
    (builder, query, root) -> {
        var phones = root.join("phones", LEFT);
        query.groupBy(root.get("id"));
        return new LinkedHashMap<>() {{
            put(PersonCard::getId,            root.get("id"));
            put(PersonCard::getEmail,         root.get("email"));
            put(PersonCard::getAddressString, JPA.concat(builder, root.get("address").get("street"), " ", root.get("address").get("city")));
            put(PersonCard::getTotalPhones,   builder.count(phones));
        }};
    }
);
```

**Eager fetching** — declare which lazy associations to join-fetch:

```java
PartialResultList<Person> result = personService.getPage(page, true, "phones", "address");
```

**Cursor-based paging** — pass the last seen entity for keyset pagination on large datasets:

```java
Page nextPage = Page.with().range(0, 10).orderBy("id", true).last(lastEntity).build();
```

---

## 4. Search criteria

Criteria objects are the values in the `requiredCriteria` / `optionalCriteria` maps. Plain values produce an exact-equality predicate. Wrap them to get richer matching:

| Wrapper | SQL equivalent | Example |
|---|---|---|
| _(plain value)_ | `= ?` | `"active", true` |
| `Like.contains(v)` | `LIKE '%v%'` | `"name", Like.contains("john")` |
| `Like.startsWith(v)` | `LIKE 'v%'` | `"code", Like.startsWith("NL")` |
| `Like.endsWith(v)` | `LIKE '%v'` | `"email", Like.endsWith("@example.com")` |
| `IgnoreCase.value(v)` | `LOWER(f) = LOWER(v)` | `"email", IgnoreCase.value("FOO@BAR.COM")` |
| `Order.lessThan(v)` | `< v` | `"age", Order.lessThan(18)` |
| `Order.greaterThan(v)` | `> v` | `"score", Order.greaterThan(50)` |
| `Order.lessThanOrEqualTo(v)` | `<= v` | `"price", Order.lessThanOrEqualTo(99)` |
| `Order.greaterThanOrEqualTo(v)` | `>= v` | `"price", Order.greaterThanOrEqualTo(10)` |
| `Between.range(lo, hi)` | `BETWEEN lo AND hi` | `"age", Between.range(18, 65)` |
| `Bool.value(v)` | `IS TRUE / IS NOT TRUE` | `"verified", Bool.value(true)` |
| `Numeric.value(v)` | `= v` (parsed) | `"id", Numeric.value(42)` |
| `Enumerated.value(v)` | `= 'ENUM_NAME'` | `"gender", Enumerated.value(MALE)` |
| `Not.value(v)` | `NOT (…)` | `"status", Not.value("INACTIVE")` |

```java
Map<String, Object> criteria = new LinkedHashMap<>();
criteria.put("lastName",  Like.contains("smith"));
criteria.put("age",       Between.range(18, 65));
criteria.put("gender",    Enumerated.value(Gender.FEMALE));
criteria.put("status",    Not.value("BANNED"));              // NOT = 'BANNED'
criteria.put("email",     Not.value(Like.contains("spam"))); // NOT LIKE '%spam%'
criteria.put("age",       Not.value(Between.range(18, 25))); // NOT BETWEEN 18 AND 25

PartialResultList<Person> result = personService.getPage(
    Page.with().range(0, 20).allMatch(criteria).orderBy("lastName", true).build(),
    true
);
```

`Like` is case-insensitive and also works on enum fields (matches `name().contains(pattern)`) and boolean fields (delegates to `Bool`).

---

## 5. Soft delete

Mark a boolean (or Boolean) field with `@SoftDeletable` to make the entity logically deletable without touching the database row.

```java
@Entity
public class Comment extends GeneratedIdEntity<Long> {

    @SoftDeletable           // deleted = true means "gone"
    private boolean deleted;

    // or:
    @SoftDeletable(Type.ACTIVE)   // active = false means "gone"
    private boolean active;
}
```

All read methods (`findById`, `list`, `getPage`, …) automatically exclude soft-deleted rows. Dedicated methods operate on the soft-deleted side:

```java
commentService.softDelete(comment);                   // mark deleted
commentService.softUndelete(comment);                 // restore
List<Comment> gone   = commentService.listSoftDeleted();
Optional<Comment> c  = commentService.findSoftDeletedById(id);
commentService.softDelete(List.of(a, b, c));          // batch
```

Calling `softDelete` / `softUndelete` on an entity without `@SoftDeletable` throws `NonSoftDeletableEntityException`.

---

## 6. Non-deletable entities

Annotate an entity with `@NonDeletable` to prevent accidental hard deletes at the service level:

```java
@Entity
@NonDeletable
public class Config extends GeneratedIdEntity<Long> { … }

configService.delete(config); // throws NonDeletableEntityException
```

The entity can still be soft-deleted if it also carries `@SoftDeletable`.

---

## 7. Auditing

Add `@EntityListeners(AuditListener.class)` to the entity and mark audited fields with `@Audit`. Whenever a marked field changes, `AuditListener` fires a CDI `AuditedChange` event — no subclassing required.

**Entity**

```java
@Entity
@EntityListeners(AuditListener.class)
public class Config extends GeneratedIdEntity<Long> {

    private String key;

    @Audit
    @Column
    private String value;   // only this field is tracked
}
```

**Observer**

```java
@ApplicationScoped
public class AuditLog {

    public void onAuditedChange(@Observes AuditedChange change) {
        System.out.printf("[AUDIT] %s#%s.%s: %s → %s%n",
            change.getEntityName(),
            change.getEntity().getId(),
            change.getPropertyName(),
            change.getOldValue(),
            change.getNewValue());
    }
}
```

`AuditedChange` carries the full entity reference, entity name, property name, old value and new value. The event is only fired when the value actually changes (old ≠ new).

---

## 8. Timestamps and optimistic locking

Use `TimestampedEntity` to get `created` and `lastModified` (`Instant`) fields that are managed automatically:

```java
@Entity
public class Article extends TimestampedEntity<Long> {
    private String title;
}

article.getCreated();       // set on first persist
article.getLastModified();  // updated on every merge
```

Use `VersionedEntity` to additionally get an `@Version` column for optimistic locking:

```java
@Entity
public class Document extends VersionedEntity<Long> {
    private String content;
}
// concurrent updates throw OptimisticLockException automatically
```

---

## 9. Provider and database detection

Every `BaseEntityService` knows which JPA provider and underlying database it is running against — detected automatically at startup.

```java
Provider provider = personService.getProvider();
Database database = personService.getDatabase();
```

| `Provider` | `Database` |
|---|---|
| `HIBERNATE` | `H2` |
| `ECLIPSELINK` | `MYSQL` |
| `OPENJPA` | `POSTGRESQL` |
| `UNKNOWN` | `UNKNOWN` |

Useful for writing provider- or database-specific query logic without hard-coding strings.

---

## 10. JPA utilities

`JPA` is a static utility class with helpers that complement the JPA Criteria API:

```java
// Safe single-result lookup (empty instead of NoResultException/NonUniqueResultException)
Optional<Person> p = JPA.findSingleResult(query);
Optional<Person> p = JPA.findFirstResult(query);    // ignores duplicates

// Portable string cast across providers and databases
Expression<String> s = JPA.castAsString(builder, root.get("age"));

// Multi-expression string concatenation in a criteria query
Expression<String> full = JPA.concat(builder, root.get("firstName"), " ", root.get("lastName"));

// Count how many other tables reference this row (useful before delete)
long refs = JPA.countForeignKeyReferences(em, Person.class, Long.class, id);

// Validation mode from persistence.xml
ValidationMode mode = JPA.getValidationMode(em);
```

---

## 11. Switchable DataSource

`SwitchableCommonDataSource` (and `SwitchableXADataSource` for XA) reads datasource configuration from an external properties file, making it possible to switch databases without touching deployment descriptors.

**Declare in `web.xml` / `@DataSourceDefinition`**

```xml
<data-source>
    <name>java:app/myDS</name>
    <class-name>org.omnifaces.persistence.datasource.SwitchableCommonDataSource</class-name>
    <property>
        <name>configFile</name>
        <value>META-INF/database.properties</value>
    </property>
</data-source>
```

**`META-INF/database.properties`**

```properties
className=org.postgresql.ds.PGSimpleDataSource
serverName=localhost
databaseName=mydb
user=myuser
password=mypassword
```

Swap the file (or override via a custom `PropertiesFileLoader` SPI) to point at a different database without redeployment.

---

## Integration tests and further examples

More detailed usage — including JSF/DataTable integration — can be found in the [OptimusFaces](https://github.com/omnifaces/optimusfaces) project, which builds pagination and lazy-loading JSF components directly on top of `BaseEntityService` and `Page`.
