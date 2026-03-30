package com.example.service;

import com.example.model.Author;
import com.example.model.Book;
import com.example.model.BookInput;
import com.example.model.Genre;
import com.moltenbits.gasp.annotation.GraphQLApi;
import com.moltenbits.gasp.annotation.GraphQLArgument;
import com.moltenbits.gasp.annotation.GraphQLMutation;
import com.moltenbits.gasp.annotation.GraphQLQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@GraphQLApi
@Service
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

    @GraphQLQuery
    public List<Book> booksByGenre(@GraphQLArgument(name = "genre") Genre genre) {
        return books.stream()
                .filter(b -> b.getGenre() == genre)
                .toList();
    }

    @GraphQLMutation
    public Book createBook(@GraphQLArgument(name = "input") BookInput input) {
        var author = new Author(idCounter.getAndIncrement(), input.getAuthorName());
        var genre = input.getGenre() != null ? input.getGenre() : Genre.FICTION;
        var book = new Book(idCounter.getAndIncrement(), input.getTitle(), "", author, genre);
        books.add(book);
        return book;
    }
}
