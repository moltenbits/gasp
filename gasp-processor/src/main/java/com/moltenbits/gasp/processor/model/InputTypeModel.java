package com.moltenbits.gasp.processor.model;
import java.util.List;
public record InputTypeModel(
    String graphQLName, String javaQualifiedName, String description, List<FieldModel> fields
) {}
