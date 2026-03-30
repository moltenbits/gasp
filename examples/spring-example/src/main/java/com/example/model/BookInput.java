package com.example.model;
import com.moltenbits.gasp.annotation.GraphQLInputType;
@GraphQLInputType
public class BookInput {
    private String title;
    private String authorName;
    private Genre genre;
    public BookInput() {}
    public BookInput(String title, String authorName, Genre genre) { this.title = title; this.authorName = authorName; this.genre = genre; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public Genre getGenre() { return genre; }
    public void setGenre(Genre genre) { this.genre = genre; }
}
