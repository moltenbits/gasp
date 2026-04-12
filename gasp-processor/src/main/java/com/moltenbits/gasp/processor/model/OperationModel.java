package com.moltenbits.gasp.processor.model;

import com.moltenbits.gasp.annotation.OperationKind;

import java.util.List;

/**
 * @param envParameterIndex index of the DataFetchingEnvironment parameter in the original
 *                          method signature, or -1 if not present. Arguments list excludes
 *                          the env parameter, so this index is relative to the full parameter list.
 */
public record OperationModel(
    OperationKind kind,
    String graphQLName,
    String description,
    GraphQLTypeRef returnType,
    boolean returnsComposableQuery,
    String serviceClass,
    String methodName,
    List<ArgumentModel> arguments,
    int envParameterIndex
) {
    public boolean passEnvironment() {
        return envParameterIndex >= 0;
    }
}
