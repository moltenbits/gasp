package com.example.model;

import com.moltenbits.gasp.annotation.GraphQLType;
import org.jspecify.annotations.NonNull;

@GraphQLType
public class Author {
    private final Long id;
    private final @NonNull String name;

    public Author(Long id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public @NonNull String getName() { return name; }
}
