package com.moltenbits.gasp.runtime

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ScalarDefinitionsSpec extends Specification {

    def context = GraphQLContext.newContext().build()
    def locale = Locale.US
    def variables = CoercedVariables.emptyVariables()

    // --- DATE scalar: serialize ---

    def "DATE serialize formats LocalDate as ISO string"() {
        expect:
        ScalarDefinitions.DATE.coercing.serialize(LocalDate.of(2024, 1, 15), context, locale) == "2024-01-15"
    }

    def "DATE serialize throws on non-LocalDate"() {
        when:
        ScalarDefinitions.DATE.coercing.serialize("not a date", context, locale)

        then:
        thrown(CoercingSerializeException)
    }

    // --- DATE scalar: parseValue ---

    def "DATE parseValue parses ISO date string"() {
        expect:
        ScalarDefinitions.DATE.coercing.parseValue("2024-01-15", context, locale) == LocalDate.of(2024, 1, 15)
    }

    def "DATE parseValue throws on invalid string"() {
        when:
        ScalarDefinitions.DATE.coercing.parseValue("not-a-date", context, locale)

        then:
        thrown(CoercingParseValueException)
    }

    // --- DATE scalar: parseLiteral ---

    def "DATE parseLiteral parses StringValue"() {
        expect:
        ScalarDefinitions.DATE.coercing.parseLiteral(
                StringValue.newStringValue("2024-01-15").build(), variables, context, locale
        ) == LocalDate.of(2024, 1, 15)
    }

    def "DATE parseLiteral throws on invalid StringValue"() {
        when:
        ScalarDefinitions.DATE.coercing.parseLiteral(
                StringValue.newStringValue("garbage").build(), variables, context, locale
        )

        then:
        thrown(CoercingParseLiteralException)
    }

    def "DATE parseLiteral throws on non-StringValue"() {
        when:
        ScalarDefinitions.DATE.coercing.parseLiteral(
                IntValue.newIntValue(BigInteger.ONE).build(), variables, context, locale
        )

        then:
        thrown(CoercingParseLiteralException)
    }

    // --- DATE_TIME scalar: serialize ---

    def "DATE_TIME serialize formats LocalDateTime"() {
        expect:
        ScalarDefinitions.DATE_TIME.coercing.serialize(
                LocalDateTime.of(2024, 1, 15, 10, 30, 0), context, locale
        ) == "2024-01-15T10:30:00"
    }

    def "DATE_TIME serialize formats OffsetDateTime"() {
        expect:
        ScalarDefinitions.DATE_TIME.coercing.serialize(
                OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC), context, locale
        ) == "2024-01-15T10:30:00Z"
    }

    def "DATE_TIME serialize formats ZonedDateTime"() {
        when:
        def result = ScalarDefinitions.DATE_TIME.coercing.serialize(
                ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneId.of("America/New_York")), context, locale
        )

        then:
        result.startsWith("2024-01-15T10:30:00")
        result.contains("America/New_York")
    }

    def "DATE_TIME serialize formats Instant"() {
        expect:
        ScalarDefinitions.DATE_TIME.coercing.serialize(
                Instant.parse("2024-01-15T10:30:00Z"), context, locale
        ) == "2024-01-15T10:30:00Z"
    }

    def "DATE_TIME serialize throws on non-temporal type"() {
        when:
        ScalarDefinitions.DATE_TIME.coercing.serialize("not a datetime", context, locale)

        then:
        thrown(CoercingSerializeException)
    }

    // --- DATE_TIME scalar: parseValue ---

    def "DATE_TIME parseValue parses OffsetDateTime string first"() {
        when:
        def result = ScalarDefinitions.DATE_TIME.coercing.parseValue("2024-01-15T10:30:00Z", context, locale)

        then:
        result instanceof OffsetDateTime
        result == OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
    }

    def "DATE_TIME parseValue falls back to LocalDateTime"() {
        when:
        def result = ScalarDefinitions.DATE_TIME.coercing.parseValue("2024-01-15T10:30:00", context, locale)

        then:
        result instanceof LocalDateTime
        result == LocalDateTime.of(2024, 1, 15, 10, 30, 0)
    }

    def "DATE_TIME parseValue throws on invalid string"() {
        when:
        ScalarDefinitions.DATE_TIME.coercing.parseValue("not-a-datetime", context, locale)

        then:
        thrown(CoercingParseValueException)
    }

    // --- DATE_TIME scalar: parseLiteral ---

    def "DATE_TIME parseLiteral parses OffsetDateTime StringValue first"() {
        when:
        def result = ScalarDefinitions.DATE_TIME.coercing.parseLiteral(
                StringValue.newStringValue("2024-01-15T10:30:00+05:00").build(), variables, context, locale
        )

        then:
        result instanceof OffsetDateTime
    }

    def "DATE_TIME parseLiteral falls back to LocalDateTime StringValue"() {
        when:
        def result = ScalarDefinitions.DATE_TIME.coercing.parseLiteral(
                StringValue.newStringValue("2024-01-15T10:30:00").build(), variables, context, locale
        )

        then:
        result instanceof LocalDateTime
    }

    def "DATE_TIME parseLiteral throws on invalid StringValue"() {
        when:
        ScalarDefinitions.DATE_TIME.coercing.parseLiteral(
                StringValue.newStringValue("garbage").build(), variables, context, locale
        )

        then:
        thrown(CoercingParseLiteralException)
    }

    def "DATE_TIME parseLiteral throws on non-StringValue"() {
        when:
        ScalarDefinitions.DATE_TIME.coercing.parseLiteral(
                IntValue.newIntValue(BigInteger.ONE).build(), variables, context, locale
        )

        then:
        thrown(CoercingParseLiteralException)
    }
}
