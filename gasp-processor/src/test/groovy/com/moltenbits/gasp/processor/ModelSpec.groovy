package com.moltenbits.gasp.processor

import com.moltenbits.gasp.annotation.OperationKind
import com.moltenbits.gasp.processor.model.*
import spock.lang.Specification
import spock.lang.Unroll

class ModelSpec extends Specification {

    // --- GraphQLTypeRef sealed variants ---

    @Unroll
    def "GraphQLTypeRef.#variant.simpleName can be constructed"() {
        expect:
        instance != null

        where:
        variant                         | instance
        GraphQLTypeRef.Scalar           | new GraphQLTypeRef.Scalar("String")
        GraphQLTypeRef.ObjectRef        | new GraphQLTypeRef.ObjectRef("User")
        GraphQLTypeRef.EnumRef          | new GraphQLTypeRef.EnumRef("Status")
        GraphQLTypeRef.ListOf           | new GraphQLTypeRef.ListOf(new GraphQLTypeRef.Scalar("Int"))
        GraphQLTypeRef.NonNull          | new GraphQLTypeRef.NonNull(new GraphQLTypeRef.Scalar("ID"))
    }

    def "GraphQLTypeRef supports nested types: NonNull(ListOf(Scalar))"() {
        when:
        def nested = new GraphQLTypeRef.NonNull(
            new GraphQLTypeRef.ListOf(
                new GraphQLTypeRef.Scalar("String")
            )
        )

        then:
        nested instanceof GraphQLTypeRef.NonNull
        nested.inner() instanceof GraphQLTypeRef.ListOf
        (nested.inner() as GraphQLTypeRef.ListOf).inner() instanceof GraphQLTypeRef.Scalar
        ((nested.inner() as GraphQLTypeRef.ListOf).inner() as GraphQLTypeRef.Scalar).name() == "String"
    }

    def "GraphQLTypeRef records support equality"() {
        expect:
        new GraphQLTypeRef.Scalar("String") == new GraphQLTypeRef.Scalar("String")
        new GraphQLTypeRef.ObjectRef("User") == new GraphQLTypeRef.ObjectRef("User")
        new GraphQLTypeRef.EnumRef("Status") == new GraphQLTypeRef.EnumRef("Status")
        new GraphQLTypeRef.ListOf(new GraphQLTypeRef.Scalar("Int")) == new GraphQLTypeRef.ListOf(new GraphQLTypeRef.Scalar("Int"))
        new GraphQLTypeRef.NonNull(new GraphQLTypeRef.Scalar("ID")) == new GraphQLTypeRef.NonNull(new GraphQLTypeRef.Scalar("ID"))
    }

    def "GraphQLTypeRef records support inequality"() {
        expect:
        new GraphQLTypeRef.Scalar("String") != new GraphQLTypeRef.Scalar("Int")
        new GraphQLTypeRef.ObjectRef("User") != new GraphQLTypeRef.ObjectRef("Post")
        new GraphQLTypeRef.EnumRef("Status") != new GraphQLTypeRef.EnumRef("Role")
    }

    // --- SchemaModel ---

    def "SchemaModel can be constructed with empty lists"() {
        when:
        def schema = new SchemaModel([], [], [], [], [], [], [], [])

        then:
        schema.types() == []
        schema.enums() == []
        schema.queries() == []
        schema.mutations() == []
        schema.subscriptions() == []
    }

    // --- OperationModel ---

    def "OperationModel can be constructed with arguments"() {
        given:
        def arg1 = new ArgumentModel("id", "id", "java.lang.Long", new GraphQLTypeRef.NonNull(new GraphQLTypeRef.Scalar("ID")), null)
        def arg2 = new ArgumentModel("name", "name", "java.lang.String", new GraphQLTypeRef.Scalar("String"), "\"default\"")

        when:
        def op = new OperationModel(
            OperationKind.QUERY,
            "findUser",
            "Find a user by ID",
            new GraphQLTypeRef.ObjectRef("User"),
            false,
            "com.example.UserService",
            "findUser",
            [arg1, arg2],
            -1
        )

        then:
        op.kind() == OperationKind.QUERY
        op.graphQLName() == "findUser"
        op.description() == "Find a user by ID"
        op.returnType() == new GraphQLTypeRef.ObjectRef("User")
        op.serviceClass() == "com.example.UserService"
        op.methodName() == "findUser"
        op.arguments().size() == 2
        op.arguments()[0] == arg1
        op.arguments()[1] == arg2
    }

    // --- toString output ---

    @Unroll
    def "#recordType.simpleName toString contains field values"() {
        expect:
        stringValue.contains(expectedSubstring)

        where:
        recordType          | stringValue                                                                                              | expectedSubstring
        GraphQLTypeRef.Scalar   | new GraphQLTypeRef.Scalar("String").toString()                                                       | "String"
        GraphQLTypeRef.ObjectRef| new GraphQLTypeRef.ObjectRef("User").toString()                                                      | "User"
        GraphQLTypeRef.EnumRef  | new GraphQLTypeRef.EnumRef("Status").toString()                                                      | "Status"
        GraphQLTypeRef.ListOf   | new GraphQLTypeRef.ListOf(new GraphQLTypeRef.Scalar("Int")).toString()                                | "Int"
        GraphQLTypeRef.NonNull  | new GraphQLTypeRef.NonNull(new GraphQLTypeRef.Scalar("ID")).toString()                                | "ID"
        ArgumentModel           | new ArgumentModel("limit", "limit", "int", new GraphQLTypeRef.Scalar("Int"), "10").toString()                | "limit"
        FieldModel              | new FieldModel("email", "email", new GraphQLTypeRef.Scalar("String"), true, false, "Email").toString()| "email"
        ObjectTypeModel         | new ObjectTypeModel("User", "com.example.User", "A user", []).toString()                             | "User"
        EnumTypeModel           | new EnumTypeModel("Status", "com.example.Status", ["ACTIVE", "INACTIVE"]).toString()                  | "ACTIVE"
        OperationModel          | new OperationModel(OperationKind.MUTATION, "createUser", "Create", new GraphQLTypeRef.ObjectRef("User"), false, "Svc", "create", [], -1).toString() | "createUser"
        SchemaModel             | new SchemaModel([], [], [], [], [], [], [], []).toString()                                                        | "SchemaModel"
    }
}
