package com.example.model;

import com.moltenbits.gasp.annotation.GraphQLType;

@GraphQLType
public class Book implements Searchable {
    private final Long id;
    private final String title;
    private final String description;
    private final Author author;
    private final Genre genre;

    public Book(Long id, String title, String description, Author author, Genre genre) {
        this.id = id; this.title = title; this.description = description; this.author = author; this.genre = genre;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Author getAuthor() { return author; }
    public Genre getGenre() { return genre; }
}
