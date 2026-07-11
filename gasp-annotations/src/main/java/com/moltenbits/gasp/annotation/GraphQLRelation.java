package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a GraphQL relationship to another entity.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GraphQLRelation {

    Class<?> entity() default void.class;

    /**
     * Declares this relation as a list (one-to-many or many-to-many).
     * When true, the generated GraphQL field uses a list type regardless of the
     * method's return type. This is useful when the return type doesn't directly
     * indicate list-ness (e.g., jOOQ path methods).
     */
    boolean list() default false;
}
