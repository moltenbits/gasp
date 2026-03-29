package com.moltenbits.gasp.processor;

import com.moltenbits.gasp.processor.model.OperationModel;
import com.moltenbits.gasp.processor.model.SchemaModel;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates the collected schema model and reports errors/warnings via Messager.
 */
public class Validator {

    /**
     * @return true if the model is valid (no errors), false if errors were emitted
     */
    public boolean validate(SchemaModel model, Messager messager) {
        boolean valid = true;

        // Check for void return types
        for (OperationModel op : model.queries()) {
            if (op.returnType() == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        String.format("@GraphQLQuery method '%s' in %s has void or unmappable return type",
                                op.methodName(), op.serviceClass()));
                valid = false;
            }
        }
        for (OperationModel op : model.mutations()) {
            if (op.returnType() == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        String.format("@GraphQLMutation method '%s' in %s has void or unmappable return type",
                                op.methodName(), op.serviceClass()));
                valid = false;
            }
        }
        for (OperationModel op : model.subscriptions()) {
            if (op.returnType() == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        String.format("@GraphQLSubscription method '%s' in %s has void or unmappable return type",
                                op.methodName(), op.serviceClass()));
                valid = false;
            }
        }

        // Check for duplicate operation names
        Set<String> queryNames = new HashSet<>();
        for (OperationModel op : model.queries()) {
            if (!queryNames.add(op.graphQLName())) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        String.format("Duplicate @GraphQLQuery name '%s'", op.graphQLName()));
                valid = false;
            }
        }
        Set<String> mutationNames = new HashSet<>();
        for (OperationModel op : model.mutations()) {
            if (!mutationNames.add(op.graphQLName())) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        String.format("Duplicate @GraphQLMutation name '%s'", op.graphQLName()));
                valid = false;
            }
        }

        // Check for null argument types
        for (OperationModel op : allOperations(model)) {
            for (var arg : op.arguments()) {
                if (arg.type() == null) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            String.format("Argument '%s' in method '%s' of %s has unmappable type",
                                    arg.graphQLName(), op.methodName(), op.serviceClass()));
                    valid = false;
                }
            }
        }

        // Warning: empty API class (no operations found)
        if (model.queries().isEmpty() && model.mutations().isEmpty() && model.subscriptions().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No @GraphQLQuery, @GraphQLMutation, or @GraphQLSubscription methods found");
        }

        return valid;
    }

    private Iterable<OperationModel> allOperations(SchemaModel model) {
        var all = new java.util.ArrayList<OperationModel>();
        all.addAll(model.queries());
        all.addAll(model.mutations());
        all.addAll(model.subscriptions());
        return all;
    }
}
