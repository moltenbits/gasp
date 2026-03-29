package com.moltenbits.gasp.processor;

import com.moltenbits.gasp.annotation.GraphQLId;
import com.moltenbits.gasp.annotation.GraphQLNonNull;
import com.moltenbits.gasp.annotation.GraphQLType;
import com.moltenbits.gasp.processor.model.GraphQLTypeRef;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves Java TypeMirror instances to GraphQL type references.
 */
public class TypeResolver {

    private static final Map<String, String> BOXED_SCALAR_MAP = Map.ofEntries(
            Map.entry("java.lang.String", "String"),
            Map.entry("java.lang.CharSequence", "String"),
            Map.entry("java.lang.Integer", "Int"),
            Map.entry("java.lang.Short", "Int"),
            Map.entry("java.lang.Byte", "Int"),
            Map.entry("java.lang.Long", "Int"),
            Map.entry("java.math.BigInteger", "Int"),
            Map.entry("java.lang.Float", "Float"),
            Map.entry("java.lang.Double", "Float"),
            Map.entry("java.math.BigDecimal", "Float"),
            Map.entry("java.lang.Boolean", "Boolean"),
            Map.entry("java.util.UUID", "ID"),
            Map.entry("java.time.LocalDate", "Date"),
            Map.entry("java.time.LocalDateTime", "DateTime"),
            Map.entry("java.time.Instant", "DateTime"),
            Map.entry("java.time.OffsetDateTime", "DateTime"),
            Map.entry("java.time.ZonedDateTime", "DateTime")
    );

    private static final Set<String> COLLECTION_TYPES = Set.of(
            "java.util.List", "java.util.Set", "java.util.Collection", "java.lang.Iterable"
    );

    private static final Set<String> UNWRAP_TYPES = Set.of(
            "java.util.Optional",
            "java.util.concurrent.CompletableFuture",
            "java.util.concurrent.CompletionStage",
            "org.reactivestreams.Publisher",
            "reactor.core.publisher.Mono",
            "reactor.core.publisher.Flux",
            "io.reactivex.rxjava3.core.Single",
            "io.reactivex.rxjava3.core.Maybe"
    );

    private final Elements elements;
    private final Types types;

    public TypeResolver(Elements elements, Types types) {
        this.elements = elements;
        this.types = types;
    }

    /**
     * Resolve a TypeMirror to a GraphQLTypeRef.
     *
     * @param type             the Java type to resolve
     * @param annotatedElement the element bearing the type (for checking @GraphQLId, @GraphQLNonNull)
     * @return the resolved type ref, or null if the type is not mappable
     */
    public GraphQLTypeRef resolve(TypeMirror type, Element annotatedElement) {
        // Handle primitives
        if (type.getKind().isPrimitive()) {
            return resolvePrimitive(type, annotatedElement);
        }

        // Handle declared types (classes, interfaces, enums)
        if (type.getKind() == TypeKind.DECLARED) {
            return resolveDeclared((DeclaredType) type, annotatedElement);
        }

        return null;
    }

    /**
     * Check for a named annotation on either the element or its type (for TYPE_USE annotations).
     */
    private boolean hasAnnotationOnElementOrType(Element element, TypeMirror type, String qualifiedName) {
        if (hasAnnotationByName(element, qualifiedName)) {
            return true;
        }
        // Check TYPE_USE annotations on the type mirror itself
        if (type != null) {
            for (var ann : type.getAnnotationMirrors()) {
                if (ann.getAnnotationType().asElement().toString().equals(qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private GraphQLTypeRef resolvePrimitive(TypeMirror type, Element annotatedElement) {
        String scalarName = switch (type.getKind()) {
            case BOOLEAN -> "Boolean";
            case INT, SHORT, BYTE -> hasAnnotation(annotatedElement, GraphQLId.class) ? "ID" : "Int";
            case LONG -> hasAnnotation(annotatedElement, GraphQLId.class) ? "ID" : "Int";
            case FLOAT, DOUBLE -> "Float";
            default -> null;
        };
        if (scalarName == null) return null;
        GraphQLTypeRef ref = new GraphQLTypeRef.Scalar(scalarName);
        return new GraphQLTypeRef.NonNull(ref);
    }

    private GraphQLTypeRef resolveDeclared(DeclaredType type, Element annotatedElement) {
        TypeElement typeElement = (TypeElement) type.asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        // Check for ID override
        boolean isId = hasAnnotation(annotatedElement, GraphQLId.class);

        // Check boxed scalars
        if (isId && (qualifiedName.equals("java.lang.Long") || qualifiedName.equals("java.lang.Integer")
                || qualifiedName.equals("java.math.BigInteger"))) {
            return maybeNonNull(new GraphQLTypeRef.Scalar("ID"), annotatedElement, type);
        }
        if (BOXED_SCALAR_MAP.containsKey(qualifiedName)) {
            GraphQLTypeRef ref = new GraphQLTypeRef.Scalar(BOXED_SCALAR_MAP.get(qualifiedName));
            return maybeNonNull(ref, annotatedElement, type);
        }

        // Check Optional (strips nullability)
        if (qualifiedName.equals("java.util.Optional")) {
            List<? extends TypeMirror> typeArgs = type.getTypeArguments();
            if (typeArgs.isEmpty()) return null;
            GraphQLTypeRef inner = resolve(typeArgs.get(0), null);
            return inner != null ? stripNonNull(inner) : null;
        }

        // Check collections → ListOf
        if (COLLECTION_TYPES.contains(qualifiedName)) {
            List<? extends TypeMirror> typeArgs = type.getTypeArguments();
            if (typeArgs.isEmpty()) return null;
            GraphQLTypeRef inner = resolve(typeArgs.get(0), null);
            return inner != null ? maybeNonNull(new GraphQLTypeRef.ListOf(inner), annotatedElement, type) : null;
        }

        // Check async wrappers → unwrap
        if (UNWRAP_TYPES.contains(qualifiedName)) {
            List<? extends TypeMirror> typeArgs = type.getTypeArguments();
            if (typeArgs.isEmpty()) return null;
            return resolve(typeArgs.get(0), annotatedElement);
        }

        // Check Flux specifically (it's a collection-like async type → ListOf)
        if (qualifiedName.equals("reactor.core.publisher.Flux")) {
            List<? extends TypeMirror> typeArgs = type.getTypeArguments();
            if (typeArgs.isEmpty()) return null;
            GraphQLTypeRef inner = resolve(typeArgs.get(0), null);
            return inner != null ? maybeNonNull(new GraphQLTypeRef.ListOf(inner), annotatedElement, type) : null;
        }

        // Check enums
        if (typeElement.getKind() == ElementKind.ENUM) {
            return maybeNonNull(new GraphQLTypeRef.EnumRef(typeElement.getSimpleName().toString()), annotatedElement, type);
        }

        // Check @GraphQLType-annotated classes (or any class → ObjectRef)
        if (typeElement.getAnnotation(GraphQLType.class) != null || isKnownEntityType(typeElement)) {
            String name = typeElement.getSimpleName().toString();
            GraphQLType ann = typeElement.getAnnotation(GraphQLType.class);
            if (ann != null && !ann.name().isEmpty()) {
                name = ann.name();
            }
            return maybeNonNull(new GraphQLTypeRef.ObjectRef(name), annotatedElement, type);
        }

        // Fallback: treat any class as an ObjectRef (may be user-defined type)
        return maybeNonNull(new GraphQLTypeRef.ObjectRef(typeElement.getSimpleName().toString()), annotatedElement, type);
    }

    private boolean isKnownEntityType(TypeElement typeElement) {
        return hasAnnotationByName(typeElement, "jakarta.persistence.Entity")
                || hasAnnotationByName(typeElement, "io.micronaut.data.annotation.MappedEntity");
    }

    private GraphQLTypeRef maybeNonNull(GraphQLTypeRef ref, Element annotatedElement, TypeMirror type) {
        if (annotatedElement != null) {
            // GASP's own @GraphQLNonNull always wins — it's an explicit schema override
            if (hasAnnotation(annotatedElement, GraphQLNonNull.class)) {
                return new GraphQLTypeRef.NonNull(ref);
            }
            // JSpecify @Nullable means nullable — strip NonNull if somehow present
            if (hasAnnotationOnElementOrType(annotatedElement, type, "org.jspecify.annotations.Nullable")) {
                return stripNonNull(ref);
            }
            // JSpecify @NonNull means non-null
            if (hasAnnotationOnElementOrType(annotatedElement, type, "org.jspecify.annotations.NonNull")) {
                return new GraphQLTypeRef.NonNull(ref);
            }
        }
        return ref;
    }

    private GraphQLTypeRef stripNonNull(GraphQLTypeRef ref) {
        if (ref instanceof GraphQLTypeRef.NonNull nonNull) {
            return nonNull.inner();
        }
        return ref;
    }

    private boolean hasAnnotation(Element element, Class<?> annotationClass) {
        return hasAnnotationByName(element, annotationClass.getName());
    }

    private boolean hasAnnotationByName(Element element, String qualifiedName) {
        if (element == null) return false;
        for (var ann : element.getAnnotationMirrors()) {
            if (ann.getAnnotationType().asElement().toString().equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }
}
