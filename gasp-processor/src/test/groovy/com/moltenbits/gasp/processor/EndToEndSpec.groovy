package com.moltenbits.gasp.processor

import com.google.testing.compile.Compilation
import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import spock.lang.Specification

import javax.tools.StandardLocation

import static com.google.testing.compile.Compilation.Status.SUCCESS

class EndToEndSpec extends Specification {

    static final String SIMPLE_SERVICE = '''
        package test;

        import com.moltenbits.gasp.annotation.GraphQLApi;
        import com.moltenbits.gasp.annotation.GraphQLArgument;
        import com.moltenbits.gasp.annotation.GraphQLMutation;
        import com.moltenbits.gasp.annotation.GraphQLQuery;

        @GraphQLApi
        public class SimpleService {

            @GraphQLQuery
            public String hello() {
                return "world";
            }

            @GraphQLQuery
            public String greet(@GraphQLArgument(name = "name") String name) {
                return "Hello, " + name + "!";
            }

            @GraphQLMutation
            public String setMessage(@GraphQLArgument(name = "msg") String msg) {
                return msg;
            }
        }
    '''

    Compilation compilation

    def setup() {
        compilation = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(JavaFileObjects.forSourceString("test.SimpleService", SIMPLE_SERVICE))
    }

    def "compilation succeeds"() {
        expect:
        compilation.status() == SUCCESS
    }

    def "generates schema.graphqls resource"() {
        when:
        def sdlFile = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")

        then:
        sdlFile.isPresent()

        when:
        def sdl = sdlFile.get().getCharContent(false).toString()

        then:
        sdl.contains("type Query")
        sdl.contains("hello: String")
        sdl.contains('greet(name: String): String')
        sdl.contains("type Mutation")
        sdl.contains('setMessage(msg: String): String')
    }

    def "generates DataFetcher for each operation"() {
        expect:
        compilation.generatedSourceFile("com.moltenbits.gasp.generated.SimpleService_HelloFetcher").isPresent()
        compilation.generatedSourceFile("com.moltenbits.gasp.generated.SimpleService_GreetFetcher").isPresent()
        compilation.generatedSourceFile("com.moltenbits.gasp.generated.SimpleService_SetMessageFetcher").isPresent()
    }

    def "generated DataFetcher source implements DataFetcher"() {
        when:
        def source = compilation.generatedSourceFile("com.moltenbits.gasp.generated.SimpleService_HelloFetcher")
                .get().getCharContent(false).toString()

        then:
        source.contains("implements DataFetcher<Object>")
        source.contains("private final SimpleService service")
        source.contains("return service.hello()")
    }

    def "generated DataFetcher extracts arguments"() {
        when:
        def source = compilation.generatedSourceFile("com.moltenbits.gasp.generated.SimpleService_GreetFetcher")
                .get().getCharContent(false).toString()

        then:
        source.contains('env.getArgument("name")')
        source.contains("return service.greet(name)")
    }

    def "generates GaspSchemaRegistry"() {
        when:
        def registryOpt = compilation.generatedSourceFile("com.moltenbits.gasp.generated.GaspSchemaRegistry")

        then:
        registryOpt.isPresent()

        when:
        def source = registryOpt.get().getCharContent(false).toString()

        then:
        source.contains("SimpleService_HelloFetcher")
        source.contains("SimpleService_GreetFetcher")
        source.contains("SimpleService_SetMessageFetcher")
        source.contains('queryFetchers.put("hello"')
        source.contains('queryFetchers.put("greet"')
        source.contains('mutationFetchers.put("setMessage"')
        source.contains("buildSchema()")
    }

    def "DataFetchingEnvironment parameter is passed through to service method"() {
        given:
        def source = JavaFileObjects.forSourceString("test.EnvService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;
            import graphql.schema.DataFetchingEnvironment;

            @GraphQLApi
            public class EnvService {
                @GraphQLQuery
                public String custom(DataFetchingEnvironment env) {
                    return env.getField().getName();
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
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.EnvService_CustomFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains("return service.custom(env)")
    }

    def "DataFetchingEnvironment is excluded from SDL arguments"() {
        given:
        def source = JavaFileObjects.forSourceString("test.MixedService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLQuery;
            import graphql.schema.DataFetchingEnvironment;

            @GraphQLApi
            public class MixedService {
                @GraphQLQuery
                public String search(@GraphQLArgument(name = "query") String query, DataFetchingEnvironment env) {
                    return query;
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
        sdl.contains('search(query: String): String')
        !sdl.contains('DataFetchingEnvironment')
        !sdl.contains('env')

        when:
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.MixedService_SearchFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains("return service.search(query, env)")
    }

    def "SDL is valid GraphQL"() {
        when:
        def sdl = compilation.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()
        def parser = new graphql.schema.idl.SchemaParser()
        def registry = parser.parse(sdl)

        then:
        noExceptionThrown()
        registry.getType("Query").isPresent()
        registry.getType("Mutation").isPresent()
    }
}
