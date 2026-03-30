package com.moltenbits.gasp.processor.model;

public sealed interface GraphQLTypeRef {
    record Scalar(String name) implements GraphQLTypeRef {}
    record ObjectRef(String name) implements GraphQLTypeRef {}
    record InputRef(String name) implements GraphQLTypeRef {}
    record EnumRef(String name) implements GraphQLTypeRef {}
    record ListOf(GraphQLTypeRef inner) implements GraphQLTypeRef {}
    record NonNull(GraphQLTypeRef inner) implements GraphQLTypeRef {}
}
