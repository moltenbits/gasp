package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures a method or field as a GraphQL field.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GraphQLField {

    String name() default "";

    String description() default "";

    boolean deprecated() default false;

    String deprecationReason() default "";

    /**
     * When set to a non-void class, wires this method as a type-level fetcher on the
     * specified GraphQL object type rather than treating it as an entity field.
     */
    Class<?> on() default void.class;
}
