package com.moltenbits.gasp.processor.generator;

import com.moltenbits.gasp.processor.model.ArgumentModel;
import com.moltenbits.gasp.processor.model.GraphQLTypeRef;
import com.moltenbits.gasp.processor.model.OperationModel;
import com.moltenbits.gasp.processor.model.SchemaModel;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;

/**
 * Generates DataFetcher implementation classes for each operation.
 */
public class DataFetcherGenerator {

    public static final String GENERATED_PACKAGE = "com.moltenbits.gasp.generated";

    public void generate(SchemaModel model, Filer filer) throws IOException {
        for (OperationModel op : model.queries()) {
            generateFetcher(op, filer);
        }
        for (OperationModel op : model.mutations()) {
            generateFetcher(op, filer);
        }
        for (OperationModel op : model.subscriptions()) {
            generateFetcher(op, filer);
        }
    }

    private void generateFetcher(OperationModel op, Filer filer) throws IOException {
        String className = fetcherClassName(op);
        String serviceSimpleName = simpleClassName(op.serviceClass());

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("import graphql.schema.DataFetcher;\n");
        sb.append("import graphql.schema.DataFetchingEnvironment;\n");
        sb.append("import ").append(op.serviceClass()).append(";\n\n");
        sb.append("/**\n * Generated DataFetcher for ").append(op.graphQLName()).append(".\n */\n");
        sb.append("public class ").append(className)
                .append(" implements DataFetcher<Object> {\n\n");

        // Field
        sb.append("    private final ").append(serviceSimpleName).append(" service;\n\n");

        // Constructor
        sb.append("    public ").append(className).append("(")
                .append(serviceSimpleName).append(" service) {\n");
        sb.append("        this.service = service;\n");
        sb.append("    }\n\n");

        // get() method
        sb.append("    @Override\n");
        sb.append("    public Object get(DataFetchingEnvironment env) throws Exception {\n");

        // Extract arguments
        for (ArgumentModel arg : op.arguments()) {
            String javaType = javaTypeForArg(arg.type());
            String extraction = extractionExpr(arg.graphQLName(), arg.type());
            sb.append("        ").append(javaType).append(" ").append(arg.javaName())
                    .append(" = ").append(extraction).append(";\n");
        }

        // Call service method — interleave env at its original position
        sb.append("        return service.").append(op.methodName()).append("(");
        int argIndex = 0;
        int totalParams = op.arguments().size() + (op.passEnvironment() ? 1 : 0);
        for (int i = 0; i < totalParams; i++) {
            if (i > 0) sb.append(", ");
            if (i == op.envParameterIndex()) {
                sb.append("env");
            } else {
                sb.append(op.arguments().get(argIndex++).javaName());
            }
        }
        sb.append(");\n");
        sb.append("    }\n");
        sb.append("}\n");

        JavaFileObject file = filer.createSourceFile(GENERATED_PACKAGE + "." + className);
        try (Writer writer = file.openWriter()) {
            writer.write(sb.toString());
        }
    }

    public static String fetcherClassName(OperationModel op) {
        String serviceSimple = simpleClassName(op.serviceClass());
        String capitalizedName = op.graphQLName().substring(0, 1).toUpperCase() + op.graphQLName().substring(1);
        return serviceSimple + "_" + capitalizedName + "Fetcher";
    }

    private static String simpleClassName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private String javaTypeForArg(GraphQLTypeRef type) {
        if (type == null) return "Object";
        return switch (type) {
            case GraphQLTypeRef.NonNull n -> javaTypeForArg(n.inner());
            case GraphQLTypeRef.Scalar s -> switch (s.name()) {
                case "String" -> "String";
                case "Int" -> "Integer";
                case "Float" -> "Double";
                case "Boolean" -> "Boolean";
                case "ID" -> "String";
                default -> "Object";
            };
            default -> "Object";
        };
    }

    private String extractionExpr(String argName, GraphQLTypeRef type) {
        String baseExpr = "env.getArgument(\"" + argName + "\")";
        if (type == null) return baseExpr;

        GraphQLTypeRef unwrapped = type instanceof GraphQLTypeRef.NonNull n ? n.inner() : type;
        if (unwrapped instanceof GraphQLTypeRef.Scalar s) {
            return switch (s.name()) {
                case "Int" -> "env.<Number>getArgument(\"" + argName + "\") != null ? env.<Number>getArgument(\"" + argName + "\").intValue() : null";
                case "Float" -> "env.<Number>getArgument(\"" + argName + "\") != null ? env.<Number>getArgument(\"" + argName + "\").doubleValue() : null";
                default -> baseExpr;
            };
        }
        return baseExpr;
    }
}
