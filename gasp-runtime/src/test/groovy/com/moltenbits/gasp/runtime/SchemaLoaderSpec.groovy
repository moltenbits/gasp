package com.moltenbits.gasp.runtime

import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class SchemaLoaderSpec extends Specification {

    def "loads schema from classpath SDL resource"() {
        when:
        def schema = SchemaLoader.load(
                [hello: { env -> "world" } as DataFetcher],
                [setMessage: { env -> env.getArgument("msg") } as DataFetcher],
                [:]
        )

        then:
        schema instanceof GraphQLSchema
    }

    def "schema contains Query type with expected fields"() {
        when:
        def schema = SchemaLoader.load(
                [hello: { env -> "world" } as DataFetcher, today: { env -> null } as DataFetcher],
                [:],
                [:]
        )

        then:
        schema.queryType != null
        schema.queryType.getFieldDefinition("hello") != null
        schema.queryType.getFieldDefinition("today") != null
    }

    def "schema contains Mutation type with expected fields"() {
        when:
        def schema = SchemaLoader.load(
                [hello: { env -> "world" } as DataFetcher],
                [setMessage: { env -> "ok" } as DataFetcher],
                [:]
        )

        then:
        schema.mutationType != null
        schema.mutationType.getFieldDefinition("setMessage") != null
    }

    def "schema registers Date custom scalar"() {
        when:
        def schema = SchemaLoader.load(
                [hello: { env -> "world" } as DataFetcher],
                [:],
                [:]
        )
        def dateType = schema.getType("Date")

        then:
        dateType != null
        dateType.name == "Date"
    }

    def "query fetcher is wired and executable"() {
        given:
        def schema = SchemaLoader.load(
                [hello: { env -> "world" } as DataFetcher],
                [:],
                [:]
        )
        def graphQL = graphql.GraphQL.newGraphQL(schema).build()

        when:
        def result = graphQL.execute('{ hello }')

        then:
        result.errors.isEmpty()
        result.getData() == [hello: "world"]
    }

    def "mutation fetcher is wired and executable"() {
        given:
        def schema = SchemaLoader.load(
                [hello: { env -> "world" } as DataFetcher],
                [setMessage: { env -> env.getArgument("msg") } as DataFetcher],
                [:]
        )
        def graphQL = graphql.GraphQL.newGraphQL(schema).build()

        when:
        def result = graphQL.execute('mutation { setMessage(msg: "hi") }')

        then:
        result.errors.isEmpty()
        result.getData() == [setMessage: "hi"]
    }
}
