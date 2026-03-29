package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures a method parameter as a GraphQL argument.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target(ElementType.PARAMETER)
public @interface GraphQLArgument {

    String name() default "";

    String description() default "";

    String defaultValue() default "";
}
