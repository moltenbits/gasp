package com.example.service;

import com.example.jooq.tables.pojos.Author;
import com.example.jooq.tables.pojos.Book;
import com.example.jooq.tables.pojos.Review;
import com.moltenbits.gasp.annotation.GraphQLApi;
import com.moltenbits.gasp.annotation.GraphQLQuery;
import com.moltenbits.gasp.jooq.JooqComposableQuery;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.jooq.tables.Author.AUTHOR;
import static com.example.jooq.tables.Book.BOOK;
import static com.example.jooq.tables.BookAuthor.BOOK_AUTHOR;
import static com.example.jooq.tables.Review.REVIEW;
import static org.jooq.impl.DSL.select;

@GraphQLApi
public class BookService {

    private final DSLContext dsl;

    public BookService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @SuppressWarnings("unchecked")
    @GraphQLQuery
    public JooqComposableQuery<List<Book>> books() {
        return (JooqComposableQuery<List<Book>>) (JooqComposableQuery<?>)
                withAssociations(JooqComposableQuery.<Book>list(dsl, BOOK,
                        BOOK.ID, BOOK.TITLE, BOOK.ISBN, BOOK.DESCRIPTION, BOOK.GENRE));
    }

    @GraphQLQuery
    public JooqComposableQuery<Book> book(Long id) {
        return withAssociations(JooqComposableQuery.<Book>one(dsl, BOOK,
                BOOK.ID, BOOK.TITLE, BOOK.ISBN, BOOK.DESCRIPTION, BOOK.GENRE))
                .where(BOOK.ID.eq(id));
    }

    @SuppressWarnings("unchecked")
    @GraphQLQuery
    public JooqComposableQuery<List<Book>> booksByGenre(String genre) {
        return (JooqComposableQuery<List<Book>>) (JooqComposableQuery<?>)
                withAssociations(JooqComposableQuery.<Book>list(dsl, BOOK,
                        BOOK.ID, BOOK.TITLE, BOOK.ISBN, BOOK.DESCRIPTION, BOOK.GENRE))
                        .where(BOOK.GENRE.eq(genre));
    }

    private JooqComposableQuery<Book> withAssociations(JooqComposableQuery<Book> query) {
        return query
                .withMultiset("authors",
                        select(AUTHOR.ID, AUTHOR.NAME)
                                .from(AUTHOR)
                                .join(BOOK_AUTHOR).on(BOOK_AUTHOR.AUTHOR_ID.eq(AUTHOR.ID))
                                .where(BOOK_AUTHOR.BOOK_ID.eq(BOOK.ID)))
                .withMultiset("reviews",
                        select(REVIEW.ID, REVIEW.REVIEWER_NAME, REVIEW.RATING, REVIEW.COMMENT)
                                .from(REVIEW)
                                .where(REVIEW.BOOK_ID.eq(BOOK.ID)))
                .mapToGraph(BookService::mapBook);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapBook(Record r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.get(BOOK.ID));
        map.put("title", r.get(BOOK.TITLE));
        map.put("isbn", r.get(BOOK.ISBN));
        map.put("description", r.get(BOOK.DESCRIPTION));
        map.put("genre", r.get(BOOK.GENRE));

        Result<?> authorRecords = r.get("authors", Result.class);
        if (authorRecords != null) {
            map.put("authors", authorRecords.map(ar -> {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", ar.get(0, Long.class));
                a.put("name", ar.get(1, String.class));
                return a;
            }));
        }

        Result<?> reviewRecords = r.get("reviews", Result.class);
        if (reviewRecords != null) {
            map.put("reviews", reviewRecords.map(rr -> {
                Map<String, Object> rv = new LinkedHashMap<>();
                rv.put("id", rr.get(0, Long.class));
                rv.put("reviewerName", rr.get(1, String.class));
                rv.put("rating", rr.get(2, Integer.class));
                rv.put("comment", rr.get(3, String.class));
                return rv;
            }));
        }

        return map;
    }
}
