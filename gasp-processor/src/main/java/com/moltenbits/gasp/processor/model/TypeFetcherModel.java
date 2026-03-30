package com.moltenbits.gasp.processor.model;
public record TypeFetcherModel(
    String parentTypeName, String fieldName, String serviceClass, String methodName,
    GraphQLTypeRef returnType, int envParameterIndex
) {}
