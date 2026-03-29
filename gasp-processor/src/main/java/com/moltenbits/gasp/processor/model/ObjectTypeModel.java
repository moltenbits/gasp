package com.moltenbits.gasp.processor.model;

import java.util.List;

public record ObjectTypeModel(
    String graphQLName,
    String javaQualifiedName,
    String description,
    List<FieldModel> fields
) {}
