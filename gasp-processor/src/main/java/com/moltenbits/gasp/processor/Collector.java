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
        List<ObjectTypeModel> objectTypes = new ArrayList<>();
        List<OperationModel> queries = new ArrayList<>();
        List<OperationModel> mutations = new ArrayList<>();
        List<OperationModel> subscriptions = new ArrayList<>();

        // Collect @GraphQLType classes
        for (Element element : roundEnv.getElementsAnnotatedWith(GraphQLType.class)) {
            if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD) continue;
            TypeElement typeElement = (TypeElement) element;
            objectTypes.add(buildObjectType(typeElement));
        }

        // Collect @GraphQLApi operations
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
                objectTypes,
                new ArrayList<>(enums),
                queries,
                mutations,
                subscriptions
        );
    }

    private ObjectTypeModel buildObjectType(TypeElement typeElement) {
        GraphQLType ann = typeElement.getAnnotation(GraphQLType.class);
        String graphQLName = (ann != null && !ann.name().isEmpty())
                ? ann.name()
                : typeElement.getSimpleName().toString();
        String description = (ann != null) ? ann.description() : "";

        List<FieldModel> fields = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            // Skip ignored fields
            if (enclosed.getAnnotation(GraphQLIgnore.class) != null) continue;

            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                String methodName = method.getSimpleName().toString();

                // Only include getter-style methods (getX/isX, no args, non-void)
                if (method.getParameters().isEmpty()
                        && method.getReturnType().getKind() != javax.lang.model.type.TypeKind.VOID
                        && !method.getModifiers().contains(Modifier.STATIC)) {

                    String fieldName = extractFieldName(methodName);
                    if (fieldName == null) continue;

                    // Check for @GraphQLField override
                    GraphQLField fieldAnn = method.getAnnotation(GraphQLField.class);
                    if (fieldAnn != null && !fieldAnn.name().isEmpty()) {
                        fieldName = fieldAnn.name();
                    }
                    String fieldDesc = (fieldAnn != null) ? fieldAnn.description() : "";

                    GraphQLTypeRef typeRef = typeResolver.resolve(method.getReturnType(), method);
                    if (typeRef == null) continue;

                    boolean isRelation = enclosed.getAnnotation(GraphQLRelation.class) != null;

                    fields.add(new FieldModel(fieldName, methodName, typeRef, true, isRelation, fieldDesc));
                }
            } else if (enclosed.getKind() == ElementKind.FIELD
                    && enclosed.getModifiers().contains(Modifier.PUBLIC)
                    && !enclosed.getModifiers().contains(Modifier.STATIC)) {

                VariableElement field = (VariableElement) enclosed;
                String fieldName = field.getSimpleName().toString();

                GraphQLField fieldAnn = field.getAnnotation(GraphQLField.class);
                if (fieldAnn != null && !fieldAnn.name().isEmpty()) {
                    fieldName = fieldAnn.name();
                }
                String fieldDesc = (fieldAnn != null) ? fieldAnn.description() : "";

                GraphQLTypeRef typeRef = typeResolver.resolve(field.asType(), field);
                if (typeRef == null) continue;

                boolean isRelation = field.getAnnotation(GraphQLRelation.class) != null;

                fields.add(new FieldModel(fieldName, field.getSimpleName().toString(), typeRef, true, isRelation, fieldDesc));
            } else if (enclosed.getKind() == ElementKind.RECORD_COMPONENT) {
                // Record components
                var component = (javax.lang.model.element.RecordComponentElement) enclosed;
                String fieldName = component.getSimpleName().toString();

                GraphQLField fieldAnn = component.getAnnotation(GraphQLField.class);
                if (fieldAnn != null && !fieldAnn.name().isEmpty()) {
                    fieldName = fieldAnn.name();
                }
                String fieldDesc = (fieldAnn != null) ? fieldAnn.description() : "";

                GraphQLTypeRef typeRef = typeResolver.resolve(component.asType(), component);
                if (typeRef == null) continue;

                boolean isRelation = component.getAnnotation(GraphQLRelation.class) != null;

                fields.add(new FieldModel(fieldName, component.getSimpleName().toString(), typeRef, true, isRelation, fieldDesc));
            }
        }

        return new ObjectTypeModel(graphQLName, typeElement.getQualifiedName().toString(), description, fields);
    }

    /**
     * Extract a field name from a getter method name.
     * "getTitle" → "title", "isActive" → "active", "name" → "name"
     */
    private static String extractFieldName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        // Skip common Object methods
        if (methodName.equals("toString") || methodName.equals("hashCode") || methodName.equals("getClass")) {
            return null;
        }
        return null;
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

            // Strip TYPE_USE annotations from the type string (e.g. "@NonNull java.lang.String" → "java.lang.String")
            String cleanParamType = param.asType().toString().replaceAll("@\\S+\\s+", "").trim();

            arguments.add(new ArgumentModel(argName, param.getSimpleName().toString(),
                    cleanParamType, argType, argDefault));
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
