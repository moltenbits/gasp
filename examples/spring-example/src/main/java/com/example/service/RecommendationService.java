package com.example.service;

import com.example.model.*;
import com.moltenbits.gasp.annotation.GraphQLField;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RecommendationService {

    @GraphQLField(on = Book.class)
    public List<Book> recommendations(Object source) {
        Book book = (Book) source;
        if (book.getGenre() == Genre.FANTASY) {
            return List.of(new Book(100L, "The Name of the Wind", "A fantasy classic", new Author(100L, "Patrick Rothfuss"), Genre.FANTASY));
        }
        return List.of();
    }
}
