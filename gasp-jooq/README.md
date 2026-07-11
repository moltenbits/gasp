# gasp-jooq

jOOQ integration for GASP. This module provides two things:

1. **`GaspJooqGenerator`** — a custom jOOQ code generator that annotates generated Table classes with GASP annotations, enabling `gasp-processor` to generate the GraphQL schema directly from your database model.

2. **`JooqComposableQuery<T>`** — a composable query builder that uses jOOQ's MULTISET to fetch an entity and its associations in a single SQL statement. Used by DataFetchers for efficient query execution.

## GaspJooqGenerator

Extends jOOQ's `JavaGenerator` to add GASP annotations to generated **Table classes** (e.g. `Book extends TableImpl<BookRecord>`) during code generation. The Table class already contains column definitions, primary keys, foreign keys, and relationship paths — the generator annotates them so that `gasp-processor` can read the schema.

### What gets annotated

**Class-level:**
- `@GraphQLType(explicitFieldsOnly = true)` on Table classes. Join tables (e.g. `book_author`) are skipped. The `explicitFieldsOnly` flag ensures only annotated methods are included in the schema — inherited `TableImpl` methods and fields are ignored.

**Column getters** (generated in the Table class footer):
- `@GraphQLField` on each column getter (e.g. `getTitle()`, `getGenre()`)
- `@GraphQLId` on primary key column getters
- `@GraphQLNonNull` on non-nullable column getters (excluding PKs which already have `@GraphQLId`)
- For columns whose getter name would conflict with inherited methods (e.g. `getName()`, `getComment()`), the getter is renamed (e.g. `getNameField()`) with `@GraphQLField(name = "name")` to preserve the GraphQL field name

**Relationship methods** (generated in the Table class footer):
- `@GraphQLRelation(entity = X.class)` on relationship getters, derived from foreign key metadata:
  - **One-to-many:** e.g. `review.book_id → book` generates `@GraphQLRelation(entity = Review.class) List<Review> getReviews()` on the `Book` Table class
  - **Many-to-many:** e.g. `book_author` join table generates `@GraphQLRelation(entity = Author.class) List<Author> getAuthors()` on the `Book` Table class
  - **Many-to-one:** e.g. `review.book_id → book` generates `@GraphQLRelation(entity = Book.class) Book getBook()` on the `Review` Table class
  - **Self-referential:** field names are derived from the FK constraint name. e.g. `FK_BOOK_PREQUEL` generates `getPrequel()`, `FK_BOOK_SEQUEL` generates `getSequel()`

Once annotated, `gasp-processor` picks up the Table classes like any other `@GraphQLType` class and generates the SDL, DataFetchers, and schema registry. No hand-written GraphQL types needed.

### Configuration

Set `GaspJooqGenerator` as the generator in your jOOQ codegen config:

```kotlin
jooq {
    configuration {
        generator {
            name = "com.moltenbits.gasp.jooq.GaspJooqGenerator"
            // ... database, target config as usual
        }
    }
}
```

Add `gasp-jooq` to the jOOQ codegen classpath:

```kotlin
dependencies {
    jooqCodegen(project(":gasp-jooq"))
    // or: jooqCodegen("com.moltenbits.gasp:gasp-jooq:0.1.0-SNAPSHOT")
}
```

## JooqComposableQuery

Implements `ComposableQuery<T>` from `gasp-runtime`. Builds a single SQL statement with jOOQ, using MULTISET correlated subqueries for nested associations.

### Usage

```java
// List query with associations
JooqComposableQuery.<Book>listOf(dsl, BOOK, BOOK.ID, BOOK.TITLE, BOOK.GENRE)
    .where(BOOK.GENRE.eq("FANTASY"))
    .withMultiset("authors", select(AUTHOR.ID, AUTHOR.NAME).from(AUTHOR)
        .join(BOOK_AUTHOR).on(BOOK_AUTHOR.AUTHOR_ID.eq(AUTHOR.ID))
        .where(BOOK_AUTHOR.BOOK_ID.eq(BOOK.ID)))
    .mapToGraph(r -> {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", r.get(BOOK.TITLE));
        // ... map fields and MULTISET results
        return map;
    })

// Single-result query
JooqComposableQuery.<Book>oneOf(dsl, BOOK, BOOK.ID, BOOK.TITLE)
    .where(BOOK.ID.eq(id))
    .mapToGraph(r -> { ... })
```

### API

| Method | Description |
|---|---|
| `listOf(dsl, table, fields...)` | Create a list query. Type parameter determines the GraphQL return type (`[T]` in SDL). |
| `oneOf(dsl, table, fields...)` | Create a single-result query. Type parameter determines the GraphQL return type (`T` in SDL). |
| `where(condition)` | Add a WHERE clause. Immutable — returns a new query instance. |
| `withMultiset(name, subquery)` | Add a correlated MULTISET subquery for a nested association. |
| `mapper(fn)` | Set a typed Record-to-POJO mapping function. |
| `mapToGraph(fn)` | Set a mapper that produces `Map<String, Object>` for graphql-java's `PropertyDataFetcher` resolution. The type parameter is preserved for SDL generation. |
| `fetchAll()` | Execute and return all results. Called by the framework for list queries. |
| `fetchOne()` | Execute and return a single result. Called by the framework for single-result queries. |
| `isList()` | Returns whether this is a list or single-result query. Used by generated DataFetchers to dispatch. |

### How MULTISET works

For a query like `{ books { title authors { name } reviews { rating } } }`, `JooqComposableQuery` builds:

```sql
SELECT 
  book.id, book.title, book.genre,
  (SELECT author.id, author.name FROM author 
   JOIN book_author ON book_author.author_id = author.id 
   WHERE book_author.book_id = book.id) AS authors,
  (SELECT review.id, review.rating FROM review 
   WHERE review.book_id = book.id) AS reviews
FROM book
WHERE book.genre = 'FANTASY'
```

One SQL statement. One database round trip. Associations are embedded as correlated subqueries.

## Dependencies

```kotlin
dependencies {
    api(project(":gasp-annotations"))
    api(project(":gasp-runtime"))
    api("org.jooq:jooq")
    implementation("org.jooq:jooq-codegen")
}
```
