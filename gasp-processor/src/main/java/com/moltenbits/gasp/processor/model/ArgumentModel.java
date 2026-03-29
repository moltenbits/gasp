package com.moltenbits.gasp.processor.model;

public record ArgumentModel(
    String graphQLName,
    String javaName,
    String javaType,
    GraphQLTypeRef type,
    String defaultValue
) {}
