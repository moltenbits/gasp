package com.moltenbits.gasp.runtime;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Custom GraphQL scalar type definitions for date/time types.
 */
public final class ScalarDefinitions {

    private ScalarDefinitions() {}

    public static final GraphQLScalarType DATE = GraphQLScalarType.newScalar()
            .name("Date")
            .description("ISO-8601 date (e.g. 2024-01-15)")
            .coercing(new Coercing<LocalDate, String>() {
                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext context, Locale locale)
                        throws CoercingSerializeException {
                    if (dataFetcherResult instanceof LocalDate ld) {
                        return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    }
                    throw new CoercingSerializeException("Expected LocalDate but got: " + dataFetcherResult.getClass());
                }

                @Override
                public LocalDate parseValue(Object input, GraphQLContext context, Locale locale)
                        throws CoercingParseValueException {
                    try {
                        return LocalDate.parse(input.toString(), DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseValueException("Invalid Date value: " + input, e);
                    }
                }

                @Override
                public LocalDate parseLiteral(Value<?> input, CoercedVariables variables,
                                              GraphQLContext context, Locale locale)
                        throws CoercingParseLiteralException {
                    if (input instanceof StringValue sv) {
                        try {
                            return LocalDate.parse(sv.getValue(), DateTimeFormatter.ISO_LOCAL_DATE);
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseLiteralException("Invalid Date literal: " + sv.getValue(), e);
                        }
                    }
                    throw new CoercingParseLiteralException("Expected StringValue for Date");
                }
            })
            .build();

    public static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("ISO-8601 date-time (e.g. 2024-01-15T10:30:00Z)")
            .coercing(new Coercing<TemporalAccessor, String>() {
                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext context, Locale locale)
                        throws CoercingSerializeException {
                    if (dataFetcherResult instanceof LocalDateTime ldt) {
                        return ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    if (dataFetcherResult instanceof OffsetDateTime odt) {
                        return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    }
                    if (dataFetcherResult instanceof ZonedDateTime zdt) {
                        return zdt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
                    }
                    if (dataFetcherResult instanceof Instant inst) {
                        return inst.toString();
                    }
                    throw new CoercingSerializeException("Expected a date-time type but got: " + dataFetcherResult.getClass());
                }

                @Override
                public TemporalAccessor parseValue(Object input, GraphQLContext context, Locale locale)
                        throws CoercingParseValueException {
                    try {
                        String str = input.toString();
                        return OffsetDateTime.parse(str, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    } catch (DateTimeParseException e1) {
                        try {
                            return LocalDateTime.parse(input.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (DateTimeParseException e2) {
                            throw new CoercingParseValueException("Invalid DateTime value: " + input, e2);
                        }
                    }
                }

                @Override
                public TemporalAccessor parseLiteral(Value<?> input, CoercedVariables variables,
                                                     GraphQLContext context, Locale locale)
                        throws CoercingParseLiteralException {
                    if (input instanceof StringValue sv) {
                        try {
                            return OffsetDateTime.parse(sv.getValue(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        } catch (DateTimeParseException e1) {
                            try {
                                return LocalDateTime.parse(sv.getValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            } catch (DateTimeParseException e2) {
                                throw new CoercingParseLiteralException("Invalid DateTime literal: " + sv.getValue(), e2);
                            }
                        }
                    }
                    throw new CoercingParseLiteralException("Expected StringValue for DateTime");
                }
            })
            .build();
}
