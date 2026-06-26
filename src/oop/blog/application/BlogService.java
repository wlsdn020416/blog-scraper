package oop.blog.application;

import oop.blog.domain.BlogPost;
import oop.blog.domain.BlogSortOption;

import java.util.List;
import java.util.Objects;

public class BlogService {
    private final BlogProvider blogProvider;

    public BlogService(BlogProvider blogProvider) {
        this.blogProvider = Objects.requireNonNull(blogProvider, "blogProvider must not be null");
    }

    public List<BlogPost> refreshPosts(String searchQuery, int limit, int start, BlogSortOption sortOption) {
        BlogSearchCommand command = new BlogSearchCommand(searchQuery, limit, start, sortOption);
        return blogProvider.fetchPosts(command.searchQuery(), command.limit(), command.start(), command.sortOption());
    }
}
