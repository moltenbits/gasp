package com.moltenbits.gasp.jooq

import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class GaspJooqGeneratorSpec extends Specification {

    @Shared Path outputDir
    @Shared Map<String, String> tableSources = [:]
    @Shared Map<String, String> schemaSources = [:]

    def setupSpec() {
        def dbUrl = "jdbc:h2:mem:generator_test;DB_CLOSE_DELAY=-1"

        Flyway.configure()
                .dataSource(dbUrl, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate()

        outputDir = Files.createTempDirectory("gasp-jooq-gen-test")

        def config = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.h2.Driver")
                        .withUrl(dbUrl)
                        .withUser("sa")
                        .withPassword(""))
                .withGenerator(new Generator()
                        .withName("com.moltenbits.gasp.jooq.GaspJooqGenerator")
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.h2.H2Database")
                                .withInputSchema("PUBLIC")
                                .withExcludes("flyway_schema_history"))
                        .withGenerate(new Generate()
                                .withDeprecated(false)
                                .withRecords(true)
                                .withPojos(true)
                                .withFluentSetters(true))
                        .withTarget(new Target()
                                .withPackageName("test.jooq")
                                .withDirectory(outputDir.toString())))

        GenerationTool.generate(config)

        // Read generated Table class sources
        Files.walk(outputDir.resolve("test/jooq/tables")).forEach { path ->
            if (path.toString().endsWith(".java") && !path.toString().contains("/pojos/") && !path.toString().contains("/records/")) {
                def name = path.fileName.toString().replace(".java", "")
                tableSources[name] = Files.readString(path)
            }
        }

        // Read generated schema descriptor sources
        def schemaDir = outputDir.resolve("test/jooq/schema")
        if (Files.exists(schemaDir)) {
            Files.walk(schemaDir).forEach { path ->
                if (path.toString().endsWith(".java")) {
                    def name = path.fileName.toString().replace(".java", "")
                    schemaSources[name] = Files.readString(path)
                }
            }
        }
    }

    def cleanupSpec() {
        outputDir?.toFile()?.deleteDir()
    }


    def "Table classes have no GASP annotations"() {
        expect:
        !tableSources["Book"].contains("com.moltenbits.gasp.annotation")
        !tableSources["Author"].contains("com.moltenbits.gasp.annotation")
        !tableSources["Review"].contains("com.moltenbits.gasp.annotation")
    }

    def "join table has no schema descriptor"() {
        expect:
        !schemaSources.containsKey("BookAuthorGraphQLSchema")
    }


    def "schema descriptors are generated for non-join tables"() {
        expect:
        schemaSources.containsKey("BookGraphQLSchema")
        schemaSources.containsKey("AuthorGraphQLSchema")
        schemaSources.containsKey("ReviewGraphQLSchema")
    }

    def "Book descriptor has @GraphQLType with explicitFieldsOnly"() {
        expect:
        schemaSources["BookGraphQLSchema"].contains("@com.moltenbits.gasp.annotation.GraphQLType(explicitFieldsOnly = true)")
    }

    def "Book descriptor has @GraphQLId on id accessor"() {
        expect:
        schemaSources["BookGraphQLSchema"].contains("@com.moltenbits.gasp.annotation.GraphQLId")
    }

    def "Book descriptor has @GraphQLNonNull on non-nullable columns"() {
        expect:
        schemaSources["BookGraphQLSchema"].contains("@com.moltenbits.gasp.annotation.GraphQLNonNull")
    }

    def "Book descriptor has @GraphQLField accessors for columns"() {
        when:
        def source = schemaSources["BookGraphQLSchema"]

        then:
        source.contains("@com.moltenbits.gasp.annotation.GraphQLField")
        source.contains("title()")
        source.contains("genre()")
    }

    def "Book descriptor has reviews one-to-many relation"() {
        when:
        def source = schemaSources["BookGraphQLSchema"]

        then:
        source.contains("ReviewGraphQLSchema.class, list = true")
        source.contains("reviews()")
    }

    def "Book descriptor has authors many-to-many relation"() {
        when:
        def source = schemaSources["BookGraphQLSchema"]

        then:
        source.contains("AuthorGraphQLSchema.class, list = true")
        source.contains("authors()")
    }

    def "Author descriptor has books many-to-many relation"() {
        when:
        def source = schemaSources["AuthorGraphQLSchema"]

        then:
        source.contains("BookGraphQLSchema.class, list = true")
        source.contains("books()")
    }

    def "Review descriptor has book many-to-one relation"() {
        when:
        def source = schemaSources["ReviewGraphQLSchema"]

        then:
        source.contains("BookGraphQLSchema.class)")
        source.contains("book()")
    }

    def "BookAuthor join table has no GASP annotations"() {
        expect:
        !tableSources["BookAuthor"].contains("com.moltenbits.gasp.annotation")
    }
}
