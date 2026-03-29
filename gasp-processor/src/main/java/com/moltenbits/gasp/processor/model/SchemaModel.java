package com.moltenbits.gasp.processor.model;

import java.util.List;

public record SchemaModel(
    List<ObjectTypeModel> types,
    List<EnumTypeModel> enums,
    List<OperationModel> queries,
    List<OperationModel> mutations,
    List<OperationModel> subscriptions
) {}
