package com.example

import com.example.jooq.tables.Author
import com.example.jooq.tables.Book
import com.example.jooq.tables.BookAuthor
import com.example.jooq.tables.Review
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import spock.lang.Shared
import spock.lang.Specification

import java.sql.DriverManager
import java.time.LocalDateTime

class JooqSpec extends Specification {

    @Shared DSLContext dsl

    def setupSpec() {
        def conn = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
        Flyway.configure()
                .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate()
        dsl = DSL.using(conn, SQLDialect.H2)
    }


    def "can insert and query authors"() {
        when:
        dsl.insertInto(Author.AUTHOR)
                .set(Author.AUTHOR.NAME, "J.R.R. Tolkien")
                .execute()
        def authors = dsl.selectFrom(Author.AUTHOR).fetch()

        then:
        authors.size() == 1
        authors[0].getName() == "J.R.R. Tolkien"
        authors[0].getId() != null
    }

    def "author has created_at timestamp"() {
        when:
        def author = dsl.selectFrom(Author.AUTHOR)
                .where(Author.AUTHOR.NAME.eq("J.R.R. Tolkien"))
                .fetchOne()

        then:
        author.getCreatedAt() != null
        author.getCreatedAt() instanceof LocalDateTime
    }


    def "can insert and query books"() {
        when:
        dsl.insertInto(Book.BOOK)
                .set(Book.BOOK.TITLE, "The Hobbit")
                .set(Book.BOOK.ISBN, "9780547928227")
                .set(Book.BOOK.DESCRIPTION, "A hobbit's adventure")
                .set(Book.BOOK.GENRE, "FANTASY")
                .execute()
        def books = dsl.selectFrom(Book.BOOK).fetch()

        then:
        books.size() == 1
        books[0].getTitle() == "The Hobbit"
        books[0].getIsbn() == "9780547928227"
        books[0].getGenre() == "FANTASY"
    }

    def "book has timestamps"() {
        when:
        def book = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Hobbit"))
                .fetchOne()

        then:
        book.getCreatedAt() != null
        book.getUpdatedAt() != null
        book.getCreatedAt() instanceof LocalDateTime
        book.getUpdatedAt() instanceof LocalDateTime
    }


    def "isbn unique constraint prevents duplicates"() {
        when:
        dsl.insertInto(Book.BOOK)
                .set(Book.BOOK.TITLE, "Duplicate ISBN")
                .set(Book.BOOK.ISBN, "9780547928227")
                .set(Book.BOOK.GENRE, "FICTION")
                .execute()

        then:
        thrown(Exception)
    }

    def "can look up book by isbn"() {
        when:
        def book = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.ISBN.eq("9780547928227"))
                .fetchOne()

        then:
        book != null
        book.getTitle() == "The Hobbit"
    }

    def "isbn is nullable"() {
        when:
        dsl.insertInto(Book.BOOK)
                .set(Book.BOOK.TITLE, "No ISBN Book")
                .set(Book.BOOK.GENRE, "FICTION")
                .execute()
        def book = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("No ISBN Book"))
                .fetchOne()

        then:
        book.getIsbn() == null
    }


    def "can create a prequel/sequel relationship"() {
        given:
        def hobbitId = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Hobbit"))
                .fetchOne().getId()

        when:
        dsl.insertInto(Book.BOOK)
                .set(Book.BOOK.TITLE, "The Lord of the Rings")
                .set(Book.BOOK.GENRE, "FANTASY")
                .set(Book.BOOK.PREQUEL_ID, hobbitId)
                .execute()
        def lotr = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Lord of the Rings"))
                .fetchOne()

        then:
        lotr.getPrequelId() == hobbitId
    }

    def "can self-join to find a book and its prequel"() {
        given:
        def prequel = Book.BOOK.as("prequel")

        when:
        def result = dsl.select(Book.BOOK.TITLE, prequel.TITLE)
                .from(Book.BOOK)
                .leftJoin(prequel).on(Book.BOOK.PREQUEL_ID.eq(prequel.ID))
                .where(Book.BOOK.TITLE.eq("The Lord of the Rings"))
                .fetchOne()

        then:
        result.value1() == "The Lord of the Rings"
        result.value2() == "The Hobbit"
    }

    def "prequel_id and sequel_id are nullable"() {
        when:
        def hobbit = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Hobbit"))
                .fetchOne()

        then:
        hobbit.getPrequelId() == null
        hobbit.getSequelId() == null
    }


    def "can associate multiple authors with a book"() {
        given:
        def tolkienId = dsl.selectFrom(Author.AUTHOR)
                .where(Author.AUTHOR.NAME.eq("J.R.R. Tolkien"))
                .fetchOne().getId()
        def christopherId = dsl.insertInto(Author.AUTHOR)
                .set(Author.AUTHOR.NAME, "Christopher Tolkien")
                .returningResult(Author.AUTHOR.ID)
                .fetchOne().value1()
        def silmarillionId = dsl.insertInto(Book.BOOK)
                .set(Book.BOOK.TITLE, "The Silmarillion")
                .set(Book.BOOK.GENRE, "FANTASY")
                .returningResult(Book.BOOK.ID)
                .fetchOne().value1()

        when:
        dsl.insertInto(BookAuthor.BOOK_AUTHOR)
                .set(BookAuthor.BOOK_AUTHOR.BOOK_ID, silmarillionId)
                .set(BookAuthor.BOOK_AUTHOR.AUTHOR_ID, tolkienId)
                .execute()
        dsl.insertInto(BookAuthor.BOOK_AUTHOR)
                .set(BookAuthor.BOOK_AUTHOR.BOOK_ID, silmarillionId)
                .set(BookAuthor.BOOK_AUTHOR.AUTHOR_ID, christopherId)
                .execute()

        def authors = dsl.select(Author.AUTHOR.NAME)
                .from(Author.AUTHOR)
                .join(BookAuthor.BOOK_AUTHOR).on(BookAuthor.BOOK_AUTHOR.AUTHOR_ID.eq(Author.AUTHOR.ID))
                .where(BookAuthor.BOOK_AUTHOR.BOOK_ID.eq(silmarillionId))
                .fetch()
                .map { it.value1() }

        then:
        authors.size() == 2
        authors.containsAll(["J.R.R. Tolkien", "Christopher Tolkien"])
    }

    def "can find all books by an author via join table"() {
        given:
        def tolkienId = dsl.selectFrom(Author.AUTHOR)
                .where(Author.AUTHOR.NAME.eq("J.R.R. Tolkien"))
                .fetchOne().getId()
        def hobbitId = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Hobbit"))
                .fetchOne().getId()
        dsl.insertInto(BookAuthor.BOOK_AUTHOR)
                .set(BookAuthor.BOOK_AUTHOR.BOOK_ID, hobbitId)
                .set(BookAuthor.BOOK_AUTHOR.AUTHOR_ID, tolkienId)
                .onDuplicateKeyIgnore()
                .execute()

        when:
        def books = dsl.select(Book.BOOK.TITLE)
                .from(Book.BOOK)
                .join(BookAuthor.BOOK_AUTHOR).on(BookAuthor.BOOK_AUTHOR.BOOK_ID.eq(Book.BOOK.ID))
                .where(BookAuthor.BOOK_AUTHOR.AUTHOR_ID.eq(tolkienId))
                .fetch()
                .map { it.value1() }

        then:
        books.containsAll(["The Hobbit", "The Silmarillion"])
    }


    def "can add reviews to a book"() {
        given:
        def hobbitId = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Hobbit"))
                .fetchOne().getId()

        when:
        dsl.insertInto(Review.REVIEW)
                .set(Review.REVIEW.BOOK_ID, hobbitId)
                .set(Review.REVIEW.REVIEWER_NAME, "Alice")
                .set(Review.REVIEW.RATING, 5)
                .set(Review.REVIEW.COMMENT, "A masterpiece")
                .execute()
        dsl.insertInto(Review.REVIEW)
                .set(Review.REVIEW.BOOK_ID, hobbitId)
                .set(Review.REVIEW.REVIEWER_NAME, "Bob")
                .set(Review.REVIEW.RATING, 4)
                .set(Review.REVIEW.COMMENT, "Great adventure")
                .execute()

        def reviews = dsl.selectFrom(Review.REVIEW)
                .where(Review.REVIEW.BOOK_ID.eq(hobbitId))
                .fetch()

        then:
        reviews.size() == 2
        reviews*.getReviewerName().containsAll(["Alice", "Bob"])
        reviews.every { it.getRating() >= 4 }
    }

    def "review has created_at timestamp"() {
        when:
        def review = dsl.selectFrom(Review.REVIEW)
                .where(Review.REVIEW.REVIEWER_NAME.eq("Alice"))
                .fetchOne()

        then:
        review.getCreatedAt() != null
        review.getCreatedAt() instanceof LocalDateTime
    }

    def "can join books with their reviews and compute average rating"() {
        when:
        def result = dsl.select(
                        Book.BOOK.TITLE,
                        DSL.avg(Review.REVIEW.RATING).as("avg_rating"),
                        DSL.count(Review.REVIEW.ID).as("review_count")
                )
                .from(Book.BOOK)
                .leftJoin(Review.REVIEW).on(Review.REVIEW.BOOK_ID.eq(Book.BOOK.ID))
                .groupBy(Book.BOOK.TITLE)
                .orderBy(Book.BOOK.TITLE)
                .fetch()

        then:
        result.size() >= 1
        def hobbit = result.find { it.value1() == "The Hobbit" }
        hobbit != null
        hobbit.value3() == 2
    }

    def "review rating constraint is enforced"() {
        given:
        def hobbitId = dsl.selectFrom(Book.BOOK)
                .where(Book.BOOK.TITLE.eq("The Hobbit"))
                .fetchOne().getId()

        when:
        dsl.insertInto(Review.REVIEW)
                .set(Review.REVIEW.BOOK_ID, hobbitId)
                .set(Review.REVIEW.REVIEWER_NAME, "Evil")
                .set(Review.REVIEW.RATING, 6)
                .execute()

        then:
        thrown(Exception)
    }


    def "can select only specific columns"() {
        when:
        def titles = dsl.select(Book.BOOK.TITLE, Book.BOOK.GENRE)
                .from(Book.BOOK)
                .fetch()

        then:
        titles.size() > 0
        titles.every { it.value1() instanceof String && it.value2() instanceof String }
    }
}
