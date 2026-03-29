package com.moltenbits.gasp.runtime;

import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads the generated SDL and wires DataFetchers to produce an executable GraphQLSchema.
 */
public final class SchemaLoader {

    private static final String SDL_RESOURCE = "META-INF/gasp/schema.graphqls";

    private SchemaLoader() {}

    public static GraphQLSchema load(
            Map<String, DataFetcher<?>> queryFetchers,
            Map<String, DataFetcher<?>> mutationFetchers,
            Map<String, Map<String, DataFetcher<?>>> typeFetchers
    ) {
        String sdl = loadSdl();
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);

        RuntimeWiring.Builder wiringBuilder = RuntimeWiring.newRuntimeWiring();

        // Register custom scalars
        wiringBuilder.scalar(ScalarDefinitions.DATE);
        wiringBuilder.scalar(ScalarDefinitions.DATE_TIME);

        // Wire query fetchers
        if (!queryFetchers.isEmpty()) {
            TypeRuntimeWiring.Builder queryWiring = TypeRuntimeWiring.newTypeWiring("Query");
            queryFetchers.forEach(queryWiring::dataFetcher);
            wiringBuilder.type(queryWiring);
        }

        // Wire mutation fetchers
        if (!mutationFetchers.isEmpty()) {
            TypeRuntimeWiring.Builder mutationWiring = TypeRuntimeWiring.newTypeWiring("Mutation");
            mutationFetchers.forEach(mutationWiring::dataFetcher);
            wiringBuilder.type(mutationWiring);
        }

        // Wire type-level fetchers (for relations)
        for (Map.Entry<String, Map<String, DataFetcher<?>>> entry : typeFetchers.entrySet()) {
            TypeRuntimeWiring.Builder typeWiring = TypeRuntimeWiring.newTypeWiring(entry.getKey());
            entry.getValue().forEach(typeWiring::dataFetcher);
            wiringBuilder.type(typeWiring);
        }

        return new SchemaGenerator().makeExecutableSchema(registry, wiringBuilder.build());
    }

    private static String loadSdl() {
        try (InputStream is = SchemaLoader.class.getClassLoader().getResourceAsStream(SDL_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException(
                        "GraphQL schema resource not found: " + SDL_RESOURCE
                                + ". Did the GASP annotation processor run during compilation?");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read GraphQL schema resource", e);
        }
    }
}
