package com.moltenbits.gasp.processor;

import com.moltenbits.gasp.processor.generator.DataFetcherGenerator;
import com.moltenbits.gasp.processor.generator.RegistryGenerator;
import com.moltenbits.gasp.processor.generator.SdlGenerator;
import com.moltenbits.gasp.processor.model.SchemaModel;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.Set;

/**
 * GASP annotation processor. Collects @GraphQLApi-annotated classes, resolves types,
 * validates the model, and generates SDL, DataFetchers, and a schema registry.
 */
@SupportedAnnotationTypes({
        "com.moltenbits.gasp.annotation.GraphQLType",
        "com.moltenbits.gasp.annotation.GraphQLApi",
        "com.moltenbits.gasp.annotation.GraphQLQuery",
        "com.moltenbits.gasp.annotation.GraphQLMutation",
        "com.moltenbits.gasp.annotation.GraphQLSubscription",
        "com.moltenbits.gasp.annotation.GraphQLInputType",
        "com.moltenbits.gasp.annotation.GraphQLInterface",
        "com.moltenbits.gasp.annotation.GraphQLEnum",
        "com.moltenbits.gasp.annotation.GraphQLField"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class GaspProcessor extends AbstractProcessor {

    private boolean processed = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (processed || roundEnv.processingOver()) {
            return false;
        }

        // Only process if there are @GraphQLApi annotations present
        if (annotations.isEmpty()) {
            return false;
        }

        processed = true;

        Collector collector = new Collector(
                processingEnv.getElementUtils(),
                processingEnv.getTypeUtils()
        );
        SchemaModel model = collector.collect(roundEnv);

        Validator validator = new Validator();
        if (!validator.validate(model, processingEnv.getMessager())) {
            return true; // errors emitted, stop
        }

        // Skip generation if nothing to generate
        if (model.types().isEmpty() && model.inputTypes().isEmpty() && model.interfaces().isEmpty()
                && model.enums().isEmpty()
                && model.queries().isEmpty() && model.mutations().isEmpty() && model.subscriptions().isEmpty()) {
            return true;
        }

        try {
            Filer filer = processingEnv.getFiler();
            new SdlGenerator().generate(model, filer);
            new DataFetcherGenerator().generate(model, filer);
            new RegistryGenerator().generate(model, filer);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    javax.tools.Diagnostic.Kind.ERROR,
                    "GASP code generation failed: " + e.getMessage()
            );
        }

        return true;
    }
}
