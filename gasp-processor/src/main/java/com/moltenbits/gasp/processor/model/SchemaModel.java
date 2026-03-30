package com.moltenbits.gasp.processor.model;

import java.util.List;

public record SchemaModel(
    List<ObjectTypeModel> types,
    List<InputTypeModel> inputTypes,
    List<InterfaceTypeModel> interfaces,
    List<EnumTypeModel> enums,
    List<OperationModel> queries,
    List<OperationModel> mutations,
    List<OperationModel> subscriptions,
    List<TypeFetcherModel> typeFetchers
) {}
