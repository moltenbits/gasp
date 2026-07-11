package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a GraphQL object type.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target(ElementType.TYPE)
public @interface GraphQLType {

    String name() default "";

    String description() default "";

    /**
     * When true, only methods explicitly annotated with @GraphQLField,
     * @GraphQLId, @GraphQLNonNull, or @GraphQLRelation are included
     * in the GraphQL schema. All other fields and methods are ignored.
     * <p>
     * Useful when annotating classes that have many inherited or internal
     * methods that should not be exposed in the GraphQL API.
     */
    boolean explicitFieldsOnly() default false;
}
