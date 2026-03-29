package com.moltenbits.gasp.spring;

import com.moltenbits.gasp.runtime.SchemaProvider;
import graphql.schema.GraphQLSchema;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.graphql.execution.GraphQlSource;

/**
 * Spring Boot auto-configuration that wires the generated schema into Spring GraphQL.
 * Provides a GraphQlSource bean from the GASP SchemaProvider, overriding Spring Boot's
 * default SDL-file-based schema loading. Scans the generated package for DataFetcher
 * and registry beans.
 */
@AutoConfiguration
@ConditionalOnClass(GraphQlSource.class)
@ComponentScan("com.moltenbits.gasp.generated")
public class GaspGraphQlAutoConfiguration {

    @Bean
    public GraphQlSource graphQlSource(SchemaProvider schemaProvider) {
        GraphQLSchema schema = schemaProvider.buildSchema();
        return GraphQlSource.builder(schema).build();
    }
}
