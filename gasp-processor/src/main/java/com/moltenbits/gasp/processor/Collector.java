package com.moltenbits.gasp.processor;

import com.moltenbits.gasp.annotation.*;
import com.moltenbits.gasp.processor.model.*;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects annotated elements from the round environment and builds a SchemaModel.
 */
public class Collector {

    private final Elements elements;
    private final Types types;
    private final TypeResolver typeResolver;
    private final Set<EnumTypeModel> enums = new LinkedHashSet<>();

    public Collector(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
        this.typeResolver = new TypeResolver(elements, types);
    }

    public SchemaModel collect(RoundEnvironment roundEnv) {
        List<OperationModel> queries = new ArrayList<>();
        List<OperationModel> mutations = new ArrayList<>();
        List<OperationModel> subscriptions = new ArrayList<>();

        for (Element element : roundEnv.getElementsAnnotatedWith(GraphQLApi.class)) {
            if (element.getKind() != ElementKind.CLASS) continue;
            TypeElement serviceElement = (TypeElement) element;
            String serviceClass = serviceElement.getQualifiedName().toString();

            for (Element enclosed : serviceElement.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.METHOD) continue;
                ExecutableElement method = (ExecutableElement) enclosed;

                GraphQLQuery queryAnn = method.getAnnotation(GraphQLQuery.class);
                GraphQLMutation mutationAnn = method.getAnnotation(GraphQLMutation.class);
                GraphQLSubscription subscriptionAnn = method.getAnnotation(GraphQLSubscription.class);

                if (queryAnn != null) {
                    queries.add(buildOperation(OperationKind.QUERY, queryAnn.name(),
                            queryAnn.description(), method, serviceClass));
                }
                if (mutationAnn != null) {
                    mutations.add(buildOperation(OperationKind.MUTATION, mutationAnn.name(),
                            mutationAnn.description(), method, serviceClass));
                }
                if (subscriptionAnn != null) {
                    subscriptions.add(buildOperation(OperationKind.SUBSCRIPTION, subscriptionAnn.name(),
                            subscriptionAnn.description(), method, serviceClass));
                }
            }
        }

        return new SchemaModel(
                List.of(),  // types — populated in Phase 2
                new ArrayList<>(enums),
                queries,
                mutations,
                subscriptions
        );
    }

    private static final String DATA_FETCHING_ENVIRONMENT = "graphql.schema.DataFetchingEnvironment";

    private OperationModel buildOperation(OperationKind kind, String annotationName,
                                           String description, ExecutableElement method,
                                           String serviceClass) {
        String graphQLName = annotationName.isEmpty()
                ? method.getSimpleName().toString()
                : annotationName;

        GraphQLTypeRef returnType = typeResolver.resolve(method.getReturnType(), method);
        trackEnums(returnType, method.getReturnType());

        int envParameterIndex = -1;
        int paramIndex = 0;
        List<ArgumentModel> arguments = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            // DataFetchingEnvironment parameter — pass through, not a GraphQL argument
            String paramType = param.asType().toString();
            if (paramType.equals(DATA_FETCHING_ENVIRONMENT)) {
                envParameterIndex = paramIndex;
                paramIndex++;
                continue;
            }
            paramIndex++;

            GraphQLArgument argAnn = param.getAnnotation(GraphQLArgument.class);
            String argName = (argAnn != null && !argAnn.name().isEmpty())
                    ? argAnn.name()
                    : param.getSimpleName().toString();
            String argDescription = (argAnn != null) ? argAnn.description() : "";
            String argDefault = (argAnn != null) ? argAnn.defaultValue() : "";

            GraphQLTypeRef argType = typeResolver.resolve(param.asType(), param);
            trackEnums(argType, param.asType());

            arguments.add(new ArgumentModel(argName, param.getSimpleName().toString(),
                    argType, argDefault));
        }

        return new OperationModel(kind, graphQLName, description, returnType,
                serviceClass, method.getSimpleName().toString(), arguments, envParameterIndex);
    }

    private void trackEnums(GraphQLTypeRef ref, javax.lang.model.type.TypeMirror typeMirror) {
        if (ref instanceof GraphQLTypeRef.EnumRef enumRef) {
            if (typeMirror instanceof javax.lang.model.type.DeclaredType dt) {
                TypeElement te = (TypeElement) dt.asElement();
                List<String> values = te.getEnclosedElements().stream()
                        .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                        .map(e -> e.getSimpleName().toString())
                        .toList();
                enums.add(new EnumTypeModel(enumRef.name(), te.getQualifiedName().toString(), values));
            }
        } else if (ref instanceof GraphQLTypeRef.ListOf listOf) {
            trackEnums(listOf.inner(), typeMirror);
        } else if (ref instanceof GraphQLTypeRef.NonNull nonNull) {
            trackEnums(nonNull.inner(), typeMirror);
        }
    }
}
