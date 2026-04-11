# GASP — GraphQL Annotation Schema Processor

## Table of Contents

- [1. Vision](#1-vision)
- [2. Architecture Overview](#2-architecture-overview)
- [3. Module Structure](#3-module-structure)
  - [3.1 Dependency Graph](#31-dependency-graph)
- [4. Annotations API (`gasp-annotations`)](#4-annotations-api-gasp-annotations)
  - [4.1 Type Annotations](#41-type-annotations)
  - [4.2 Field Annotations](#42-field-annotations)
  - [4.3 Operation Annotations](#43-operation-annotations)
  - [4.4 Relationship / Fetch Annotations](#44-relationship--fetch-annotations)
  - [4.5 Third-Party Annotation Interop](#45-third-party-annotation-interop)
- [5. Annotation Processor Internals (`gasp-processor`)](#5-annotation-processor-internals-gasp-processor)
  - [5.1 Entry Point](#51-entry-point)
  - [5.2 Processing Pipeline](#52-processing-pipeline)
  - [5.3 Internal Model](#53-internal-model)
  - [5.4 Type Resolution Rules](#54-type-resolution-rules)
  - [5.5 Validation (Compile-Time Errors)](#55-validation-compile-time-errors)
- [6. Code Generation — What Gets Produced](#6-code-generation--what-gets-produced)
  - [6.1 SDL File](#61-sdl-file)
  - [6.2 Type Registry](#62-type-registry)
  - [6.3 DataFetcher Classes](#63-datafetcher-classes)
  - [6.4 Relation Fetchers](#64-relation-fetchers)
  - [6.5 Field Mappings](#65-field-mappings)
- [7. jOOQ Integration (`gasp-jooq`)](#7-jooq-integration-gasp-jooq)
  - [7.1 JooqComposableQuery](#71-jooqcomposablequery)
  - [7.2 Usage in Fetcher Methods](#72-usage-in-fetcher-methods)
  - [7.3 Without jOOQ](#73-without-jooq)
- [8. Framework Adapters](#8-framework-adapters)
  - [8.1 Micronaut Adapter](#81-micronaut-adapter)
  - [8.2 Spring Adapter](#82-spring-adapter)
- [9. Runtime Library (`gasp-runtime`)](#9-runtime-library-gasp-runtime)
  - [9.1 Composable Query Architecture](#91-composable-query-architecture)
  - [9.2 ComposableQuery Interface](#92-composablequery-interface)
  - [9.3 How Fetchers Return Composable Queries](#93-how-fetchers-return-composable-queries)
  - [9.4 Example: Composable Path (with jOOQ)](#94-example-composable-path-with-jooq)
  - [9.5 Example: Direct Return Path (No ORM)](#95-example-direct-return-path-no-orm)
  - [9.6 Type-Level Fetcher Wiring](#96-type-level-fetcher-wiring)
- [10. Phased Implementation](#10-phased-implementation)
  - [10.1 Phase 1 — Foundation, Framework Adapters, and Basic Type Mapping (COMPLETE)](#101-phase-1--foundation-framework-adapters-and-basic-type-mapping-complete)
  - [10.2 Phase 2 — GraphQL Type System & Type-Level Fetchers (COMPLETE)](#102-phase-2--graphql-type-system--type-level-fetchers-complete)
  - [10.3 Phase 3 — Entity Mapping & jOOQ Integration](#103-phase-3--entity-mapping--jooq-integration)
  - [10.4 Phase 4 — Advanced Features](#104-phase-4--advanced-features)
- [11. Open Questions](#11-open-questions)
- [12. Build Configuration](#12-build-configuration)

---

## 1. Vision

A compile-time GraphQL framework that generates schemas, DataFetchers, and efficient database queries from annotated Java classes. No runtime reflection. No startup cost for schema generation. Precise SQL that fetches only what the GraphQL client requests.

**Core principle:** The annotation processor does the heavy lifting during `javac`. At runtime, the generated code is just method calls — no introspection, no classpath scanning, no schema building.

> **Note:** As each phase is completed, a detailed description of the work finished should be added to [COMPLETED.md](./COMPLETED.md). This serves as a living record of what was built, decisions made, and test coverage achieved — providing context for future phases and contributors.

---

## 2. Architecture Overview

```
  Build time (javac)                          Runtime
 ┌──────────────────────────────┐    ┌──────────────────────────────┐
 │                              │    │                              │
 │  @GraphQLType                │    │  HTTP request with           │
 │  @GraphQLQuery               │    │  GraphQL query               │
 │  @MappedEntity               │    │         │                    │
 │         │                    │    │         ▼                    │
 │         ▼                    │    │  graphql-java parses &       │
 │  GASP Annotation Processor   │    │  calls generated DataFetcher │
 │         │                    │    │         │                    │
 │    ┌────┴────────┐          │    │         ▼                    │
 │    ▼             ▼          │    │  DataFetcher reads           │
 │  schema.graphqls  Generated  │    │  selectionSet, calls         │
 │                   source:    │    │  generated QueryBuilder      │
 │              DataFetchers    │    │         │                    │
 │              QueryBuilders   │    │         ▼                    │
 │              TypeRegistry    │    │  jOOQ executes precise SQL   │
 │              FieldMappings   │    │  SELECT only requested cols  │
 │                              │    │  JOIN only requested rels    │
 └──────────────────────────────┘    └──────────────────────────────┘
```

---

## 3. Module Structure

```
gasp/
├── buildSrc/                   Convention plugins (gasp.base, gasp.micronaut, gasp.spring)
│   └── src/main/groovy/
│
├── gasp-annotations/           Zero-dependency annotation JAR
│   └── src/main/java/
│       └── com/moltenbits/gasp/annotation/
│
├── gasp-processor/             JSR 269 annotation processor
│   └── src/main/java/
│       └── com/moltenbits/gasp/processor/
│
├── gasp-runtime/               Minimal runtime library
│   └── src/main/java/         (schema loading, execution wiring,
│       └── com/moltenbits/gasp/runtime/    type mapping helpers)
│
├── gasp-micronaut/             Micronaut adapter (factory, DI)
│   └── src/main/java/
│       └── com/moltenbits/gasp/micronaut/
│
├── gasp-spring/                Spring Boot adapter (auto-configuration)
│   └── src/main/java/
│       └── com/moltenbits/gasp/spring/
│
├── gasp-jooq/                  jOOQ integration for dynamic queries (planned)
│   └── src/main/java/
│       └── com/moltenbits/gasp/jooq/
│
└── examples/
    ├── micronaut-example/      Working Micronaut 5 example app
    └── spring-example/         Working Spring Boot 4 example app
```

### 3.1 Dependency Graph

```
gasp-annotations  ←── gasp-processor
       ↑                    ↑
       │               (compile-only)
       │
gasp-runtime  ←── gasp-jooq
       ↑               ↑
       │               │
gasp-micronaut    gasp-spring
(+ graphql-java)  (+ graphql-java)
```

Users depend on one adapter module + optionally gasp-jooq. The processor is a compile-only dependency (annotationProcessor/apt).

---

## 4. Annotations API (`gasp-annotations`)

This module has **zero dependencies** — just annotation definitions. Users add it as a compile dependency.

### 4.1 Type Annotations

```java
@Target(TYPE)
@Retention(CLASS)  // only needed at compile time
public @interface GraphQLType {
    String name() default "";           // defaults to class simple name
    String description() default "";
}
```

```java
@Target(TYPE)
@Retention(CLASS)
public @interface GraphQLInputType {
    String name() default "";           // defaults to {ClassName}Input
    String description() default "";
}
```

```java
@Target(TYPE)
@Retention(CLASS)
public @interface GraphQLInterface {
    String name() default "";
}
```

```java
@Target(TYPE)
@Retention(CLASS)
public @interface GraphQLEnum {
    String name() default "";
}
```

### 4.2 Field Annotations

```java
@Target({METHOD, FIELD})
@Retention(CLASS)
public @interface GraphQLField {
    String name() default "";           // defaults to field/getter name
    String description() default "";
    boolean deprecated() default false;
    String deprecationReason() default "";
}
```

```java
@Target({METHOD, FIELD})
@Retention(CLASS)
public @interface GraphQLIgnore { }
```

```java
@Target({METHOD, FIELD})
@Retention(CLASS)
public @interface GraphQLId { }         // maps to GraphQL ID type
```

```java
@Target({METHOD, FIELD, PARAMETER})
@Retention(CLASS)
public @interface GraphQLNonNull { }    // override nullability
```

### 4.3 Operation Annotations

```java
@Target(TYPE)
@Retention(CLASS)
public @interface GraphQLApi { }        // marks a service class
```

```java
@Target(METHOD)
@Retention(CLASS)
public @interface GraphQLQuery {
    String name() default "";           // defaults to method name
    String description() default "";
}
```

```java
@Target(METHOD)
@Retention(CLASS)
public @interface GraphQLMutation {
    String name() default "";
    String description() default "";
}
```

```java
@Target(METHOD)
@Retention(CLASS)
public @interface GraphQLSubscription {
    String name() default "";
    String description() default "";
}
```

```java
@Target(PARAMETER)
@Retention(CLASS)
public @interface GraphQLArgument {
    String name() default "";           // defaults to parameter name
    String description() default "";
    String defaultValue() default "";
}
```

### 4.4 Relationship / Fetch Annotations

```java
@Target({METHOD, FIELD})
@Retention(CLASS)
public @interface GraphQLRelation {
    /**
     * The entity type at the other end of the relation.
     * Processor infers this from the field type when possible.
     */
    Class<?> entity() default void.class;
}
```

```java
@Target({METHOD, FIELD})
@Retention(CLASS)
public @interface GraphQLBatched { }    // hint: use DataLoader batching
```

### 4.5 Third-Party Annotation Interop

The processor recognizes these without requiring GASP annotations (detected by annotation name string — no compile dependency needed):

**JSpecify:**
- `@org.jspecify.annotations.NonNull` → treated as `@GraphQLNonNull`
- `@org.jspecify.annotations.Nullable` → explicitly nullable (overrides any non-null default)

**JPA / Micronaut Data:**
- `@jakarta.persistence.Entity` / `@io.micronaut.data.annotation.MappedEntity` → treated as `@GraphQLType`
- `@jakarta.persistence.Id` / `@io.micronaut.data.annotation.Id` → treated as `@GraphQLId`
- `@jakarta.persistence.Transient` → treated as `@GraphQLIgnore`
- `@jakarta.persistence.Column(nullable = false)` → treated as `@GraphQLNonNull`
- `@jakarta.persistence.OneToMany`, `@ManyToOne`, etc. → treated as `@GraphQLRelation`

GASP's own annotations take precedence when present. JSpecify `@Nullable` takes precedence over JSpecify `@NonNull`. JSpecify annotations use `@Target(TYPE_USE)`, so the processor checks both element-level and type-use annotations.

This means a JPA/Micronaut Data entity with JSpecify nullability annotations works with zero GASP annotations for basic cases.

---

## 5. Annotation Processor Internals (`gasp-processor`)

### 5.1 Entry Point

```java
@SupportedAnnotationTypes({
    "com.moltenbits.gasp.annotation.GraphQLType",
    "com.moltenbits.gasp.annotation.GraphQLApi",
    "com.moltenbits.gasp.annotation.GraphQLEnum",
    "com.moltenbits.gasp.annotation.GraphQLInterface",
    "jakarta.persistence.Entity",
    "io.micronaut.data.annotation.MappedEntity"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class GaspProcessor extends AbstractProcessor {
    // Standard JSR 269 processor
}
```

Registered via `META-INF/services/javax.annotation.processing.Processor`.

### 5.2 Processing Pipeline

```
1. Collect          Gather all annotated elements into a model
       │
       ▼
2. Resolve          Resolve types: Java types → GraphQL types
       │            Handle generics, collections, optionals, enums
       ▼
3. Validate         Check for errors:
       │            - Circular references without @GraphQLRelation
       │            - Unsupported types
       │            - Duplicate type/field names
       │            - Missing return types on operations
       ▼
4. Generate         Emit source files:
                    - SDL (schema.graphqls)
                    - TypeRegistry (assembles the schema)
                    - DataFetcher classes (one per operation)
                    - FieldMapping classes (GraphQL name → column)
                    - QueryBuilder classes (jOOQ query assembly)
```

### 5.3 Internal Model

```java
// Built during the Collect phase from annotation processing TypeElements

record SchemaModel(
    List<ObjectTypeModel> types,
    List<InputTypeModel> inputTypes,
    List<EnumTypeModel> enums,
    List<InterfaceTypeModel> interfaces,
    List<OperationModel> queries,
    List<OperationModel> mutations,
    List<OperationModel> subscriptions
)

record ObjectTypeModel(
    String graphQLName,
    String javaQualifiedName,
    String description,
    List<FieldModel> fields
)

record FieldModel(
    String graphQLName,
    String javaFieldName,          // or getter method name
    GraphQLTypeRef type,           // resolved GraphQL type reference
    boolean nullable,
    boolean isRelation,
    String description
)

record OperationModel(
    OperationKind kind,            // QUERY, MUTATION, SUBSCRIPTION
    String graphQLName,
    String description,
    GraphQLTypeRef returnType,
    String serviceClass,           // qualified name of the @GraphQLApi class
    String methodName,
    List<ArgumentModel> arguments,
    int envParameterIndex          // index of DataFetchingEnvironment param, or -1
)

record ArgumentModel(
    String graphQLName,
    String javaName,
    String javaType,               // original Java type for correct coercion
    GraphQLTypeRef type,
    String defaultValue
)

// Type references (resolved from Java types)
sealed interface GraphQLTypeRef {
    record Scalar(String name) implements GraphQLTypeRef { }          // String, Int, Float, Boolean, ID
    record ObjectRef(String name) implements GraphQLTypeRef { }       // reference to a named type
    record EnumRef(String name) implements GraphQLTypeRef { }
    record ListOf(GraphQLTypeRef inner) implements GraphQLTypeRef { } // [T]
    record NonNull(GraphQLTypeRef inner) implements GraphQLTypeRef { }
}
```

### 5.4 Type Resolution Rules

The resolver maps `javax.lang.model.type.TypeMirror` → `GraphQLTypeRef`:

| Java type | GraphQL type | Notes |
|---|---|---|
| `String`, `CharSequence` | `String` | |
| `int`, `Integer`, `short`, `Short`, `byte`, `Byte` | `Int` | |
| `long`, `Long`, `BigInteger` | `Int` | Or `ID` if `@GraphQLId` |
| `float`, `Float`, `double`, `Double`, `BigDecimal` | `Float` | |
| `boolean`, `Boolean` | `Boolean` | |
| `java.util.UUID` | `ID` | |
| `java.time.LocalDate` | `Date` scalar | Custom scalar |
| `java.time.LocalDateTime` | `DateTime` scalar | Custom scalar |
| `java.time.Instant`, `OffsetDateTime`, `ZonedDateTime` | `DateTime` scalar | Custom scalar |
| `List<T>`, `Set<T>`, `Collection<T>`, `Iterable<T>` | `[T]` | Recurse on `T` |
| `Optional<T>` | nullable `T` | Strips NonNull |
| `CompletableFuture<T>`, `Publisher<T>`, `Mono<T>`, `Flux<T>` | unwrap `T` | Async wrappers |
| Java enum | GraphQL enum | Auto-registered |
| `@GraphQLType` class | Object type ref | By name |
| `@Entity` / `@MappedEntity` class | Object type ref | By name |
| Primitives (`int`, `boolean`, etc.) | NonNull wrapped | Primitives can't be null |

### 5.5 Validation (Compile-Time Errors)

The processor emits `Messager.ERROR` for:
- `@GraphQLQuery` method with `void` return type
- `@GraphQLType` with no accessible fields/getters
- Two types resolving to the same GraphQL name
- `@GraphQLArgument` on a type the processor can't map
- Circular type references without `@GraphQLRelation` (would cause infinite SDL)

The processor emits `Messager.WARNING` for:
- Entity field with no recognizable GraphQL type mapping
- `@GraphQLIgnore` on a field that wouldn't have been included anyway

---

## 6. Code Generation — What Gets Produced

### 6.1 SDL File

`META-INF/gasp/schema.graphqls` — generated as a resource file. Example from:

```java
@GraphQLType
@MappedEntity
public class Book {
    @Id @GeneratedValue
    private Long id;
    private String title;
    @ManyToOne
    private Author author;
    private LocalDate publishedDate;
}

@GraphQLType
@MappedEntity
public class Author {
    @Id @GeneratedValue
    private Long id;
    private String name;
}

@GraphQLApi
public class BookService {
    @GraphQLQuery
    public Book book(@GraphQLArgument(name = "id") Long id) { ... }

    @GraphQLQuery
    public List<Book> books() { ... }

    @GraphQLMutation
    public Book createBook(@GraphQLArgument(name = "input") BookInput input) { ... }
}
```

Produces:

```graphql
type Book {
  id: ID!
  title: String!
  author: Author
  publishedDate: Date
}

type Author {
  id: ID!
  name: String!
}

input BookInput {
  title: String!
  authorId: ID
  publishedDate: Date
}

type Query {
  book(id: ID!): Book
  books: [Book]
}

type Mutation {
  createBook(input: BookInput!): Book
}

scalar Date
```

### 6.2 Type Registry

`GaspSchemaRegistry.java` — a generated class that builds the `GraphQLSchema` from the SDL and wires all DataFetchers. Single point of assembly.

```java
// GENERATED — do not edit
package com.moltenbits.gasp.generated;

public final class GaspSchemaRegistry {

    private final Map<String, DataFetcher<?>> queryFetchers = new LinkedHashMap<>();
    private final Map<String, DataFetcher<?>> mutationFetchers = new LinkedHashMap<>();
    private final Map<String, Map<String, DataFetcher<?>>> typeFetchers = new LinkedHashMap<>();

    public GaspSchemaRegistry(
            BookService_BookFetcher bookFetcher,
            BookService_BooksFetcher booksFetcher,
            BookService_CreateBookFetcher createBookFetcher,
            Book_AuthorFetcher bookAuthorFetcher
    ) {
        queryFetchers.put("book", bookFetcher);
        queryFetchers.put("books", booksFetcher);
        mutationFetchers.put("createBook", createBookFetcher);
        typeFetchers.computeIfAbsent("Book", k -> new LinkedHashMap<>())
                     .put("author", bookAuthorFetcher);
    }

    public GraphQLSchema buildSchema() {
        // Parse SDL from classpath resource
        // Wire fetchers via RuntimeWiring
        // Return executable schema
    }
}
```

### 6.3 DataFetcher Classes

One per operation:

```java
// GENERATED — do not edit
package com.moltenbits.gasp.generated;

public class BookService_BookFetcher implements DataFetcher<Object> {

    private final BookService service;

    public BookService_BookFetcher(BookService service) {
        this.service = service;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Long id = env.getArgument("id");
        return service.book(id);
    }
}
```

### 6.4 Relation Fetchers

For `@ManyToOne` / `@GraphQLRelation` fields, the processor generates separate DataFetchers that resolve the relation. These are wired as type-level fetchers (on the `Book` type's `author` field), not query-level.

```java
// GENERATED — do not edit
package com.moltenbits.gasp.generated;

public class Book_AuthorFetcher implements DataFetcher<Object> {

    private final AuthorRepository authorRepository;  // or jOOQ DSLContext

    public Book_AuthorFetcher(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Book book = env.getSource();
        // Only called if 'author' was in the selection set
        return authorRepository.findById(book.getAuthorId());
    }
}
```

### 6.5 Field Mappings

`BookFieldMapping.java` — maps GraphQL field names to database column metadata. Used by the jOOQ query builder.

```java
// GENERATED — do not edit
package com.moltenbits.gasp.generated;

public final class BookFieldMapping {

    public static final String TABLE_NAME = "book";
    public static final String GRAPH_TYPE_NAME = "Book";

    public static final Map<String, String> FIELD_TO_COLUMN = Map.of(
        "id", "id",
        "title", "title",
        "publishedDate", "published_date"
    );

    public static final Map<String, RelationMapping> RELATIONS = Map.of(
        "author", new RelationMapping("author", "author_id", "id", "Author")
    );

    public record RelationMapping(
        String table,
        String foreignKey,
        String referencedColumn,
        String graphQLType
    ) { }
}
```

---

## 7. jOOQ Integration (`gasp-jooq`)

This module provides `JooqComposableQuery<T>`, a `ComposableQuery<T>` implementation backed by jOOQ. Users return `JooqComposableQuery` objects from their fetcher methods, and the framework composes them across the GraphQL type hierarchy into a single optimized SQL query.

### 7.1 JooqComposableQuery

```java
public class JooqComposableQuery<T> implements ComposableQuery<T> {

    private final DSLContext dsl;
    private final Table<?> table;
    private final List<Condition> conditions;
    private final Map<String, Field<?>> fieldMap;          // GraphQL name → jOOQ Field
    private final Map<String, RelationMapping> relations;

    // User-facing API for adding constraints
    public JooqComposableQuery<T> where(Condition condition) { ... }

    // Framework uses these during composition:
    // - Inspects the selection set to determine columns and joins
    // - Merges conditions from parent and child fetchers
    // - Executes a single query with the composed result
}
```

### 7.2 Usage in Fetcher Methods

```java
@GraphQLApi
public class BookService {

    @GraphQLQuery
    public JooqComposableQuery<Book> books(DataFetchingEnvironment env) {
        Principal user = env.getGraphQlContext().get("principal");
        return JooqComposableQuery.of(Book.class)
                .where(BOOK.OWNER_ID.eq(user.getId()));
    }
}

public class AuthorService {

    @GraphQLField(on = Book.class)
    public JooqComposableQuery<Author> authors(Book source, DataFetchingEnvironment env) {
        return JooqComposableQuery.of(Author.class)
                .where(AUTHOR.BOOK_ID.eq(source.getId()))
                .where(AUTHOR.ACTIVE.isTrue());
    }
}
```

The framework receives both queries, composes them (adding joins and selection set optimization), and executes a single SQL statement.

### 7.3 Without jOOQ

The framework works without jOOQ. Without `gasp-jooq`, fetcher methods return concrete objects directly (like the `BookService_BookFetcher` example above). The user handles data access however they want — calling services, repositories, etc. Each fetcher executes independently, which may result in multiple queries. The jOOQ integration is the optimization layer, not a requirement.

---

## 8. Framework Adapters

Both adapters are implemented and have working example applications with integration tests.

### 8.1 Micronaut Adapter

`gasp-micronaut` — a Micronaut library (`io.micronaut.library` plugin) with a single factory class:

```java
@Factory
public class GaspGraphQLFactory {

    @Singleton
    public GraphQLSchema graphQLSchema(SchemaProvider schemaProvider) {
        return schemaProvider.buildSchema();
    }

    @Singleton
    public GraphQL graphQL(GraphQLSchema schema) {
        return GraphQL.newGraphQL(schema).build();
    }
}
```

Micronaut's `micronaut-graphql` module provides the `/graphql` HTTP endpoint automatically when a `GraphQL` bean is present. The processor generates `@Named @Singleton` on DataFetcher and Registry classes so Micronaut's compile-time DI discovers them automatically.

The `gasp.micronaut` convention plugin in `buildSrc` configures everything needed for a Micronaut example app: the Micronaut Gradle plugin, GASP annotation processor, `micronaut-serde-jackson`, Netty runtime, and Spock test infrastructure.

### 8.2 Spring Adapter

`gasp-spring` — a Spring Boot auto-configuration class:

```java
@AutoConfiguration
@ConditionalOnClass(GraphQlSource.class)
@ComponentScan("com.moltenbits.gasp.generated")
public class GaspGraphQlAutoConfiguration {

    @Bean
    public GraphQlSource graphQlSource(SchemaProvider schemaProvider) {
        GraphQLSchema schema = schemaProvider.buildSchema();
        return GraphQlSource.builder(schema).build();
    }
}
```

Spring Boot's `spring-boot-starter-graphql` provides the `/graphql` endpoint. The `@ComponentScan` discovers generated classes annotated with `@Named @Singleton` (Spring recognizes JSR-330 annotations). Auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

The `gasp.spring` convention plugin in `buildSrc` configures the Spring Boot Gradle plugin, BOM-based dependency management, GASP annotation processor, and Spock + spock-spring test infrastructure.

---

## 9. Runtime Library (`gasp-runtime`)

Minimal runtime components shared across adapters:

- **`SchemaLoader`** — parses the generated `schema.graphqls` from classpath, applies RuntimeWiring from the registry, registers custom scalars
- **`SchemaProvider`** — interface implemented by the generated `GaspSchemaRegistry`; consumed by framework adapters to build the schema
- **`ScalarDefinitions`** — custom scalar implementations for `Date` (LocalDate) and `DateTime` (LocalDateTime, OffsetDateTime, ZonedDateTime, Instant) with full serialize/parseValue/parseLiteral
- **`ComposableQuery<T>`** — marker interface for composable query objects (planned, see [9.1](#91-composable-query-architecture))

This module depends on `graphql-java` and `gasp-annotations`. It does NOT depend on any web framework, ORM, or database library.

### 9.1 Composable Query Architecture

Inspired by [GORM GraphQL's](https://grails.github.io/grails-data-graphql/latest/hibernate/guide/index.html) `DetachedCriteria` pattern. The core idea: fetchers at each level of a GraphQL type hierarchy **return a query description**, not results. The framework composes these descriptions across levels and executes a single optimized query.

### 9.2 ComposableQuery Interface

```java
// gasp-runtime — marker interface, no ORM dependency
public interface ComposableQuery<T> {
    // Backend modules (gasp-jooq, JPA, etc.) implement this with
    // their own query builder APIs. The framework composes and
    // executes these — the user never calls an execute method.
}
```

This interface lives in `gasp-runtime` so all modules can reference it. Backend modules provide concrete implementations:

- **`gasp-jooq`** → `JooqComposableQuery<T>` backed by jOOQ's `SelectConditionStep`
- **Future JPA module** → backed by `CriteriaQuery` or `Specification<T>`

### 9.3 How Fetchers Return Composable Queries

A fetcher method can return either:

1. **`ComposableQuery<T>`** — the ideal path. The framework composes queries from all levels in the hierarchy into a single optimized query and executes it once.

2. **Concrete objects (`T` or `List<T>`)** — works fine, but each level fetches independently, resulting in multiple queries. Still valid, just not as efficient.

The processor detects the return type and generates the appropriate DataFetcher behavior.

### 9.4 Example: Composable Path (with jOOQ)

Field resolvers can live on the entity class itself:

```java
@GraphQLType
public class Book {
    @GraphQLId
    private Long id;
    private String title;

    // Type-level field — processor generates a DataFetcher for Book.authors
    @GraphQLField
    public JooqComposableQuery<Author> authors(DataFetchingEnvironment env) {
        return JooqComposableQuery.of(Author.class)
                .where(AUTHOR.BOOK_ID.eq(this.id))
                .where(AUTHOR.ACTIVE.isTrue());
    }
}

@GraphQLApi
public class BookService {

    @GraphQLQuery
    public JooqComposableQuery<Book> books(DataFetchingEnvironment env) {
        Principal user = env.getGraphQlContext().get("principal");
        return JooqComposableQuery.of(Book.class)
                .where(BOOK.OWNER_ID.eq(user.getId()));
    }
}
```

Or on a separate service class using `@GraphQLField(on = ...)`:

```java
public class AuthorService {

    @GraphQLField(on = Book.class)
    public JooqComposableQuery<Author> authors(Book source, DataFetchingEnvironment env) {
        return JooqComposableQuery.of(Author.class)
                .where(AUTHOR.BOOK_ID.eq(source.getId()))
                .where(AUTHOR.ACTIVE.isTrue());
    }
}
```

In both cases, the framework receives the `ComposableQuery` objects, composes them (adding joins, selection set optimization), and executes a single SQL query.

### 9.5 Example: Direct Return Path (No ORM)

Same two patterns apply — on the entity or in a service:

```java
@GraphQLType
public class Book {
    @GraphQLId
    private Long id;
    private String title;

    @GraphQLField
    public List<Author> authors() {
        return authorService.findByBookId(this.id);
    }
}

@GraphQLApi
public class BookService {

    @GraphQLQuery
    public List<Book> books(DataFetchingEnvironment env) {
        return bookService.findByOwner(currentUser(env));
    }
}
```

This works but results in N+1 queries. The user trades composition for simplicity.

### 9.6 Type-Level Fetcher Wiring

**Methods on the entity class:** A `@GraphQLField` method on a `@GraphQLType` class becomes a type-level field. The processor generates a DataFetcher that calls the method on the source object (`env.getSource()`). Field resolvers live on the entity they belong to.

**Methods on a service class:** For cases where the resolver needs dependencies the entity shouldn't know about, `@GraphQLField(on = Type.class)` on a method in any class generates a type-level fetcher for the specified type. The processor passes `env.getSource()` as the first argument. No `@GraphQLApi` annotation is needed — the processor discovers `@GraphQLField(on = ...)` methods directly.

```java
public class BookService {

    // Book.recommendations resolved externally (needs RecommendationService)
    @GraphQLField(on = Book.class)
    public List<Book> recommendations(Book source, DataFetchingEnvironment env) {
        return recommendationService.forBook(source.getId());
    }
}
```

No manual fetcher registration is needed — the processor discovers and wires everything at compile time.

---

## 10. Phased Implementation

### 10.1 Phase 1 — Foundation, Framework Adapters, and Basic Type Mapping (COMPLETE)

**Goal:** Compile-time annotation processor generates SDL, DataFetchers, and schema registry from annotated service classes and `@GraphQLType` POJOs. Working framework integrations for both Micronaut and Spring Boot with example applications.

**Delivered:**
- `gasp-annotations` module — 15 annotations + 1 enum, zero dependencies
- `gasp-processor` module — Collect → Resolve → Validate → Generate pipeline
- `gasp-runtime` module — `SchemaLoader`, `SchemaProvider` interface, `ScalarDefinitions` (Date/DateTime)
- `gasp-micronaut` module — `GaspGraphQLFactory` wiring `SchemaProvider` → `GraphQL` bean
- `gasp-spring` module — `GaspGraphQlAutoConfiguration` providing `GraphQlSource` bean
- `@GraphQLApi` + `@GraphQLQuery`/`@GraphQLMutation`/`@GraphQLSubscription` → SDL + DataFetcher generation
- `@GraphQLType` scanning → object type SDL generation from getters, public fields, record components
- Argument wiring with correct numeric type coercion (uses actual Java type)
- `DataFetchingEnvironment` pass-through — service methods can accept `env` for custom fetching
- JSpecify `@NonNull`/`@Nullable` interop (TYPE_USE annotation handling)
- JPA `@Entity` and Micronaut `@MappedEntity` recognized as `@GraphQLType` equivalents
- Generated classes annotated `@Named @Singleton` for both Micronaut and Spring DI discovery
- `buildSrc` convention plugins: `gasp.base`, `gasp.micronaut`, `gasp.spring`
- `examples/micronaut-example` and `examples/spring-example` — working apps with integration tests

### 10.2 Phase 2 — GraphQL Type System & Type-Level Fetchers (COMPLETE)

**Goal:** Full GraphQL type system support: input types, enums, interfaces. Type-level field resolution. Compile-time input type conversion. Optional `@GraphQLArgument`.

**Delivered:**
- `@GraphQLEnum` → standalone enum type scanning and SDL generation (not just tracked from operations)
- `@GraphQLInputType` → input type scanning, `input` SDL generation, and compile-time map-to-POJO conversion via generated `InputConverterGenerator` (no reflection)
- `@GraphQLInterface` → interface type scanning, `interface` SDL generation, automatic `implements` detection on object types, default `TypeResolver` for runtime resolution
- `@GraphQLField(on = Type.class)` → type-level DataFetcher generation and registry wiring from methods on any class (not just `@GraphQLApi`), with `MirroredTypeException` handling
- `@GraphQLArgument` is now optional — argument names are derived from Java parameter names; `@GraphQLArgument` only needed to override the name or add description/default value
- `ComposableQuery<T>` marker interface in `gasp-runtime`
- `InputRef` variant added to `GraphQLTypeRef` sealed interface
- `InputTypeModel`, `InterfaceTypeModel`, `TypeFetcherModel` records in processor model
- Enum argument coercion via `Enum.valueOf()` in generated code
- Non-java.lang argument type imports in generated DataFetchers
- Default `TypeResolver` via `WiringFactory` for interface/union type resolution by class simple name
- Example projects updated with `Genre` enum, `Searchable` interface, `BookInput` input type, `RecommendationService` type-level fetcher, and `booksByGenre` enum query
- 143 total tests across all modules

**What works after Phase 2:**

```java
@GraphQLEnum
public enum Genre { FICTION, FANTASY, SCIENCE_FICTION, MYSTERY }

@GraphQLInterface
public interface Searchable { String getTitle(); String getDescription(); }

@GraphQLInputType
public class BookInput { /* title, authorName, genre with getters/setters */ }

@GraphQLType
public class Book implements Searchable {
    // id, title, description, author, genre with getters
}

@GraphQLApi
@Singleton
public class BookService {
    @GraphQLQuery
    public Book book(Long id) { ... }                    // no @GraphQLArgument needed

    @GraphQLQuery
    public List<Book> booksByGenre(Genre genre) { ... }  // enum argument

    @GraphQLMutation
    public Book createBook(BookInput input) { ... }      // input type argument
}

@Singleton
public class RecommendationService {
    @GraphQLField(on = Book.class)                       // type-level fetcher
    public List<Book> recommendations(Object source) { ... }
}
```
→ Generates SDL with object types, input types, interfaces, enums, type-level fields, and operations. Input type arguments are converted from maps at compile time. All wiring is automatic.

### 10.3 Phase 3 — Entity Mapping & jOOQ Integration

**Goal:** ORM-aware entity mapping and `ComposableQuery<T>` implementation backed by jOOQ. The framework composes query objects returned by fetchers across the type hierarchy into a single optimized SQL query.

**Deliverables:**
- `@MappedEntity` / `@Entity` → automatic field scanning with JPA/Micronaut Data annotation support at the field level (`@Column(nullable = false)` → `NonNull`, `@Transient` → ignored, `@OneToMany`/`@ManyToOne` → relation)
- FieldMapping class generation (GraphQL name → Java field → column name)
- `gasp-jooq` module
- `JooqComposableQuery<T>` — `ComposableQuery<T>` implementation backed by jOOQ's `SelectConditionStep`
- Framework composition engine: merges `ComposableQuery` objects from parent and child fetchers into a single query with appropriate joins, column selection, and where clauses
- Generated DataFetchers detect `ComposableQuery` return types vs concrete objects
- Selection set awareness: only SELECT requested columns, only JOIN requested relations
- Generated FieldMapping → jOOQ Field resolution
- Support for `@GraphQLRelation` on entity fields
- DataLoader integration for batched relation fetching (N+1 prevention)
- Argument handling: `where`, `orderBy`, `limit`, `offset` from GraphQL arguments

**What works after Phase 3:**

Fetchers return `JooqComposableQuery<T>` and the framework composes them:

```java
@GraphQLQuery
public JooqComposableQuery<Book> books(DataFetchingEnvironment env) {
    return JooqComposableQuery.of(Book.class)
            .where(BOOK.OWNER_ID.eq(currentUserId(env)));
}
```

```graphql
{ books { id title } }
```
→ `SELECT id, title FROM book WHERE owner_id = ?` (no author join, no extra columns)

```graphql
{ books { id title author { name } } }
```
→ `SELECT book.id, book.title, author.name FROM book LEFT JOIN author ON ... WHERE owner_id = ?`

Queries from multiple levels compose into a single SQL statement whenever possible.

### 10.4 Phase 4 — Advanced Features

- **Pagination:** Relay connection types, auto-generated `first`/`after`/`last`/`before` arguments
- **Filtering:** Auto-generated `where` input types from entity fields (`{ books(where: { title: { contains: "Java" } }) { ... } }`)
- **Sorting:** Auto-generated `orderBy` enum from entity fields
- **Subscriptions:** Reactive `Publisher` return types → GraphQL subscriptions
- **DataLoader generation:** Automatic batching for `@GraphQLRelation` fields
- **Custom scalars:** User-defined scalar type registration
- **Schema stitching:** Compose multiple `@GraphQLApi` modules
- **Authorization directives:** `@GraphQLAuth(role = "ADMIN")` → schema directive + runtime check

---

## 11. Open Questions

1. ~~**Record support:**~~ **Resolved.** The processor handles records as first-class types via record component scanning.

2. ~~**Input type generation strategy:**~~ **Resolved.** Explicit `@GraphQLInputType` is required. The processor generates a compile-time converter class (no reflection) for map-to-POJO conversion when used as a mutation argument.

3. **jOOQ codegen ordering:** jOOQ generates from the database schema; GASP generates from Java annotations. These are independent build phases. Do we need to coordinate them, or do they just both contribute to the final compilation?

4. **Incremental compilation:** JSR 269 processors can support incremental compilation. This is important for developer experience. The processor should track which source files contribute to which generated files.

5. **Column naming convention:** Should the processor infer column names from field names (camelCase → snake_case)? Or require explicit `@Column`/`@MappedProperty`? Micronaut Data and JPA both have conventions here we should respect.

6. **Nullability defaults:** GraphQL fields are nullable by default; Java primitives are non-null. What's the default for reference types? Options:
   - Follow Java: reference types are nullable (matches GraphQL default)
   - Follow Kotlin-style: everything non-null unless `Optional` or `@Nullable`
   - Configurable via processor option

7. **Testing story:** How do users test their GraphQL API? We should provide test utilities — perhaps a `GaspTestClient` that sends queries and returns typed results.

8. **Error handling:** How do service method exceptions map to GraphQL errors? Generate try/catch in DataFetchers that wraps exceptions in `GraphQLError`?

---

## 12. Build Configuration

Users add GASP to their project:

```kotlin
// build.gradle.kts
dependencies {
    annotationProcessor("com.moltenbits.gasp:gasp-processor:1.0.0")

    implementation("com.moltenbits.gasp:gasp-annotations:1.0.0")
    implementation("com.moltenbits.gasp:gasp-micronaut:1.0.0")   // or gasp-spring
    implementation("com.moltenbits.gasp:gasp-jooq:1.0.0")        // optional
}
```

That's it. Write your entities and services, annotate them, compile, and the GraphQL API exists.
