CREATE TABLE author (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE book (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(50) NOT NULL
);

CREATE TABLE book_author (
    book_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, author_id),
    CONSTRAINT fk_ba_book FOREIGN KEY (book_id) REFERENCES book(id),
    CONSTRAINT fk_ba_author FOREIGN KEY (author_id) REFERENCES author(id)
);

CREATE TABLE review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    reviewer_name VARCHAR(255) NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000),
    CONSTRAINT fk_review_book FOREIGN KEY (book_id) REFERENCES book(id)
);
