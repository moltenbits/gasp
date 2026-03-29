package com.moltenbits.gasp.runtime;

import graphql.schema.GraphQLSchema;

/**
 * Interface for providing an executable GraphQL schema.
 * The generated GaspSchemaRegistry implements this, and framework adapters
 * (gasp-micronaut, gasp-spring) consume it to wire the schema into the
 * framework's GraphQL endpoint.
 */
public interface SchemaProvider {
    GraphQLSchema buildSchema();
}
