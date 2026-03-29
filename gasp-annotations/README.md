# gasp-annotations

Zero-dependency annotation library that defines the GASP API surface. Users annotate their Java classes, methods, and fields with these annotations; the `gasp-processor` reads them at compile time to generate a complete GraphQL schema and all supporting code.

All annotations use `@Retention(CLASS)` — they are only needed during compilation and carry no runtime footprint.

## Dependency

```kotlin
dependencies {
    implementation("com.moltenbits.gasp:gasp-annotations:0.1.0-SNAPSHOT")
}
```

This module has **no transitive dependencies**.

## Annotations

### Type annotations

These mark classes for inclusion in the generated GraphQL schema.

| Annotation | Target | Purpose |
|---|---|---|
| `@GraphQLType` | `TYPE` | Exposes a class as a GraphQL object type. Fields are inferred from getters/record components. |
| `@GraphQLInputType` | `TYPE` | Exposes a class as a GraphQL input type, used as a mutation/query argument. |
| `@GraphQLInterface` | `TYPE` | Declares a GraphQL interface type. |
| `@GraphQLEnum` | `TYPE` | Exposes a Java enum as a GraphQL enum type. Enum constants become enum values. |

Each type annotation accepts an optional `name` element that overrides the default (the class simple name). `@GraphQLType` and `@GraphQLInputType` also accept a `description`.

```java
@GraphQLType(name = "BookDetail", description = "Extended book information")
public class Book { ... }
```

### Operation annotations

These define the service layer — methods that become top-level Query, Mutation, or Subscription fields.

| Annotation | Target | Purpose |
|---|---|---|
| `@GraphQLApi` | `TYPE` | Marks a class as a GraphQL service endpoint. The processor scans its methods for operation annotations. |
| `@GraphQLQuery` | `METHOD` | Exposes a method as a `Query` field. |
| `@GraphQLMutation` | `METHOD` | Exposes a method as a `Mutation` field. |
| `@GraphQLSubscription` | `METHOD` | Exposes a method as a `Subscription` field. |
| `@GraphQLArgument` | `PARAMETER` | Configures a method parameter as a named GraphQL argument, with optional description and default value. |

`@GraphQLQuery`, `@GraphQLMutation`, and `@GraphQLSubscription` accept `name` (defaults to the method name) and `description`. `@GraphQLArgument` also accepts `defaultValue`.

```java
@GraphQLApi
public class BookService {

    @GraphQLQuery(description = "Find a book by ID")
    public Book book(@GraphQLArgument(name = "id") Long id) {
        return bookRepository.findById(id);
    }

    @GraphQLMutation
    public Book createBook(@GraphQLArgument(name = "input") BookInput input) {
        return bookRepository.save(map(input));
    }
}
```

### Field annotations

These control how individual fields and properties appear in the GraphQL schema.

| Annotation | Target | Purpose |
|---|---|---|
| `@GraphQLField` | `METHOD`, `FIELD` | Overrides the name, description, or deprecation status of a field in the schema. |
| `@GraphQLIgnore` | `METHOD`, `FIELD` | Excludes a field or getter from the generated schema entirely. |
| `@GraphQLId` | `METHOD`, `FIELD` | Forces the field's GraphQL type to `ID` (overrides the default scalar mapping for `Long`, `Integer`, `UUID`, etc.). |
| `@GraphQLNonNull` | `METHOD`, `FIELD`, `PARAMETER` | Marks the field as non-nullable (`!`) in the schema, overriding default nullability. |

```java
@GraphQLType
public class Book {
    @GraphQLId
    private Long id;                          // → ID! in SDL

    @GraphQLField(description = "Full title including subtitle")
    private String title;

    @GraphQLIgnore
    private String internalNotes;             // excluded from schema

    @GraphQLNonNull
    private String isbn;                      // → String! in SDL
}
```

### Relationship annotations

These declare associations between GraphQL types, enabling the processor to generate relation-aware fetchers and query builders in later phases.

| Annotation | Target | Purpose |
|---|---|---|
| `@GraphQLRelation` | `METHOD`, `FIELD` | Declares a relationship to another entity. The `entity` element optionally specifies the target type (inferred from the field type when possible). |
| `@GraphQLBatched` | `METHOD`, `FIELD` | Hints that the field should be resolved via DataLoader batching to prevent N+1 queries. |

```java
@GraphQLType
public class Book {
    @GraphQLRelation
    private Author author;

    @GraphQLRelation
    @GraphQLBatched
    private List<Review> reviews;
}
```

## OperationKind enum

The `OperationKind` enum (`QUERY`, `MUTATION`, `SUBSCRIPTION`) is a shared constant used by the processor's internal model to classify operations. It is part of the public API so that advanced users and tooling can reference operation kinds programmatically.

## Third-party annotation interop

The processor recognizes several third-party annotations without requiring GASP annotations. No compile dependency on these libraries is needed — the processor checks annotation names by string.

### JSpecify

| Third-party annotation | GASP equivalent |
|---|---|
| `@org.jspecify.annotations.NonNull` | `@GraphQLNonNull` |
| `@org.jspecify.annotations.Nullable` | Explicitly nullable (overrides any non-null default) |

### JPA / Micronaut Data

| Third-party annotation | GASP equivalent |
|---|---|
| `@jakarta.persistence.Entity`, `@io.micronaut.data.annotation.MappedEntity` | `@GraphQLType` |
| `@jakarta.persistence.Id`, `@io.micronaut.data.annotation.Id` | `@GraphQLId` |
| `@jakarta.persistence.Transient` | `@GraphQLIgnore` |
| `@jakarta.persistence.Column(nullable = false)` | `@GraphQLNonNull` |
| `@jakarta.persistence.OneToMany`, `@ManyToOne`, etc. | `@GraphQLRelation` |

This means standard JPA, Micronaut Data, or JSpecify-annotated code works out of the box. GASP annotations can be layered on top to override defaults.
