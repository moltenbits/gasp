package com.example

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class BookServiceSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    private Map graphql(String query) {
        def response = client.toBlocking().retrieve(
                HttpRequest.POST("/graphql", [query: query]),
                Map
        )
        return response
    }

    // --- Query: books ---

    def "books query returns all books"() {
        when:
        def result = graphql('{ books { id title } }')

        then:
        result.data.books.size() == 3
        result.data.books*.title.containsAll(["The Hobbit", "1984", "The Lord of the Rings"])
    }

    def "books query can request nested author"() {
        when:
        def result = graphql('{ books { title author { name } } }')

        then:
        result.data.books.every { it.author?.name != null }
        result.data.books.find { it.title == "The Hobbit" }.author.name == "J.R.R. Tolkien"
        result.data.books.find { it.title == "1984" }.author.name == "George Orwell"
    }

    def "books query can select only title"() {
        when:
        def result = graphql('{ books { title } }')

        then:
        result.data.books.every { it.containsKey("title") }
        result.data.books.every { !it.containsKey("id") }
        result.data.books.every { !it.containsKey("author") }
    }

    // --- Query: book ---

    def "book query returns a single book by id"() {
        when:
        def result = graphql('{ book(id: 1) { id title } }')

        then:
        result.data.book.id == 1
        result.data.book.title == "The Hobbit"
    }

    def "book query returns null for non-existent id"() {
        when:
        def result = graphql('{ book(id: 999) { id title } }')

        then:
        result.data.book == null
    }

    def "book query with nested author fields"() {
        when:
        def result = graphql('{ book(id: 2) { title author { id name } } }')

        then:
        result.data.book.title == "1984"
        result.data.book.author.id == 2
        result.data.book.author.name == "George Orwell"
    }

    // --- Mutation: createBook ---

    def "createBook mutation adds a book and returns it"() {
        when:
        def result = graphql('mutation { createBook(title: "Dune", authorName: "Frank Herbert") { id title author { name } } }')

        then:
        result.data.createBook.title == "Dune"
        result.data.createBook.author.name == "Frank Herbert"
        result.data.createBook.id != null
    }

    def "createBook mutation is visible in subsequent books query"() {
        when:
        graphql('mutation { createBook(title: "Neuromancer", authorName: "William Gibson") { id } }')
        def result = graphql('{ books { title } }')

        then:
        result.data.books*.title.contains("Neuromancer")
    }

    // --- Schema validation ---

    def "graphql endpoint returns errors for invalid query"() {
        when:
        def result = graphql('{ nonExistentField }')

        then:
        result.errors != null
        result.errors.size() > 0
    }

    def "graphql endpoint returns errors for invalid field on type"() {
        when:
        def result = graphql('{ books { nonExistentField } }')

        then:
        result.errors != null
        result.errors.size() > 0
    }
}
