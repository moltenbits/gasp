package com.moltenbits.gasp.processor.model;

public record FieldModel(
    String graphQLName,
    String javaFieldName,
    GraphQLTypeRef type,
    boolean nullable,
    boolean isRelation,
    String description
) {}
