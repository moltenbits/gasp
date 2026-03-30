package com.example

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookServiceSpec extends Specification {

    @Value('${local.server.port}')
    int port

    private Map graphql(String query) {
        def client = RestClient.builder()
                .baseUrl("http://localhost:${port}")
                .build()
        return client.post()
                .uri('/graphql')
                .contentType(MediaType.APPLICATION_JSON)
                .body([query: query])
                .retrieve()
                .body(Map)
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
        def result = graphql('mutation { createBook(title: "Dune", authorName: "Frank Herbert", genre: SCIENCE_FICTION) { id title author { name } genre } }')

        then:
        result.data.createBook.title == "Dune"
        result.data.createBook.author.name == "Frank Herbert"
        result.data.createBook.genre == "SCIENCE_FICTION"
        result.data.createBook.id != null
    }

    def "createBook mutation with enum defaults when genre omitted"() {
        when:
        def result = graphql('mutation { createBook(title: "Sapiens", authorName: "Yuval Harari") { title genre } }')

        then:
        result.data.createBook.title == "Sapiens"
        result.data.createBook.genre == "FICTION"
    }

    def "createBook mutation is visible in subsequent books query"() {
        when:
        graphql('mutation { createBook(title: "Neuromancer", authorName: "William Gibson", genre: SCIENCE_FICTION) { id } }')
        def result = graphql('{ books { title } }')

        then:
        result.data.books*.title.contains("Neuromancer")
    }

    // --- Enum: booksByGenre ---

    def "booksByGenre query filters by enum value"() {
        when:
        def result = graphql('{ booksByGenre(genre: FANTASY) { title genre } }')
        then:
        result.data.booksByGenre.size() == 2
        result.data.booksByGenre*.title.containsAll(["The Hobbit", "The Lord of the Rings"])
        result.data.booksByGenre.every { it.genre == "FANTASY" }
    }

    def "booksByGenre returns empty list for genre with no books"() {
        when:
        def result = graphql('{ booksByGenre(genre: MYSTERY) { title } }')
        then:
        result.data.booksByGenre.size() == 0
    }

    def "book query returns genre field"() {
        when:
        def result = graphql('{ book(id: 1) { title genre } }')
        then:
        result.data.book.genre == "FANTASY"
    }

    // --- Type-level fetcher: recommendations ---

    def "book has recommendations from type-level fetcher"() {
        when:
        def result = graphql('{ book(id: 1) { title recommendations { title author { name } } } }')
        then:
        result.data.book.title == "The Hobbit"
        result.data.book.recommendations.size() == 1
        result.data.book.recommendations[0].title == "The Name of the Wind"
        result.data.book.recommendations[0].author.name == "Patrick Rothfuss"
    }

    def "book with no recommendations returns empty list"() {
        when:
        def result = graphql('{ book(id: 2) { title recommendations { title } } }')
        then:
        result.data.book.title == "1984"
        result.data.book.recommendations.size() == 0
    }

    // --- Interface: Searchable ---

    def "book has description field from Searchable interface"() {
        when:
        def result = graphql('{ book(id: 1) { title description } }')
        then:
        result.data.book.title == "The Hobbit"
        result.data.book.description == "A hobbit's adventure"
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
