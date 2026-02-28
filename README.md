[![Maven](https://img.shields.io/maven-metadata/v/https/repo.maven.apache.org/maven2/org/omnifaces/omnipersistence/maven-metadata.xml.svg)](https://repo.maven.apache.org/maven2/org/omnifaces/omnipersistence/)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/omnipersistence.svg)](https://javadoc.io/doc/org.omnifaces/omnipersistence)
[![Tests](https://github.com/omnifaces/omnipersistence/actions/workflows/maven.yml/badge.svg)](https://github.com/omnifaces/omnipersistence/actions)
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
12. [Comparison with Jakarta Data](#12-comparison-with-jakarta-data)

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

All of them provide correct implementations of `equals`, `hashCode`, `compareTo` and `toString` based on entity ID out of the box.

**Override `identityGetters()`** to base all four on specific business-key fields — this is the preferred approach, as a single override keeps all four methods consistent:

```java
@Entity
public class Phone extends GeneratedIdEntity<Long> {

    private Type type;
    private String number;
    private Person owner;

    // equals, hashCode, compareTo and toString all use type + number instead of database ID
    @Override
    protected Stream<Function<Phone, Object>> identityGetters() {
        return Stream.of(Phone::getType, Phone::getNumber);
    }
}
```

**Use the protected final helpers only for fine-tuning**, when individual methods must behave differently from each other. The most common case is `compareTo` needing a different sort order than the fields that define equality:

```java
@Entity
public class Person extends GeneratedIdEntity<Long> {

    private String email;
    private String lastName;
    private String firstName;

    // equals, hashCode and toString identify by email (business key)
    @Override
    protected Stream<Function<Person, Object>> identityGetters() {
        return Stream.of(Person::getEmail);
    }

    // compareTo orders by last name then first name (for sorted collections and display)
    @Override
    public int compareTo(BaseEntity<Long> other) {
        return compareTo(other, Person::getLastName, Person::getFirstName);
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

**Cursor-based paging** — pass the last seen entity for keyset pagination on large datasets. Bypasses SQL `OFFSET`, so performance stays stable regardless of page depth:

```java
// Page 1 — ordinary offset paging
Page page1 = Page.with().range(0, 10).orderBy("id", true).build();
PartialResultList<Person> first = personService.getPage(page1, false);

// Page 2 — cursor-based: lastSeen drives the WHERE predicate instead of OFFSET
Person lastSeen = first.get(first.size() - 1);
Page page2 = Page.with().range(lastSeen, 10, false).orderBy("id", true).build();
PartialResultList<Person> second = personService.getPage(page2, false);
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

---

## 12. Comparison with Jakarta Data

[Jakarta Data](https://jakarta.ee/specifications/data/1.0/) is a new specification introduced in Jakarta EE 11 that standardises a repository-pattern API for data access across relational and NoSQL databases. Both OmniPersistence and Jakarta Data aim to reduce persistence boilerplate, but they make very different architectural choices. Understanding those differences helps you pick the right tool for your project.

### Programming model

OmniPersistence uses **service-class inheritance**. You extend `BaseEntityService<I, E>` and get a concrete, injectable service bean. All persistence logic lives in an ordinary Java class that you can customise by overriding methods.

Jakarta Data uses **annotated repository interfaces**. You declare an interface, annotate it with `@Repository`, and the runtime (or an annotation processor) generates the implementation. You never write an implementation class.

```java
// OmniPersistence — concrete service class
@ApplicationScoped // or @Stateless
public class BookService extends BaseEntityService<Long, Book> { }

// Jakarta Data — interface-only repository
@Repository
public interface Books extends CrudRepository<Book, Long> { }
```

### Persistence context and entity state

This is the deepest architectural difference.

OmniPersistence operates through a **full, stateful JPA `EntityManager`**. Entities returned from service methods are managed — the persistence context tracks changes (dirty checking), transparent lazy loading works, and explicit `flush()` calls propagate changes. This is the standard JPA programming model.

Jakarta Data uses a **stateless session** (Hibernate's `StatelessSession` in the reference implementation). Entities are always detached. There is no dirty checking, no transparent lazy loading, and no implicit cascade. Every write requires an explicit `@Insert`, `@Update`, `@Save`, or `@Delete` call.

| Capability | OmniPersistence | Jakarta Data |
|---|---|---|
| Dirty checking (auto-save on flush) | ✅ | ❌ |
| Transparent lazy loading | ✅ | ❌ |
| Cascade operations | ✅ via JPA `CascadeType` | ❌ never cascades |
| Optimistic locking | ✅ `@Version` honored | ✅ honored on `@Update`/`@Delete` |
| Pessimistic locking | ✅ via `EntityManager` | ❌ |
| Entity graphs | ✅ `@NamedEntityGraph` + `getByIdWithLoadGraph` | ❌ |

### Entity requirements

OmniPersistence **requires entities to extend one of its base classes** (`BaseEntity`, `GeneratedIdEntity`, `TimestampedEntity`, etc.). This gives you `equals`, `hashCode`, `compareTo` and `toString` out of the box but couples your entity model to the library.

Jakarta Data works with **any `@Entity` class**. No supertype or interface is required. This makes it easier to introduce into an existing JPA model without modification.

### Querying

OmniPersistence exposes three query paths:

1. **Named queries** declared on the entity with `@NamedQuery`.
2. **JPQL fragment shortcuts** — `list("WHERE e.active = true")`, `find("WHERE e.email = ?1", email)`.
3. **Programmatic Criteria API** inside `getPage()` via a `CriteriaBuilder` callback for complex joins, projections, and aggregations.

Jakarta Data exposes three query paths:

1. **`@Find`** — maps method parameters to entity fields by name (no method-name parsing required).
2. **`@Query`** — accepts JDQL or JPQL strings, validated at compile time by the annotation processor.
3. **Method-name conventions** — e.g. `findByLastNameAndFirstName`, supported as a deprecated migration path from Spring Data.

```java
// OmniPersistence — JPQL fragment (alias e is predefined)
List<Book> books = bookService.list("WHERE e.year > ?1 ORDER BY e.title", 2020);

// Jakarta Data — @Find (parameter name matches entity field)
@Find
@OrderBy("title")
List<Book> booksAfter(int year);

// Jakarta Data — @Query
@Query("where year > :year order by title")
List<Book> booksAfter(int year);
```

A practical distinction: Jakarta Data validates `@Query` strings at **compile time** (assuming a supporting annotation processor). OmniPersistence JPQL strings are only validated at **runtime** when the query is first executed.

### Pagination and search criteria

OmniPersistence's `Page` + `PartialResultList` system is particularly rich. Criteria are expressed as a `Map<String, Object>` where values are plain objects (exact-equality) or typed wrappers that translate to the appropriate SQL predicate:

```java
Map<String, Object> criteria = Map.of(
    "lastName",  Like.contains("smith"),
    "age",       Between.range(18, 65),
    "status",    Not.value("BANNED")
);
Page page = Page.with().range(0, 20).allMatch(criteria).orderBy("lastName", true).build();
PartialResultList<Person> result = personService.getPage(page, true);
result.getEstimatedTotalNumberOfResults(); // total count
```

The `allMatch` map produces AND conditions; `anyMatch` produces OR conditions. DTO projections and join-fetch eager loading are also handled inside `getPage`.

Jakarta Data's `PageRequest` + `Page<T>` covers offset pagination and the total count, and `CursoredPage<T>` covers keyset (cursor-based) pagination. However, **filtering criteria are not built into the pagination object** — conditions must be expressed in the query method signature itself. There is no equivalent to the rich `Criteria` wrappers (`Like`, `Between`, `Not`, etc.); range or partial-match conditions require a `@Query` string or query-by-method-name keywords.

| Feature | OmniPersistence | Jakarta Data |
|---|---|---|
| Offset pagination | ✅ `Page` + `PartialResultList` | ✅ `PageRequest` + `Page<T>` |
| Cursor/keyset pagination | ✅ `Page.with().last(entity)` | ✅ `CursoredPage<T>` |
| Total count | ✅ `getEstimatedTotalNumberOfResults()` | ✅ `page.totalElements()` |
| Criteria wrappers (Like, Between, Not…) | ✅ built-in | ❌ |
| AND / OR condition grouping in pagination | ✅ `allMatch` / `anyMatch` | ❌ |
| DTO projection inside pagination | ✅ via `CriteriaBuilder` callback | ❌ |
| Eager join-fetch in pagination | ✅ varargs join-fetch names | ❌ |

### Soft delete

OmniPersistence has first-class soft-delete support. Annotate any boolean field with `@SoftDeletable` and every read method (`findById`, `list`, `getPage`, …) automatically excludes soft-deleted rows. Dedicated service methods handle the soft side:

```java
commentService.softDelete(comment);
commentService.softUndelete(comment);
List<Comment> gone = commentService.listSoftDeleted();
```

Jakarta Data has no soft-delete concept. Implementing it requires writing explicit `@Query` methods with a `WHERE deleted = false` clause in every query, plus manual exclusion from pagination.

### Non-deletable entities

OmniPersistence's `@NonDeletable` entity annotation causes `delete()` to throw `NonDeletableEntityException` as a safety guard. Jakarta Data has no equivalent.

### Auditing

OmniPersistence provides field-level change auditing. Annotate a field with `@Audit`, add `@EntityListeners(AuditListener.class)` to the entity, and every real field change fires a CDI `AuditedChange` event carrying the entity, field name, old value, and new value:

```java
public void onAuditedChange(@Observes AuditedChange change) {
    log.info("{}.{}: {} → {}", change.getEntityName(),
        change.getPropertyName(), change.getOldValue(), change.getNewValue());
}
```

Jakarta Data has no auditing API.

### CDI lifecycle events

`BaseEntity` registers a `BaseEntityListener` that fires CDI events after persistence operations. Any CDI bean can observe entity changes application-wide:

```java
public void onCreated(@Observes @Created Person p) { … }
public void onUpdated(@Observes @Updated Person p) { … }
public void onDeleted(@Observes @Deleted Person p) { … }
```

Jakarta Data fires no CDI events for entity lifecycle changes.

### Lazy collection helpers

Because OmniPersistence works with a stateful `EntityManager`, lazy associations can be loaded on demand:

```java
person = personService.fetchLazyCollections(person, Person::getPhones, Person::getGroups);
person = personService.fetchLazyBlobs(person);
```

Jakarta Data's stateless model means there is nothing to lazily load — associations are never transparently fetched, and the spec provides no fetch helper API.

### Entity model utilities

OmniPersistence provides **ready-made base classes** with `equals`/`hashCode`/`compareTo`/`toString` driven by a single `identityGetters()` override, plus automatic timestamp and version column management. Jakarta Data has no such base classes; those concerns remain the developer's responsibility (or can be addressed by JPA `@MappedSuperclass` patterns as before).

### DataSource configuration

OmniPersistence's `SwitchableCommonDataSource` / `SwitchableXADataSource` externalises JDBC connection properties to a properties file so the target database can be changed without touching deployment descriptors. Jakarta Data has no equivalent feature.

### Provider and database detection

Every `BaseEntityService` detects the active JPA provider (`HIBERNATE`, `ECLIPSELINK`, `OPENJPA`) and underlying database (`H2`, `MYSQL`, `POSTGRESQL`, `SQLSERVER`, `DB2`) at startup, enabling provider-specific or database-specific branches in service code. Jakarta Data has no equivalent.

### NoSQL / multi-datastore support

Jakarta Data is **datastore-agnostic by design**. The same `@Repository`, `@Find`, `@Query`, `@Insert`, etc. annotations work against both JPA (relational) and Jakarta NoSQL (document, key-value, wide-column) databases. The common query language, JDQL, is a carefully constrained subset of JPQL that avoids relational-only constructs so it can be translated to NoSQL query languages.

OmniPersistence is **exclusively JPA (relational)**. It exposes full JPA Criteria API, JPQL, named queries, entity graphs, and the EntityManager directly — none of which apply to a NoSQL store.

### Summary table

| Feature | OmniPersistence | Jakarta Data 1.0 |
|---|---|---|
| **Model** | Service-class inheritance | Annotated repository interface |
| **Entity requirement** | Must extend `BaseEntity` hierarchy | Any `@Entity`, no supertype required |
| **Persistence context** | Stateful `EntityManager` | Stateless session |
| **Dirty checking** | ✅ | ❌ |
| **Transparent lazy loading** | ✅ | ❌ |
| **Cascade** | ✅ via JPA `CascadeType` | ❌ |
| **Entity graphs** | ✅ `@NamedEntityGraph` | ❌ |
| **Pessimistic locking** | ✅ | ❌ |
| **Optimistic locking** | ✅ | ✅ |
| **JPQL queries** | ✅ (full JPQL) | ✅ (JDQL + JPQL, provider may restrict) |
| **Criteria API queries** | ✅ programmatic | ❌ |
| **Compile-time query validation** | ❌ | ✅ (`@Query` strings) |
| **Named queries** | ✅ | ❌ |
| **Method-name query derivation** | ❌ | ✅ (deprecated extension) |
| **Rich pagination criteria** (`Like`, `Between`, `Not`…) | ✅ | ❌ |
| **AND / OR filter grouping in pagination** | ✅ | ❌ |
| **DTO projection in pagination** | ✅ | ❌ |
| **Cursor-based pagination** | ✅ | ✅ |
| **Soft delete** | ✅ | ❌ |
| **Non-deletable guard** | ✅ | ❌ |
| **Field-level auditing** | ✅ | ❌ |
| **CDI lifecycle events** | ✅ | ❌ |
| **Lazy collection helpers** | ✅ | ❌ |
| **Provider/database detection** | ✅ | ❌ |
| **Switchable DataSource** | ✅ | ❌ |
| **Entity base classes** (ID, timestamps, version) | ✅ | ❌ |
| **Business-key identity helpers** | ✅ | ❌ |
| **NoSQL / non-relational datastores** | ❌ | ✅ |
| **Jakarta EE target** | EE 10 | EE 11 |

### When to use which

**Choose OmniPersistence** when you are building a Jakarta EE 10 (or later) application that uses a relational database through JPA, and you want a rich, immediately productive toolkit: stateful entities with lazy loading and cascades, built-in soft delete, auditing, flexible paginated search with typed criteria, and CDI lifecycle hooks — all without any annotation-processor tooling requirements.

**Choose Jakarta Data** when you need to target both relational and NoSQL datastores from a single programming model, when you want the compile-time safety of validated query strings, when your architecture calls for a strict stateless data layer, or when you are starting a Jakarta EE 11 greenfield project that will benefit from a standard specification with multiple vendor implementations.
