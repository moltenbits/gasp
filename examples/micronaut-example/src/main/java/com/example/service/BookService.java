package com.example.service;

import com.example.model.Author;
import com.example.model.Book;
import com.moltenbits.gasp.annotation.GraphQLApi;
import com.moltenbits.gasp.annotation.GraphQLArgument;
import com.moltenbits.gasp.annotation.GraphQLMutation;
import com.moltenbits.gasp.annotation.GraphQLQuery;
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
        books.add(new Book(idCounter.getAndIncrement(), "The Hobbit", tolkien));
        books.add(new Book(idCounter.getAndIncrement(), "1984", orwell));
        books.add(new Book(idCounter.getAndIncrement(), "The Lord of the Rings", tolkien));
    }

    @GraphQLQuery
    public Book book(@GraphQLArgument(name = "id") Long id) {
        return books.stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @GraphQLQuery
    public List<Book> books() {
        return List.copyOf(books);
    }

    @GraphQLMutation
    public Book createBook(
            @GraphQLArgument(name = "title") String title,
            @GraphQLArgument(name = "authorName") String authorName
    ) {
        var author = new Author(idCounter.getAndIncrement(), authorName);
        var book = new Book(idCounter.getAndIncrement(), title, author);
        books.add(book);
        return book;
    }
}
