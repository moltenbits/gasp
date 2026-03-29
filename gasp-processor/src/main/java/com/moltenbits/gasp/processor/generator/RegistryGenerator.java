package com.moltenbits.gasp.processor.generator;

import com.moltenbits.gasp.processor.model.OperationModel;
import com.moltenbits.gasp.processor.model.SchemaModel;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the GaspSchemaRegistry class that wires all DataFetchers to the schema.
 */
public class RegistryGenerator {

    private static final String GENERATED_PACKAGE = DataFetcherGenerator.GENERATED_PACKAGE;

    public void generate(SchemaModel model, Filer filer) throws IOException {
        List<OperationModel> allOps = new ArrayList<>();
        allOps.addAll(model.queries());
        allOps.addAll(model.mutations());
        allOps.addAll(model.subscriptions());

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("import graphql.schema.DataFetcher;\n");
        sb.append("import graphql.schema.GraphQLSchema;\n");
        sb.append("import com.moltenbits.gasp.runtime.SchemaLoader;\n");
        sb.append("import com.moltenbits.gasp.runtime.SchemaProvider;\n");
        sb.append("import jakarta.inject.Named;\n");
        sb.append("import jakarta.inject.Singleton;\n\n");
        sb.append("import java.util.LinkedHashMap;\n");
        sb.append("import java.util.Map;\n\n");
        sb.append("/**\n * Generated schema registry. Wires all DataFetchers to the GraphQL schema.\n */\n");
        sb.append("@Named\n");
        sb.append("@Singleton\n");
        sb.append("public final class GaspSchemaRegistry implements SchemaProvider {\n\n");

        sb.append("    private final Map<String, DataFetcher<?>> queryFetchers = new LinkedHashMap<>();\n");
        sb.append("    private final Map<String, DataFetcher<?>> mutationFetchers = new LinkedHashMap<>();\n");
        sb.append("    private final Map<String, Map<String, DataFetcher<?>>> typeFetchers = new LinkedHashMap<>();\n\n");

        // Constructor
        sb.append("    public GaspSchemaRegistry(\n");
        for (int i = 0; i < allOps.size(); i++) {
            OperationModel op = allOps.get(i);
            String fetcherClass = DataFetcherGenerator.fetcherClassName(op);
            String paramName = toLowerCamelCase(fetcherClass);
            sb.append("            ").append(fetcherClass).append(" ").append(paramName);
            if (i < allOps.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    ) {\n");

        for (OperationModel op : model.queries()) {
            String paramName = toLowerCamelCase(DataFetcherGenerator.fetcherClassName(op));
            sb.append("        queryFetchers.put(\"").append(op.graphQLName()).append("\", ").append(paramName).append(");\n");
        }
        for (OperationModel op : model.mutations()) {
            String paramName = toLowerCamelCase(DataFetcherGenerator.fetcherClassName(op));
            sb.append("        mutationFetchers.put(\"").append(op.graphQLName()).append("\", ").append(paramName).append(");\n");
        }

        sb.append("    }\n\n");

        // buildSchema method
        sb.append("    @Override\n");
        sb.append("    public GraphQLSchema buildSchema() {\n");
        sb.append("        return SchemaLoader.load(queryFetchers, mutationFetchers, typeFetchers);\n");
        sb.append("    }\n\n");

        // Getters for testing
        sb.append("    public Map<String, DataFetcher<?>> getQueryFetchers() { return queryFetchers; }\n");
        sb.append("    public Map<String, DataFetcher<?>> getMutationFetchers() { return mutationFetchers; }\n");
        sb.append("    public Map<String, Map<String, DataFetcher<?>>> getTypeFetchers() { return typeFetchers; }\n");

        sb.append("}\n");

        JavaFileObject file = filer.createSourceFile(GENERATED_PACKAGE + ".GaspSchemaRegistry");
        try (Writer writer = file.openWriter()) {
            writer.write(sb.toString());
        }
    }

    private static String toLowerCamelCase(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
