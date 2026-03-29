package com.moltenbits.gasp.processor.model;

public record ArgumentModel(
    String graphQLName,
    String javaName,
    GraphQLTypeRef type,
    String defaultValue
) {}
