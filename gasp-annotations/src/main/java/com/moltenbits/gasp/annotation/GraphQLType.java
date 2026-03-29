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
}
