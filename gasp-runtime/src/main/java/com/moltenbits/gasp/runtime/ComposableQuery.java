package com.moltenbits.gasp.runtime;

/**
 * Marker interface for composable query objects. Fetcher methods that return
 * a ComposableQuery signal to the framework that the query should be composed
 * across the GraphQL type hierarchy and executed once, rather than being
 * treated as a final result.
 *
 * @param <T> the entity type this query resolves to
 */
public interface ComposableQuery<T> {
}
