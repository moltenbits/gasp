package com.moltenbits.gasp.jooq;

import com.moltenbits.gasp.runtime.ComposableQuery;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.RecordMapper;

/**
 * A composable query backed by jOOQ. Supports dynamic association composition
 * via MULTISET subqueries — nested collections are fetched in a single SQL query.
 *
 * <p>Usage:
 * <pre>
 * JooqComposableQuery.list(dsl, BOOK, BOOK.ID, BOOK.TITLE, BOOK.GENRE)
 *     .where(BOOK.GENRE.eq("FANTASY"))
 *     .withMultiset("authors",
 *         select(AUTHOR.ID, AUTHOR.NAME).from(AUTHOR)
 *             .join(BOOK_AUTHOR).on(BOOK_AUTHOR.AUTHOR_ID.eq(AUTHOR.ID))
 *             .where(BOOK_AUTHOR.BOOK_ID.eq(BOOK.ID)))
 *     .mapper(r -> new Book(r.get(BOOK.ID), r.get(BOOK.TITLE), ...))
 * </pre>
 *
 * @param <T> the POJO type this query resolves to
 */
public class JooqComposableQuery<T> implements ComposableQuery<T> {

    private final DSLContext dsl;
    private final Table<?> table;
    private final List<SelectField<?>> baseFields;
    private final List<Condition> conditions;
    private final List<MultisetAssociation> multisets;
    private final org.jooq.RecordMapper<Record, T> mapper;
    private final boolean isList;

    private JooqComposableQuery(DSLContext dsl, Table<?> table, List<SelectField<?>> baseFields,
                                 List<Condition> conditions, List<MultisetAssociation> multisets,
                                 org.jooq.RecordMapper<Record, T> mapper, boolean isList) {
        this.dsl = dsl;
        this.table = table;
        this.baseFields = baseFields;
        this.conditions = conditions;
        this.multisets = multisets;
        this.mapper = mapper;
        this.isList = isList;
    }

    /**
     * Create a list query. The type parameter T is the GraphQL type (e.g. Book POJO)
     * used for SDL generation. At runtime, results are jOOQ Records.
     * Returns JooqComposableQuery&lt;List&lt;T&gt;&gt; for list return types.
     */
    @SuppressWarnings("unchecked")
    public static <T> JooqComposableQuery<List<T>> listOf(DSLContext dsl, Table<?> table, SelectField<?>... fields) {
        return (JooqComposableQuery<List<T>>) (JooqComposableQuery<?>) new JooqComposableQuery<>(dsl, table, Arrays.asList(fields),
                new ArrayList<>(), new ArrayList<>(), null, true);
    }

    /**
     * Create a single-result query. The type parameter T is the GraphQL type (e.g. Book POJO)
     * used for SDL generation. At runtime, the result is a jOOQ Record.
     */
    public static <T> JooqComposableQuery<T> oneOf(DSLContext dsl, Table<?> table, SelectField<?>... fields) {
        return new JooqComposableQuery<>(dsl, table, Arrays.asList(fields),
                new ArrayList<>(), new ArrayList<>(), null, false);
    }

    /**
     * Create a raw list query (internal use).
     */
    public static <T> JooqComposableQuery<T> list(DSLContext dsl, Table<?> table, SelectField<?>... fields) {
        return new JooqComposableQuery<>(dsl, table, Arrays.asList(fields),
                new ArrayList<>(), new ArrayList<>(), null, true);
    }

    /**
     * Create a raw single-result query (internal use).
     */
    public static <T> JooqComposableQuery<T> one(DSLContext dsl, Table<?> table, SelectField<?>... fields) {
        return new JooqComposableQuery<>(dsl, table, Arrays.asList(fields),
                new ArrayList<>(), new ArrayList<>(), null, false);
    }

    /**
     * Add a WHERE condition.
     */
    public JooqComposableQuery<T> where(Condition condition) {
        var newConditions = new ArrayList<>(conditions);
        newConditions.add(condition);
        return new JooqComposableQuery<>(dsl, table, baseFields, newConditions, multisets, mapper, isList);
    }

    /**
     * Add a MULTISET subquery for a nested association. The subquery is correlated
     * to the parent table and produces a nested collection in the result set.
     */
    public JooqComposableQuery<T> withMultiset(String fieldName, org.jooq.Select<?> subquery) {
        var newMultisets = new ArrayList<>(multisets);
        newMultisets.add(new MultisetAssociation(fieldName, subquery));
        return new JooqComposableQuery<>(dsl, table, baseFields, conditions, newMultisets, mapper, isList);
    }

    /**
     * Set the record-to-POJO mapper. The mapper receives a Record that contains
     * the base fields plus any MULTISET fields (accessible via field name).
     */
    public JooqComposableQuery<T> mapper(org.jooq.RecordMapper<Record, T> mapper) {
        return new JooqComposableQuery<>(dsl, table, baseFields, conditions, multisets, mapper, isList);
    }

    /**
     * Set a mapper that produces Maps for graphql-java's PropertyDataFetcher.
     * The type parameter T is preserved for SDL generation but the runtime
     * objects are Maps that graphql-java resolves fields from.
     */
    @SuppressWarnings("unchecked")
    public JooqComposableQuery<T> mapToGraph(org.jooq.RecordMapper<Record, java.util.Map<String, Object>> mapper) {
        return new JooqComposableQuery<>(dsl, table, baseFields, conditions, multisets,
                (org.jooq.RecordMapper<Record, T>) (org.jooq.RecordMapper<?, ?>) mapper, isList);
    }

    @Override
    public boolean isList() {
        return isList;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<T> fetchAll() {
        var allFields = buildSelectFields();
        var query = dsl.select(allFields).from(table);
        var result = !conditions.isEmpty()
                ? query.where(DSL.and(conditions))
                : query;
        if (mapper != null) {
            return result.fetch(mapper);
        }
        return (List<T>) result.fetch();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T fetchOne() {
        var allFields = buildSelectFields();
        var query = dsl.select(allFields).from(table);
        var result = !conditions.isEmpty()
                ? query.where(DSL.and(conditions))
                : query;
        if (mapper != null) {
            return result.fetchOne(mapper);
        }
        return (T) result.fetchOne();
    }

    private List<SelectField<?>> buildSelectFields() {
        var allFields = new ArrayList<SelectField<?>>(baseFields);
        for (var ms : multisets) {
            allFields.add(DSL.multiset(ms.subquery()).as(ms.fieldName()));
        }
        return allFields;
    }

    // --- Accessors for framework composition ---

    public DSLContext dsl() { return dsl; }
    public Table<?> table() { return table; }
    public List<Condition> conditions() { return List.copyOf(conditions); }
    public List<MultisetAssociation> multisets() { return List.copyOf(multisets); }

    public record MultisetAssociation(String fieldName, org.jooq.Select<?> subquery) {}
}
