package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum as a GraphQL enum type.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target(ElementType.TYPE)
public @interface GraphQLEnum {

    String name() default "";
}
