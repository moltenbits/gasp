package com.example.model;

import com.moltenbits.gasp.annotation.GraphQLType;

@GraphQLType
public class Author {
    private final Long id;
    private final String name;

    public Author(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
