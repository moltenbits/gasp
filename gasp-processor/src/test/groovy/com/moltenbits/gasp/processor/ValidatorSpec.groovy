package com.moltenbits.gasp.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import static com.google.testing.compile.Compilation.Status.FAILURE
import static com.google.testing.compile.Compilation.Status.SUCCESS

class ValidatorSpec extends Specification {

    def "void return type on @GraphQLQuery produces compile error"() {
        given:
        def source = JavaFileObjects.forSourceString("test.BadService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class BadService {
                @GraphQLQuery
                public void doNothing() {}
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(source)

        then:
        result.status() == FAILURE
        result.errors().any { it.getMessage(null).contains("void or unmappable return type") }
    }

    def "duplicate query names produce compile error"() {
        given:
        def source = JavaFileObjects.forSourceString("test.DuplicateService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class DuplicateService {
                @GraphQLQuery(name = "hello")
                public String hello1() { return "a"; }

                @GraphQLQuery(name = "hello")
                public String hello2() { return "b"; }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(source)

        then:
        result.status() == FAILURE
        result.errors().any { it.getMessage(null).contains("Duplicate") }
    }

    def "valid service compiles successfully"() {
        given:
        def source = JavaFileObjects.forSourceString("test.ValidService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class ValidService {
                @GraphQLQuery
                public String hello() { return "world"; }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(source)

        then:
        result.status() == SUCCESS
    }
}
