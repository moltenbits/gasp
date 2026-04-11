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

    def "argument name is derived from parameter name without @GraphQLArgument"() {
        given:
        def source = JavaFileObjects.forSourceString("test.ImplicitArgService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class ImplicitArgService {
                @GraphQLQuery
                public String find(String keyword, int limit) {
                    return keyword;
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
        sdl.contains('find(keyword: String, limit: Int!): String')

        when:
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.ImplicitArgService_FindFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains('env.getArgument("keyword")')
        fetcher.contains('env.<Number>getArgument("limit")')
    }

    def "@GraphQLArgument overrides argument name in SDL and DataFetcher"() {
        given:
        def source = JavaFileObjects.forSourceString("test.RenamedArgService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLArgument;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class RenamedArgService {
                @GraphQLQuery
                public String lookup(@GraphQLArgument(name = "searchTerm") String query) {
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
        sdl.contains('lookup(searchTerm: String): String')
        !sdl.contains('query:')

        when:
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.RenamedArgService_LookupFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains('env.getArgument("searchTerm")')
        fetcher.contains('return service.lookup(query)')
    }

    // --- @GraphQLType ---

    def "@GraphQLType generates object type SDL from getters"() {
        given:
        def author = JavaFileObjects.forSourceString("test.Author", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLType;

            @GraphQLType
            public class Author {
                private final String name;
                private final int age;
                public Author(String name, int age) { this.name = name; this.age = age; }
                public String getName() { return name; }
                public int getAge() { return age; }
            }
        ''')
        def book = JavaFileObjects.forSourceString("test.Book", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLType;

            @GraphQLType
            public class Book {
                private final String title;
                private final Author author;
                public Book(String title, Author author) { this.title = title; this.author = author; }
                public String getTitle() { return title; }
                public Author getAuthor() { return author; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.BookService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;
            import java.util.List;

            @GraphQLApi
            public class BookService {
                @GraphQLQuery
                public List<Book> books() { return List.of(); }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(author, book, service)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('type Author {')
        sdl.contains('name: String')
        sdl.contains('age: Int!')
        sdl.contains('type Book {')
        sdl.contains('title: String')
        sdl.contains('author: Author')
    }

    // --- @GraphQLEnum ---

    def "@GraphQLEnum generates enum type SDL"() {
        given:
        def genre = JavaFileObjects.forSourceString("test.Genre", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLEnum;

            @GraphQLEnum
            public enum Genre { FICTION, FANTASY, MYSTERY }
        ''')
        def service = JavaFileObjects.forSourceString("test.Svc", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class Svc {
                @GraphQLQuery
                public Genre genre() { return Genre.FICTION; }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(genre, service)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('enum Genre {')
        sdl.contains('FICTION')
        sdl.contains('FANTASY')
        sdl.contains('MYSTERY')
    }

    def "enum argument generates valueOf coercion in DataFetcher"() {
        given:
        def genre = JavaFileObjects.forSourceString("test.Genre", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLEnum;

            @GraphQLEnum
            public enum Genre { FICTION, FANTASY }
        ''')
        def service = JavaFileObjects.forSourceString("test.Svc", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class Svc {
                @GraphQLQuery
                public String byGenre(Genre genre) { return genre.name(); }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(genre, service)

        then:
        result.status() == SUCCESS

        when:
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.Svc_ByGenreFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains('Genre.valueOf(')
        fetcher.contains('env.<String>getArgument("genre")')
    }

    // --- @GraphQLInputType ---

    def "@GraphQLInputType generates input type SDL"() {
        given:
        def input = JavaFileObjects.forSourceString("test.BookInput", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLInputType;

            @GraphQLInputType
            public class BookInput {
                private String title;
                private int pages;
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public int getPages() { return pages; }
                public void setPages(int pages) { this.pages = pages; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.Svc", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLMutation;

            @GraphQLApi
            public class Svc {
                @GraphQLMutation
                public String create(BookInput input) { return "ok"; }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(input, service)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('input BookInput {')
        sdl.contains('title: String')
        sdl.contains('pages: Int!')
        sdl.contains('create(input: BookInput): String')
    }

    def "@GraphQLInputType argument generates converter class"() {
        given:
        def input = JavaFileObjects.forSourceString("test.BookInput", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLInputType;

            @GraphQLInputType
            public class BookInput {
                private String title;
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.Svc", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLMutation;

            @GraphQLApi
            public class Svc {
                @GraphQLMutation
                public String create(BookInput input) { return "ok"; }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(input, service)

        then:
        result.status() == SUCCESS
        result.generatedSourceFile("com.moltenbits.gasp.generated.BookInputConverter").isPresent()

        when:
        def converter = result.generatedSourceFile("com.moltenbits.gasp.generated.BookInputConverter")
                .get().getCharContent(false).toString()

        then:
        converter.contains('public static BookInput fromMap(Map<String, Object> map)')
        converter.contains('obj.setTitle((String)')
        !converter.contains('ObjectMapper')
        !converter.contains('java.lang.reflect')

        when:
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.Svc_CreateFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains('BookInputConverter.fromMap(')
    }

    // --- @GraphQLInterface ---

    def "@GraphQLInterface generates interface SDL and implements clause"() {
        given:
        def iface = JavaFileObjects.forSourceString("test.Searchable", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLInterface;

            @GraphQLInterface
            public interface Searchable {
                String getTitle();
            }
        ''')
        def item = JavaFileObjects.forSourceString("test.Item", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLType;

            @GraphQLType
            public class Item implements Searchable {
                private final String title;
                private final double price;
                public Item(String title, double price) { this.title = title; this.price = price; }
                public String getTitle() { return title; }
                public double getPrice() { return price; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.Svc", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class Svc {
                @GraphQLQuery
                public Item item() { return null; }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(iface, item, service)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('interface Searchable {')
        sdl.contains('type Item implements Searchable {')
        sdl.contains('title: String')
        sdl.contains('price: Float!')
    }

    // --- @GraphQLField(on = ...) type-level fetcher ---

    def "@GraphQLField(on) generates type-level DataFetcher and SDL field"() {
        given:
        def book = JavaFileObjects.forSourceString("test.Book", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLType;

            @GraphQLType
            public class Book {
                private final String title;
                public Book(String title) { this.title = title; }
                public String getTitle() { return title; }
            }
        ''')
        def service = JavaFileObjects.forSourceString("test.BookService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLApi;
            import com.moltenbits.gasp.annotation.GraphQLQuery;

            @GraphQLApi
            public class BookService {
                @GraphQLQuery
                public Book book() { return null; }
            }
        ''')
        def recoService = JavaFileObjects.forSourceString("test.RecoService", '''
            package test;
            import com.moltenbits.gasp.annotation.GraphQLField;
            import java.util.List;

            public class RecoService {
                @GraphQLField(on = Book.class)
                public List<Book> related(Object source) { return List.of(); }
            }
        ''')

        when:
        def result = Compiler.javac()
                .withProcessors(new GaspProcessor())
                .compile(book, service, recoService)

        then:
        result.status() == SUCCESS

        when:
        def sdl = result.generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/gasp/schema.graphqls")
                .get().getCharContent(false).toString()

        then:
        sdl.contains('type Book {')
        sdl.contains('related: [Book]')

        when:
        def fetcher = result.generatedSourceFile("com.moltenbits.gasp.generated.Book_RelatedFetcher")
                .get().getCharContent(false).toString()

        then:
        fetcher.contains('implements DataFetcher<Object>')
        fetcher.contains('env.getSource()')
        fetcher.contains('service.related(source)')

        when:
        def registry = result.generatedSourceFile("com.moltenbits.gasp.generated.GaspSchemaRegistry")
                .get().getCharContent(false).toString()

        then:
        registry.contains('typeFetchers.computeIfAbsent("Book"')
        registry.contains('.put("related"')
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
