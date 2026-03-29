package com.moltenbits.gasp.annotation

import spock.lang.Specification
import spock.lang.Unroll

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.annotation.Documented

class AnnotationSpec extends Specification {

    @Unroll
    def "#annotationClass.simpleName has CLASS retention"() {
        expect:
        annotationClass.getAnnotation(Retention).value() == RetentionPolicy.CLASS

        where:
        annotationClass << allAnnotations()
    }

    @Unroll
    def "#annotationClass.simpleName is @Documented"() {
        expect:
        annotationClass.isAnnotationPresent(Documented)

        where:
        annotationClass << allAnnotations()
    }

    @Unroll
    def "#annotationClass.simpleName targets #expectedTargets"() {
        expect:
        annotationClass.getAnnotation(Target).value() as Set == expectedTargets as Set

        where:
        annotationClass      | expectedTargets
        GraphQLType          | [ElementType.TYPE]
        GraphQLInputType     | [ElementType.TYPE]
        GraphQLInterface     | [ElementType.TYPE]
        GraphQLEnum          | [ElementType.TYPE]
        GraphQLApi           | [ElementType.TYPE]
        GraphQLQuery         | [ElementType.METHOD]
        GraphQLMutation      | [ElementType.METHOD]
        GraphQLSubscription  | [ElementType.METHOD]
        GraphQLArgument      | [ElementType.PARAMETER]
        GraphQLField         | [ElementType.METHOD, ElementType.FIELD]
        GraphQLIgnore        | [ElementType.METHOD, ElementType.FIELD]
        GraphQLId            | [ElementType.METHOD, ElementType.FIELD]
        GraphQLRelation      | [ElementType.METHOD, ElementType.FIELD]
        GraphQLBatched       | [ElementType.METHOD, ElementType.FIELD]
        GraphQLNonNull       | [ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER]
    }

    private static List<Class> allAnnotations() {
        [
            GraphQLType, GraphQLInputType, GraphQLInterface, GraphQLEnum,
            GraphQLField, GraphQLIgnore, GraphQLId, GraphQLNonNull,
            GraphQLApi, GraphQLQuery, GraphQLMutation, GraphQLSubscription,
            GraphQLArgument, GraphQLRelation, GraphQLBatched
        ]
    }
}
