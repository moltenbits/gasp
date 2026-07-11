package com.example

import com.example.jooq.tables.Author as AuthorTable
import com.example.jooq.tables.Book as BookTable
import com.example.jooq.tables.BookAuthor
import com.example.jooq.tables.Review as ReviewTable
import com.example.service.BookService
import com.moltenbits.gasp.generated.GaspSchemaRegistry
import com.moltenbits.gasp.generated.BookService_BooksFetcher
import com.moltenbits.gasp.generated.BookService_BookFetcher
import com.moltenbits.gasp.generated.BookService_BooksByGenreFetcher
import graphql.GraphQL
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.ExecuteContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultExecuteListener
import org.jooq.impl.DefaultExecuteListenerProvider
import spock.lang.Shared
import spock.lang.Specification

import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

class GraphQLJooqSpec extends Specification {

    @Shared DSLContext dsl
    @Shared GraphQL graphQL
    @Shared AtomicInteger queryCount = new AtomicInteger(0)

    def setupSpec() {
        def conn = DriverManager.getConnection("jdbc:h2:mem:graphql_test;DB_CLOSE_DELAY=-1", "sa", "")
        Flyway.configure()
                .dataSource("jdbc:h2:mem:graphql_test;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate()

        // Configure jOOQ with a query counter
        def counter = queryCount
        def config = new DefaultConfiguration()
                .set(conn)
                .set(SQLDialect.H2)
                .set(new DefaultExecuteListenerProvider(new DefaultExecuteListener() {
                    @Override
                    void executeStart(ExecuteContext ctx) {
                        counter.incrementAndGet()
                    }
                }))
        dsl = DSL.using(config)

        // Seed data
        dsl.insertInto(AuthorTable.AUTHOR).set(AuthorTable.AUTHOR.NAME, "J.R.R. Tolkien").execute()
        dsl.insertInto(AuthorTable.AUTHOR).set(AuthorTable.AUTHOR.NAME, "George Orwell").execute()

        def tolkienId = dsl.selectFrom(AuthorTable.AUTHOR).where(AuthorTable.AUTHOR.NAME.eq("J.R.R. Tolkien")).fetchOne().getId()
        def orwellId = dsl.selectFrom(AuthorTable.AUTHOR).where(AuthorTable.AUTHOR.NAME.eq("George Orwell")).fetchOne().getId()

        dsl.insertInto(BookTable.BOOK)
                .set(BookTable.BOOK.TITLE, "The Hobbit")
                .set(BookTable.BOOK.ISBN, "9780547928227")
                .set(BookTable.BOOK.DESCRIPTION, "A hobbit's adventure")
                .set(BookTable.BOOK.GENRE, "FANTASY")
                .execute()
        dsl.insertInto(BookTable.BOOK)
                .set(BookTable.BOOK.TITLE, "1984")
                .set(BookTable.BOOK.DESCRIPTION, "Dystopian classic")
                .set(BookTable.BOOK.GENRE, "FICTION")
                .execute()

        def hobbitId = dsl.selectFrom(BookTable.BOOK).where(BookTable.BOOK.TITLE.eq("The Hobbit")).fetchOne().getId()
        def orwell1984Id = dsl.selectFrom(BookTable.BOOK).where(BookTable.BOOK.TITLE.eq("1984")).fetchOne().getId()

        dsl.insertInto(BookAuthor.BOOK_AUTHOR).set(BookAuthor.BOOK_AUTHOR.BOOK_ID, hobbitId).set(BookAuthor.BOOK_AUTHOR.AUTHOR_ID, tolkienId).execute()
        dsl.insertInto(BookAuthor.BOOK_AUTHOR).set(BookAuthor.BOOK_AUTHOR.BOOK_ID, orwell1984Id).set(BookAuthor.BOOK_AUTHOR.AUTHOR_ID, orwellId).execute()

        dsl.insertInto(ReviewTable.REVIEW)
                .set(ReviewTable.REVIEW.BOOK_ID, hobbitId)
                .set(ReviewTable.REVIEW.REVIEWER_NAME, "Alice")
                .set(ReviewTable.REVIEW.RATING, 5)
                .set(ReviewTable.REVIEW.COMMENT, "A masterpiece")
                .execute()
        dsl.insertInto(ReviewTable.REVIEW)
                .set(ReviewTable.REVIEW.BOOK_ID, hobbitId)
                .set(ReviewTable.REVIEW.REVIEWER_NAME, "Bob")
                .set(ReviewTable.REVIEW.RATING, 4)
                .set(ReviewTable.REVIEW.COMMENT, "Great adventure")
                .execute()

        // Wire up GASP schema
        def bookService = new BookService(dsl)
        def registry = new GaspSchemaRegistry(
                new BookService_BooksFetcher(bookService),
                new BookService_BookFetcher(bookService),
                new BookService_BooksByGenreFetcher(bookService)
        )
        graphQL = GraphQL.newGraphQL(registry.buildSchema()).build()
    }

    def setup() {
        queryCount.set(0)
    }


    def "books query returns all books"() {
        when:
        def result = graphQL.execute('{ books { id title genre } }')

        then:
        result.errors.isEmpty()
        result.getData().books.size() == 2
        result.getData().books*.title.containsAll(["The Hobbit", "1984"])
        queryCount.get() == 1
    }

    def "books query with nested authors"() {
        when:
        def result = graphQL.execute('{ books { title authors { name } } }')

        then:
        result.errors.isEmpty()
        def hobbit = result.getData().books.find { it.title == "The Hobbit" }
        hobbit.authors.size() == 1
        hobbit.authors[0].name == "J.R.R. Tolkien"
        queryCount.get() == 1
    }

    def "books query with nested reviews"() {
        when:
        def result = graphQL.execute('{ books { title reviews { reviewerName rating } } }')

        then:
        result.errors.isEmpty()
        def hobbit = result.getData().books.find { it.title == "The Hobbit" }
        hobbit.reviews.size() == 2
        hobbit.reviews*.reviewerName.containsAll(["Alice", "Bob"])
        queryCount.get() == 1
    }

    def "books query with all nested types"() {
        when:
        def result = graphQL.execute('{ books { title authors { name } reviews { reviewerName rating } } }')

        then:
        result.errors.isEmpty()
        result.getData().books.size() == 2
        queryCount.get() == 1
    }

    def "can select only title without nested types"() {
        when:
        def result = graphQL.execute('{ books { title } }')

        then:
        result.errors.isEmpty()
        result.getData().books.size() == 2
        result.getData().books.every { it.containsKey("title") }
        queryCount.get() == 1
    }


    def "book query by id with scalar fields"() {
        when:
        def result = graphQL.execute('{ book(id: 1) { title isbn description } }')

        then:
        result.errors.isEmpty()
        result.getData().book.title == "The Hobbit"
        result.getData().book.isbn == "9780547928227"
        result.getData().book.description == "A hobbit's adventure"
        queryCount.get() == 1
    }

    def "book query with only authors"() {
        when:
        def result = graphQL.execute('{ book(id: 1) { title authors { name } } }')

        then:
        result.errors.isEmpty()
        result.getData().book.title == "The Hobbit"
        result.getData().book.authors.size() == 1
        result.getData().book.authors[0].name == "J.R.R. Tolkien"
        queryCount.get() == 1
    }

    def "book query with only reviews"() {
        when:
        def result = graphQL.execute('{ book(id: 1) { title reviews { reviewerName rating comment } } }')

        then:
        result.errors.isEmpty()
        result.getData().book.reviews.size() == 2
        result.getData().book.reviews*.reviewerName.containsAll(["Alice", "Bob"])
        result.getData().book.reviews.find { it.reviewerName == "Alice" }.rating == 5
        result.getData().book.reviews.find { it.reviewerName == "Alice" }.comment == "A masterpiece"
        queryCount.get() == 1
    }

    def "book query with all nested types"() {
        when:
        def result = graphQL.execute('{ book(id: 1) { title authors { name } reviews { rating } } }')

        then:
        result.errors.isEmpty()
        result.getData().book.title == "The Hobbit"
        result.getData().book.authors.size() == 1
        result.getData().book.reviews.size() == 2
        queryCount.get() == 1
    }

    def "book query with all fields"() {
        when:
        def result = graphQL.execute('{ book(id: 1) { id title isbn description genre authors { id name } reviews { id reviewerName rating comment } } }')

        then:
        result.errors.isEmpty()
        result.getData().book.id == "1"  // ID type serializes as String
        result.getData().book.title == "The Hobbit"
        result.getData().book.isbn == "9780547928227"
        result.getData().book.description == "A hobbit's adventure"
        result.getData().book.genre == "FANTASY"
        result.getData().book.authors[0].id != null
        result.getData().book.authors[0].name == "J.R.R. Tolkien"
        result.getData().book.reviews.size() == 2
        queryCount.get() == 1
    }

    def "book with no reviews returns empty list"() {
        when:
        def result = graphQL.execute('{ book(id: 2) { title reviews { rating } } }')

        then:
        result.errors.isEmpty()
        result.getData().book.title == "1984"
        result.getData().book.reviews.size() == 0
        queryCount.get() == 1
    }

    def "book with nullable isbn returns null"() {
        when:
        def result = graphQL.execute('{ book(id: 2) { title isbn } }')

        then:
        result.errors.isEmpty()
        result.getData().book.title == "1984"
        result.getData().book.isbn == null
        queryCount.get() == 1
    }

    def "book query returns null for missing id"() {
        when:
        def result = graphQL.execute('{ book(id: 999) { title } }')

        then:
        result.errors.isEmpty()
        result.getData().book == null
        queryCount.get() == 1
    }


    def "booksByGenre filters correctly"() {
        when:
        def result = graphQL.execute('{ booksByGenre(genre: "FANTASY") { title genre } }')

        then:
        result.errors.isEmpty()
        result.getData().booksByGenre.size() == 1
        result.getData().booksByGenre[0].title == "The Hobbit"
        result.getData().booksByGenre[0].genre == "FANTASY"
        queryCount.get() == 1
    }

    def "booksByGenre with nested authors"() {
        when:
        def result = graphQL.execute('{ booksByGenre(genre: "FANTASY") { title authors { name } } }')

        then:
        result.errors.isEmpty()
        result.getData().booksByGenre[0].authors.size() == 1
        result.getData().booksByGenre[0].authors[0].name == "J.R.R. Tolkien"
        queryCount.get() == 1
    }

    def "booksByGenre with nested reviews"() {
        when:
        def result = graphQL.execute('{ booksByGenre(genre: "FANTASY") { title reviews { rating } } }')

        then:
        result.errors.isEmpty()
        result.getData().booksByGenre[0].reviews.size() == 2
        queryCount.get() == 1
    }

    def "booksByGenre returns empty for no matches"() {
        when:
        def result = graphQL.execute('{ booksByGenre(genre: "MYSTERY") { title } }')

        then:
        result.errors.isEmpty()
        result.getData().booksByGenre.size() == 0
        queryCount.get() == 1
    }


    def "invalid field returns error"() {
        when:
        def result = graphQL.execute('{ books { nonExistent } }')

        then:
        !result.errors.isEmpty()
    }
}
