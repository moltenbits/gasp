package com.moltenbits.gasp.processor.generator;

import com.moltenbits.gasp.processor.model.FieldModel;
import com.moltenbits.gasp.processor.model.GraphQLTypeRef;
import com.moltenbits.gasp.processor.model.InputTypeModel;
import com.moltenbits.gasp.processor.model.SchemaModel;

import com.moltenbits.gasp.processor.model.EnumTypeModel;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates compile-time converter classes for @GraphQLInputType POJOs.
 * Each converter has a static fromMap() method that converts a Map (as delivered
 * by graphql-java for input arguments) to the POJO using direct setter calls.
 * No reflection.
 */
public class InputConverterGenerator {

    private static final String GENERATED_PACKAGE = DataFetcherGenerator.GENERATED_PACKAGE;

    public void generate(SchemaModel model, Filer filer) throws IOException {
        enumNameToQualified = model.enums().stream()
                .collect(Collectors.toMap(EnumTypeModel::graphQLName, EnumTypeModel::javaQualifiedName));
        for (InputTypeModel inputType : model.inputTypes()) {
            generateConverter(inputType, filer);
        }
    }

    private Map<String, String> enumNameToQualified;

    private void generateConverter(InputTypeModel inputType, Filer filer) throws IOException {
        String simpleClassName = inputType.javaQualifiedName()
                .substring(inputType.javaQualifiedName().lastIndexOf('.') + 1);
        String converterClassName = simpleClassName + "Converter";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("import ").append(inputType.javaQualifiedName()).append(";\n");
        sb.append("import java.util.Map;\n");

        // Import enum types referenced by fields
        for (FieldModel field : inputType.fields()) {
            GraphQLTypeRef unwrapped = unwrap(field.type());
            if (unwrapped instanceof GraphQLTypeRef.EnumRef enumRef) {
                String qualifiedName = enumNameToQualified.get(enumRef.name());
                if (qualifiedName != null) {
                    sb.append("import ").append(qualifiedName).append(";\n");
                }
            }
        }
        sb.append("\n");

        sb.append("/**\n * Generated converter for ").append(simpleClassName)
                .append(". No reflection — direct setter calls.\n */\n");
        sb.append("public final class ").append(converterClassName).append(" {\n\n");
        sb.append("    private ").append(converterClassName).append("() {}\n\n");

        sb.append("    @SuppressWarnings(\"unchecked\")\n");
        sb.append("    public static ").append(simpleClassName)
                .append(" fromMap(Map<String, Object> map) {\n");
        sb.append("        if (map == null) return null;\n");
        sb.append("        ").append(simpleClassName).append(" obj = new ")
                .append(simpleClassName).append("();\n");

        for (FieldModel field : inputType.fields()) {
            String fieldName = field.graphQLName();
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            GraphQLTypeRef unwrapped = unwrap(field.type());

            sb.append("        if (map.containsKey(\"").append(fieldName).append("\")) {\n");

            if (unwrapped instanceof GraphQLTypeRef.EnumRef enumRef) {
                // Enum fields: graphql-java delivers as String, convert via valueOf
                // We need the enum's simple name which matches the GraphQL name
                sb.append("            Object val = map.get(\"").append(fieldName).append("\");\n");
                sb.append("            if (val instanceof String s) {\n");
                sb.append("                obj.").append(setterName).append("(")
                        .append(enumRef.name()).append(".valueOf(s));\n");
                sb.append("            }\n");
            } else if (unwrapped instanceof GraphQLTypeRef.Scalar s) {
                String cast = scalarCast(s.name());
                sb.append("            obj.").append(setterName).append("((")
                        .append(cast).append(") map.get(\"").append(fieldName).append("\"));\n");
            } else {
                sb.append("            obj.").append(setterName)
                        .append("(map.get(\"").append(fieldName).append("\"));\n");
            }

            sb.append("        }\n");
        }

        sb.append("        return obj;\n");
        sb.append("    }\n");
        sb.append("}\n");

        JavaFileObject file = filer.createSourceFile(GENERATED_PACKAGE + "." + converterClassName);
        try (Writer writer = file.openWriter()) {
            writer.write(sb.toString());
        }
    }

    public static String converterClassName(InputTypeModel inputType) {
        String simpleClassName = inputType.javaQualifiedName()
                .substring(inputType.javaQualifiedName().lastIndexOf('.') + 1);
        return simpleClassName + "Converter";
    }

    private static GraphQLTypeRef unwrap(GraphQLTypeRef ref) {
        if (ref instanceof GraphQLTypeRef.NonNull n) return n.inner();
        return ref;
    }

    private static String scalarCast(String scalarName) {
        return switch (scalarName) {
            case "String" -> "String";
            case "Int" -> "Integer";
            case "Float" -> "Double";
            case "Boolean" -> "Boolean";
            case "ID" -> "String";
            default -> "Object";
        };
    }
}
