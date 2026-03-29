package com.moltenbits.gasp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a GraphQL API endpoint, enabling its methods as schema operations.
 */
@Retention(RetentionPolicy.CLASS)
@Documented
@Target(ElementType.TYPE)
public @interface GraphQLApi {
}
