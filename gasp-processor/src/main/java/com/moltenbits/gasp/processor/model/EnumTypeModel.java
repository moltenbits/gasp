package com.moltenbits.gasp.processor.model;

import java.util.List;

public record EnumTypeModel(
    String graphQLName,
    String javaQualifiedName,
    List<String> values
) {}
