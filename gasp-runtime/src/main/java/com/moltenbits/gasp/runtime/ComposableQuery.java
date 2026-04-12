package com.moltenbits.gasp.runtime;

import java.util.List;

/**
 * A composable query object. Fetcher methods that return a ComposableQuery
 * signal to the framework that the query should be executed by the framework
 * rather than treated as a final result.
 *
 * <p>Backend modules provide concrete implementations:
 * <ul>
 *   <li>{@code gasp-jooq} → {@code JooqComposableQuery<T>} backed by jOOQ's query builder</li>
 * </ul>
 *
 * <p>The framework calls {@link #fetchAll()} for list queries or {@link #fetchOne()}
 * for single-result queries based on the GraphQL return type.
 *
 * @param <T> the entity type this query resolves to
 */
public interface ComposableQuery<T> {

    /**
     * Whether this query returns a list of results or a single result.
     */
    boolean isList();

    /**
     * Execute the query and return all matching results.
     */
    List<T> fetchAll();

    /**
     * Execute the query and return a single result, or null if not found.
     */
    T fetchOne();
}
