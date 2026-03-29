package com.moltenbits.gasp.micronaut;

import com.moltenbits.gasp.runtime.SchemaProvider;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut factory that wires the generated schema into a GraphQL bean.
 * Micronaut's graphql module provides the /graphql HTTP endpoint automatically
 * when a GraphQL bean is present.
 */
@Factory
public class GaspGraphQLFactory {

    @Singleton
    public GraphQLSchema graphQLSchema(SchemaProvider schemaProvider) {
        return schemaProvider.buildSchema();
    }

    @Singleton
    public GraphQL graphQL(GraphQLSchema schema) {
        return GraphQL.newGraphQL(schema).build();
    }
}
