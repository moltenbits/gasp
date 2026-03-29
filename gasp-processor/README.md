# gasp-processor

JSR 269 annotation processor that reads GASP annotations at compile time and generates a complete GraphQL schema, DataFetcher implementations, and a schema registry — all as source code and resources, with zero runtime reflection.

## Dependency

The processor is a compile-only dependency. It runs during `javac` and produces generated sources that are compiled into the application.

```kotlin
dependencies {
    annotationProcessor("com.moltenbits.gasp:gasp-processor:0.1.0-SNAPSHOT")
}
```

## What it generates

Given annotated `@GraphQLApi` service classes, the processor emits three categories of output:

### 1. SDL schema — `META-INF/gasp/schema.graphqls`

A GraphQL Schema Definition Language file written as a classpath resource. It contains:

- `type Query { ... }` with a field for each `@GraphQLQuery` method
- `type Mutation { ... }` with a field for each `@GraphQLMutation` method
- `type Subscription { ... }` with a field for each `@GraphQLSubscription` method
- `enum` definitions for any Java enums referenced by operation signatures
- `scalar` declarations for custom scalars (e.g. `Date`, `DateTime`) when used

Arguments, return types, nullability, and default values are all rendered from the resolved type model.

### 2. DataFetcher classes — one per operation

Each `@GraphQLQuery`, `@GraphQLMutation`, or `@GraphQLSubscription` method gets a generated `DataFetcher<Object>` implementation in the `com.moltenbits.gasp.generated` package.

The naming convention is `{ServiceSimpleName}_{OperationName}Fetcher`. For example, `BookService.book()` produces `BookService_BookFetcher`.

Each generated fetcher:
- Takes the service class as a constructor parameter
- Extracts arguments from `DataFetchingEnvironment` with correct type coercion
- Delegates to the original service method
- Handles numeric type coercion (`Number.intValue()` / `Number.doubleValue()`) for `Int` and `Float` arguments

### 3. Schema registry — `GaspSchemaRegistry.java`

A single generated class that:
- Accepts all generated DataFetcher instances via its constructor
- Maps each fetcher to its corresponding query/mutation/subscription field name
- Provides a `buildSchema()` method that loads the generated SDL via `SchemaLoader` and wires the fetchers into an executable `GraphQLSchema`
- Exposes getter methods for the fetcher maps (useful for testing)

## Processing pipeline

The processor runs a four-stage pipeline on each compilation round:

```
Source files with @GraphQLApi
         │
         ▼
┌─────────────────┐
│    Collector     │  Scans @GraphQLApi classes, extracts @GraphQLQuery/
│                  │  @GraphQLMutation/@GraphQLSubscription methods,
│                  │  builds OperationModel records with resolved types.
│                  │  Tracks Java enums encountered in signatures.
└────────┬────────┘
         │ SchemaModel
         ▼
┌─────────────────┐
│    Validator     │  Checks for errors: void return types, duplicate
│                  │  operation names, unmappable argument types.
│                  │  Emits Messager.ERROR / WARNING as needed.
└────────┬────────┘
         │ valid model
         ▼
┌─────────────────┐
│   SdlGenerator   │  Writes META-INF/gasp/schema.graphqls
│ DataFetcherGen.  │  Writes one .java per operation
│  RegistryGen.    │  Writes GaspSchemaRegistry.java
└─────────────────┘
```

### Collector

`Collector.java` discovers all `@GraphQLApi`-annotated classes from the `RoundEnvironment` and iterates their enclosed methods. For each operation annotation it finds, it:

1. Resolves the operation name — uses the annotation's `name` element, falling back to the Java method name
2. Resolves the return type via `TypeResolver`
3. Iterates method parameters, resolving each to a `GraphQLTypeRef` and extracting `@GraphQLArgument` metadata
4. Tracks any Java enums encountered (in return types or arguments) for automatic enum registration

The result is a `SchemaModel` containing lists of operations, enum definitions, and (in future phases) object type definitions.

### TypeResolver

`TypeResolver.java` maps `javax.lang.model.type.TypeMirror` to the `GraphQLTypeRef` sealed interface hierarchy. Resolution rules:

| Java type | GraphQL type | Notes |
|---|---|---|
| `int`, `short`, `byte` | `Int!` | Primitives are always non-null |
| `long` | `Int!` | `ID!` if `@GraphQLId` present |
| `float`, `double` | `Float!` | Primitives are always non-null |
| `boolean` | `Boolean!` | Primitives are always non-null |
| `String`, `CharSequence` | `String` | |
| `Integer`, `Short`, `Byte`, `Long`, `BigInteger` | `Int` | `ID` if `@GraphQLId` |
| `Float`, `Double`, `BigDecimal` | `Float` | |
| `Boolean` | `Boolean` | |
| `UUID` | `ID` | |
| `LocalDate` | `Date` | Custom scalar |
| `LocalDateTime`, `Instant`, `OffsetDateTime`, `ZonedDateTime` | `DateTime` | Custom scalar |
| `List<T>`, `Set<T>`, `Collection<T>`, `Iterable<T>` | `[T]` | Recurse on element type |
| `Optional<T>` | nullable `T` | Strips `NonNull` wrapper |
| `CompletableFuture<T>`, `CompletionStage<T>`, `Mono<T>`, `Single<T>`, `Maybe<T>`, `Publisher<T>` | unwrap `T` | Async wrappers are transparent |
| `Flux<T>` | `[T]` | Reactive stream → list |
| Java enum | Enum type | By simple class name |
| `@GraphQLType` / `@Entity` / `@MappedEntity` class | Object type | By simple class name or `@GraphQLType.name` |

`@GraphQLNonNull` on the annotated element wraps the result in `NonNull`. `@GraphQLId` forces the scalar to `ID`.

### Validator

`Validator.java` checks the collected model and emits compile-time diagnostics:

**Errors** (abort code generation):
- `@GraphQLQuery`/`@GraphQLMutation`/`@GraphQLSubscription` method with void or unmappable return type
- Duplicate operation names within the same operation kind
- Arguments with unmappable types

**Warnings**:
- No operations found in any `@GraphQLApi` class

### Generators

Three generators run in sequence when validation passes:

- **`SdlGenerator`** — renders the `SchemaModel` to SDL syntax using a recursive `renderTypeRef` method that maps `NonNull` → `!`, `ListOf` → `[...]`, scalars/enums/objects → by name. Tracks which custom scalars are used and appends `scalar` declarations. Writes the result as a classpath resource via `Filer.createResource`.

- **`DataFetcherGenerator`** — emits one Java source file per operation. Uses `StringBuilder`-based code generation (no template engine). Handles argument extraction with numeric coercion for `Int` (via `Number.intValue()`) and `Float` (via `Number.doubleValue()`).

- **`RegistryGenerator`** — emits `GaspSchemaRegistry.java` with a constructor that accepts all generated fetchers and maps them to field names. The `buildSchema()` method delegates to `SchemaLoader.load()`.

## Internal model

The processor uses a set of Java records in the `model` package to represent the schema:

| Record | Purpose |
|---|---|
| `SchemaModel` | Top-level container: lists of types, enums, queries, mutations, subscriptions |
| `ObjectTypeModel` | A GraphQL object type with fields (populated in Phase 2) |
| `FieldModel` | A field within an object type: name, type ref, nullability, relation flag |
| `OperationModel` | A query/mutation/subscription: name, return type, service class, method, arguments |
| `ArgumentModel` | A named argument: GraphQL name, Java name, type ref, default value |
| `EnumTypeModel` | A GraphQL enum: name, Java class, constant values |
| `GraphQLTypeRef` | Sealed interface — `Scalar`, `ObjectRef`, `EnumRef`, `ListOf`, `NonNull` variants |

## Processor registration

The processor is registered via the standard `META-INF/services/javax.annotation.processing.Processor` service file, which lists `com.moltenbits.gasp.processor.GaspProcessor`.

It processes annotations from the `com.moltenbits.gasp.annotation` package and targets Java 21 (`@SupportedSourceVersion(RELEASE_21)`). Processing runs once per compilation — subsequent rounds are skipped via a `processed` flag.
