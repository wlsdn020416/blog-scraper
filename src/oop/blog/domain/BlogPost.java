package oop.blog.domain;

public record BlogPost(
        String title,
        String link,
        String description,
        String bloggerName,
        String bloggerLink,
        String postDate
) {
}
