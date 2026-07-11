package com.moltenbits.gasp.jooq

import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultExecuteListener
import org.jooq.impl.DefaultExecuteListenerProvider
import spock.lang.Shared
import spock.lang.Specification

import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

import static org.jooq.impl.DSL.field
import static org.jooq.impl.DSL.table

class JooqComposableQuerySpec extends Specification {

    @Shared DSLContext dsl
    @Shared AtomicInteger queryCount = new AtomicInteger(0)

    def setupSpec() {
        def conn = DriverManager.getConnection("jdbc:h2:mem:composable_test;DB_CLOSE_DELAY=-1", "sa", "")
        Flyway.configure()
                .dataSource("jdbc:h2:mem:composable_test;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate()

        def counter = queryCount
        def config = new DefaultConfiguration()
                .set(conn)
                .set(SQLDialect.H2)
                .set(new DefaultExecuteListenerProvider(new DefaultExecuteListener() {
                    @Override
                    void executeStart(org.jooq.ExecuteContext ctx) {
                        counter.incrementAndGet()
                    }
                }))
        dsl = DSL.using(config)

        // Seed data
        dsl.insertInto(table("author")).set(field("name"), "Tolkien").execute()
        dsl.insertInto(table("author")).set(field("name"), "Orwell").execute()
        dsl.insertInto(table("book")).set(field("title"), "The Hobbit").set(field("genre"), "FANTASY").execute()
        dsl.insertInto(table("book")).set(field("title"), "1984").set(field("genre"), "FICTION").execute()
        dsl.insertInto(table("book_author")).set(field("book_id"), 1L).set(field("author_id"), 1L).execute()
        dsl.insertInto(table("book_author")).set(field("book_id"), 2L).set(field("author_id"), 2L).execute()
    }

    def setup() {
        queryCount.set(0)
    }


    def "listOf creates a list query"() {
        when:
        def query = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))

        then:
        query.isList()
    }

    def "oneOf creates a single-result query"() {
        when:
        def query = JooqComposableQuery.oneOf(dsl, table("book"), field("id"), field("title"))

        then:
        !query.isList()
    }


    def "where returns a new instance"() {
        given:
        def original = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))

        when:
        def filtered = original.where(field("genre").eq("FANTASY"))

        then:
        !original.is(filtered)
        original.conditions().isEmpty()
        filtered.conditions().size() == 1
    }

    def "withMultiset returns a new instance"() {
        given:
        def original = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))

        when:
        def withAuthors = original.withMultiset("authors", DSL.select(field("name")).from(table("author")))

        then:
        !original.is(withAuthors)
        original.multisets().isEmpty()
        withAuthors.multisets().size() == 1
    }

    def "chained where conditions accumulate"() {
        given:
        def query = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))
                .where(field("genre").eq("FANTASY"))
                .where(field("title").eq("The Hobbit"))

        expect:
        query.conditions().size() == 2
    }


    def "fetchAll returns all rows"() {
        when:
        def results = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))
                .fetchAll()

        then:
        results.size() == 2
        queryCount.get() == 1
    }

    def "fetchAll with where condition filters"() {
        when:
        def results = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"), field("genre"))
                .where(field("genre").eq("FANTASY"))
                .fetchAll()

        then:
        results.size() == 1
        results[0].get("title") == "The Hobbit"
        queryCount.get() == 1
    }

    def "fetchAll with multiple where conditions"() {
        when:
        def results = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"), field("genre"))
                .where(field("genre").eq("FANTASY"))
                .where(field("title").eq("The Hobbit"))
                .fetchAll()

        then:
        results.size() == 1
        queryCount.get() == 1
    }


    def "fetchOne returns single row"() {
        when:
        def result = JooqComposableQuery.oneOf(dsl, table("book"), field("id"), field("title"))
                .where(field("id").eq(1L))
                .fetchOne()

        then:
        result != null
        result.get("title") == "The Hobbit"
        queryCount.get() == 1
    }

    def "fetchOne returns null for no match"() {
        when:
        def result = JooqComposableQuery.oneOf(dsl, table("book"), field("id"), field("title"))
                .where(field("id").eq(999L))
                .fetchOne()

        then:
        result == null
        queryCount.get() == 1
    }


    def "withMultiset includes correlated subquery in single query"() {
        when:
        def results = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))
                .withMultiset("authors",
                        DSL.select(field("author.name"))
                                .from(table("author"))
                                .join(table("book_author")).on(field("book_author.author_id").eq(field("author.id")))
                                .where(field("book_author.book_id").eq(field("book.id"))))
                .fetchAll()

        then:
        results.size() == 2
        queryCount.get() == 1
    }

    def "fetchOne with multiset includes associations in single query"() {
        when:
        def result = JooqComposableQuery.oneOf(dsl, table("book"), field("id"), field("title"))
                .where(field("id").eq(1L))
                .withMultiset("authors",
                        DSL.select(field("author.name"))
                                .from(table("author"))
                                .join(table("book_author")).on(field("book_author.author_id").eq(field("author.id")))
                                .where(field("book_author.book_id").eq(field("book.id"))))
                .fetchOne()

        then:
        result != null
        result.get("title") == "The Hobbit"
        queryCount.get() == 1
    }


    def "mapper transforms records"() {
        when:
        def results = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))
                .mapper(r -> r.get("title", String))
                .fetchAll()

        then:
        results.size() == 2
        results.containsAll(["The Hobbit", "1984"])
        queryCount.get() == 1
    }

    def "fetchOne with mapper transforms single record"() {
        when:
        def result = JooqComposableQuery.oneOf(dsl, table("book"), field("id"), field("title"))
                .where(field("id").eq(1L))
                .mapper(r -> r.get("title", String))
                .fetchOne()

        then:
        result == "The Hobbit"
        queryCount.get() == 1
    }

    def "without mapper returns raw Records"() {
        when:
        def results = JooqComposableQuery.listOf(dsl, table("book"), field("id"), field("title"))
                .fetchAll()

        then:
        results.size() == 2
        results[0] instanceof org.jooq.Record
        queryCount.get() == 1
    }
}
