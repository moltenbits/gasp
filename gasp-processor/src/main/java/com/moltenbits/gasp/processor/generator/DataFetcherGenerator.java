package com.moltenbits.gasp.processor.generator;

import com.moltenbits.gasp.processor.model.ArgumentModel;
import com.moltenbits.gasp.processor.model.GraphQLTypeRef;
import com.moltenbits.gasp.processor.model.OperationModel;
import com.moltenbits.gasp.processor.model.SchemaModel;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

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
        sb.append("import jakarta.inject.Named;\n");
        sb.append("import jakarta.inject.Singleton;\n");
        sb.append("import ").append(op.serviceClass()).append(";\n\n");
        sb.append("/**\n * Generated DataFetcher for ").append(op.graphQLName()).append(".\n */\n");
        sb.append("@Named\n");
        sb.append("@Singleton\n");
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
            String javaType = simpleClassName(arg.javaType());
            String extraction = extractionExpr(arg.graphQLName(), arg.javaType());
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

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "java.lang.Integer",
            "long", "java.lang.Long",
            "short", "java.lang.Short",
            "byte", "java.lang.Byte",
            "float", "java.lang.Float",
            "double", "java.lang.Double",
            "java.math.BigInteger", "java.math.BigDecimal"
    );

    private String extractionExpr(String argName, String javaType) {
        String baseExpr = "env.getArgument(\"" + argName + "\")";
        if (NUMERIC_TYPES.contains(javaType)) {
            String conversion = switch (javaType) {
                case "int", "java.lang.Integer" -> "intValue()";
                case "long", "java.lang.Long" -> "longValue()";
                case "short", "java.lang.Short" -> "shortValue()";
                case "byte", "java.lang.Byte" -> "byteValue()";
                case "float", "java.lang.Float" -> "floatValue()";
                case "double", "java.lang.Double" -> "doubleValue()";
                default -> null;
            };
            if (conversion != null) {
                return "env.<Number>getArgument(\"" + argName + "\") != null ? env.<Number>getArgument(\"" + argName + "\")." + conversion + " : null";
            }
        }
        return baseExpr;
    }
}
