# gasp-micronaut

Micronaut framework adapter for GASP. This module will provide drop-in integration between GASP's generated schema infrastructure and a Micronaut application — DI wiring, HTTP endpoint, and configuration properties.

> **Status:** Placeholder. Implementation is planned for Phase 3.

## Dependency

```kotlin
dependencies {
    implementation("com.moltenbits.gasp:gasp-micronaut:0.1.0-SNAPSHOT")
}
```

Currently depends on `gasp-runtime` only.

## Planned functionality

### Bean factory

A `@Factory` class that auto-wires the generated `GaspSchemaRegistry` into the Micronaut application context and produces `GraphQLSchema` and `GraphQL` singleton beans:

```java
@Factory
@Requires(classes = GaspSchemaRegistry.class)
public class GaspMicronautFactory {

    @Singleton
    public GraphQLSchema graphQLSchema(GaspSchemaRegistry registry) {
        return registry.buildSchema();
    }

    @Singleton
    public GraphQL graphQL(GraphQLSchema schema) {
        return GraphQL.newGraphQL(schema).build();
    }
}
```

### GraphQL HTTP controller

An HTTP controller that accepts GraphQL requests and executes them against the wired schema:

- `POST /graphql` with JSON body (`query`, `variables`, `operationName`)
- `POST /graphql` with `application/graphql` content type (raw query string)
- `GET /graphql` with query parameters

### Configuration properties

Configurable via `application.yml`:

```yaml
gasp:
  http:
    endpoint: /graphql    # default
    enabled: true         # default
```

### DataFetcher DI integration

Generated DataFetcher classes will be annotated with `@Singleton` by the processor when the Micronaut adapter is on the classpath, enabling Micronaut's compile-time dependency injection to discover and wire them automatically.

### Integration testing

A test suite that boots a Micronaut application context with annotated services, verifies the `/graphql` endpoint responds correctly, and validates end-to-end query execution.
