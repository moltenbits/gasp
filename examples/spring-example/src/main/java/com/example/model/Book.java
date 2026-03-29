package com.example.model;

import com.moltenbits.gasp.annotation.GraphQLType;

@GraphQLType
public class Book {
    private final Long id;
    private final String title;
    private final Author author;

    public Book(Long id, String title, Author author) {
        this.id = id;
        this.title = title;
        this.author = author;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Author getAuthor() { return author; }
}
