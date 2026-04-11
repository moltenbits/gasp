package com.example.service;

import com.example.model.Author;
import com.example.model.Book;
import com.example.model.BookInput;
import com.example.model.Genre;
import com.moltenbits.gasp.annotation.GraphQLApi;
import com.moltenbits.gasp.annotation.GraphQLMutation;
import com.moltenbits.gasp.annotation.GraphQLQuery;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@GraphQLApi
@Singleton
public class BookService {

    private final List<Book> books = new ArrayList<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    public BookService() {
        var tolkien = new Author(1L, "J.R.R. Tolkien");
        var orwell = new Author(2L, "George Orwell");
        books.add(new Book(idCounter.getAndIncrement(), "The Hobbit", "A hobbit's adventure", tolkien, Genre.FANTASY));
        books.add(new Book(idCounter.getAndIncrement(), "1984", "A dystopian novel", orwell, Genre.FICTION));
        books.add(new Book(idCounter.getAndIncrement(), "The Lord of the Rings", "An epic fantasy", tolkien, Genre.FANTASY));
    }

    @GraphQLQuery
    public Book book(Long id) {
        return books.stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @GraphQLQuery
    public List<Book> books() {
        return List.copyOf(books);
    }

    @GraphQLQuery
    public List<Book> booksByGenre(Genre genre) {
        return books.stream()
                .filter(b -> b.getGenre() == genre)
                .toList();
    }

    @GraphQLQuery
    public String debug(DataFetchingEnvironment env) {
        var fields = env.getSelectionSet().getImmediateFields().stream()
                .map(f -> f.getName())
                .toList();
        return "Requested fields: " + fields;
    }

    @GraphQLMutation
    public Book createBook(BookInput input) {
        var author = new Author(idCounter.getAndIncrement(), input.getAuthorName());
        var genre = input.getGenre() != null ? input.getGenre() : Genre.FICTION;
        var book = new Book(idCounter.getAndIncrement(), input.getTitle(), "", author, genre);
        books.add(book);
        return book;
    }
}
