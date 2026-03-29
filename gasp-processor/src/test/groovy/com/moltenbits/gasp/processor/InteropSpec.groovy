package com.moltenbits.gasp.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import javax.tools.StandardLocation

import static com.google.testing.compile.Compilation.Status.SUCCESS

class InteropSpec extends Specification {

    // --- JSpecify ---

    def "@NonNull from JSpecify produces non-null type in SDL"() {
        given:
        def source = JavaFileObjects.forSourceString("test.JSpecifyService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLQuery;
            import org.jspecify.annotations.NonNull;

            @GraphQLApi
            public class JSpecifyService {
                @NonNull
                @GraphQLQuery
                public String greeting(@GraphQLArgument(name = "name") @NonNull String name) {
                    return "Hello, " + name;
                }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(source)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('greeting(name: String!): String!')
    }

    def "@Nullable from JSpecify produces nullable type in SDL"() {
        given:
        def source = JavaFileObjects.forSourceString("test.NullableService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLQuery;
            import org.jspecify.annotations.Nullable;

            @GraphQLApi
            public class NullableService {
                @GraphQLQuery
                public @Nullable String find(@GraphQLArgument(name = "id") @Nullable String id) {
                    return null;
                }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(source)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('find(id: String): String')
        !sdl.contains('find(id: String!)')
        !sdl.contains('): String!')
    }

    def "@GraphQLNonNull takes precedence over @Nullable"() {
        given:
        def source = JavaFileObjects.forSourceString("test.MixedNullService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLNonNull;
            import com.moltenbits.gasp.annotation.GraphQLQuery;
            import org.jspecify.annotations.Nullable;

            @GraphQLApi
            public class MixedNullService {
                @GraphQLQuery
                @GraphQLNonNull
                public @Nullable String alwaysNonNull() {
                    return "ok";
                }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(source)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('alwaysNonNull: String!')
    }

    // --- JPA ---

    def "@Entity from JPA is treated as @GraphQLType for return type resolution"() {
        given:
        def entity = JavaFileObjects.forSourceString("test.Book", '''
            package test;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;

            @Entity
            public class Book {
                @Id
                private Long id;
                private String title;

                public Long getId() { return id; }
                public String getTitle() { return title; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.BookService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class BookService {
                @GraphQLQuery
                public Book book(@GraphQLArgument(name = "id") String id) {
                    return null;
                }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(entity, service)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('book(id: String): Book')
    }

    // --- Micronaut Data ---

    def "@MappedEntity from Micronaut Data is treated as @GraphQLType for return type resolution"() {
        given:
        def entity = JavaFileObjects.forSourceString("test.Author", '''
            package test;
            import io.micronaut.data.annotation.MappedEntity;
            import io.micronaut.data.annotation.Id;

            @MappedEntity
            public class Author {
                @Id
                private Long id;
                private String name;

                public Long getId() { return id; }
                public String getName() { return name; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.AuthorService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class AuthorService {
                @GraphQLQuery
                public Author author(@GraphQLArgument(name = "id") String id) {
                    return null;
                }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(entity, service)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('author(id: String): Author')
    }
}
