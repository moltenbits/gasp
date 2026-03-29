package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or field as a GraphQL ID type.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GraphQLId {
}
