package com.example.schema;

import com.example.jooq.schema.BookGraphQLSchema;
import com.moltenbits.gasp.annotation.GraphQLIgnore;
import com.moltenbits.gasp.annotation.GraphQLType;

import java.time.LocalDateTime;

/**
 * User customization of the Book GraphQL type.
 * Extends the generated schema descriptor to:
 * - Hide internal timestamps from the API
 * - Hide the raw FK columns (prequelId, sequelId) since the relations are exposed
 */
@GraphQLType(name = "Book", explicitFieldsOnly = true)
public class BookSchema extends BookGraphQLSchema {

    @GraphQLIgnore
    @Override
    public LocalDateTime createdAt() { return null; }

    @GraphQLIgnore
    @Override
    public LocalDateTime updatedAt() { return null; }

    @GraphQLIgnore
    @Override
    public Long prequelId() { return null; }

    @GraphQLIgnore
    @Override
    public Long sequelId() { return null; }
}
