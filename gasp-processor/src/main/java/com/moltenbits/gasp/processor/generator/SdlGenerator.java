package com.moltenbits.gasp.processor.generator;

import com.moltenbits.gasp.processor.model.*;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates the GraphQL SDL schema file from the SchemaModel.
 */
public class SdlGenerator {

    public void generate(SchemaModel model, Filer filer) throws IOException {
        StringBuilder sdl = new StringBuilder();
        Set<String> customScalars = new LinkedHashSet<>();

        // Query type
        if (!model.queries().isEmpty()) {
            sdl.append("type Query {\n");
            for (OperationModel op : model.queries()) {
                sdl.append("  ").append(renderField(op, customScalars)).append("\n");
            }
            sdl.append("}\n\n");
        }

        // Mutation type
        if (!model.mutations().isEmpty()) {
            sdl.append("type Mutation {\n");
            for (OperationModel op : model.mutations()) {
                sdl.append("  ").append(renderField(op, customScalars)).append("\n");
            }
            sdl.append("}\n\n");
        }

        // Subscription type
        if (!model.subscriptions().isEmpty()) {
            sdl.append("type Subscription {\n");
            for (OperationModel op : model.subscriptions()) {
                sdl.append("  ").append(renderField(op, customScalars)).append("\n");
            }
            sdl.append("}\n\n");
        }

        // Enum types
        for (EnumTypeModel enumType : model.enums()) {
            sdl.append("enum ").append(enumType.graphQLName()).append(" {\n");
            for (String value : enumType.values()) {
                sdl.append("  ").append(value).append("\n");
            }
            sdl.append("}\n\n");
        }

        // Custom scalars
        for (String scalar : customScalars) {
            sdl.append("scalar ").append(scalar).append("\n");
        }

        FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/gasp/schema.graphqls");
        try (Writer writer = resource.openWriter()) {
            writer.write(sdl.toString());
        }
    }

    private String renderField(OperationModel op, Set<String> customScalars) {
        StringBuilder sb = new StringBuilder();
        sb.append(op.graphQLName());

        if (!op.arguments().isEmpty()) {
            sb.append("(");
            for (int i = 0; i < op.arguments().size(); i++) {
                if (i > 0) sb.append(", ");
                ArgumentModel arg = op.arguments().get(i);
                sb.append(arg.graphQLName()).append(": ").append(renderTypeRef(arg.type(), customScalars));
                if (!arg.defaultValue().isEmpty()) {
                    sb.append(" = ").append(arg.defaultValue());
                }
            }
            sb.append(")");
        }

        sb.append(": ").append(renderTypeRef(op.returnType(), customScalars));
        return sb.toString();
    }

    /**
     * Render a GraphQLTypeRef to SDL syntax.
     */
    public static String renderTypeRef(GraphQLTypeRef ref, Set<String> customScalars) {
        if (ref == null) return "String"; // fallback for unresolved

        return switch (ref) {
            case GraphQLTypeRef.Scalar s -> {
                trackCustomScalar(s.name(), customScalars);
                yield s.name();
            }
            case GraphQLTypeRef.ObjectRef o -> o.name();
            case GraphQLTypeRef.EnumRef e -> e.name();
            case GraphQLTypeRef.ListOf l -> "[" + renderTypeRef(l.inner(), customScalars) + "]";
            case GraphQLTypeRef.NonNull n -> renderTypeRef(n.inner(), customScalars) + "!";
        };
    }

    private static void trackCustomScalar(String name, Set<String> customScalars) {
        if (!name.equals("String") && !name.equals("Int") && !name.equals("Float")
                && !name.equals("Boolean") && !name.equals("ID")) {
            customScalars.add(name);
        }
    }
}
