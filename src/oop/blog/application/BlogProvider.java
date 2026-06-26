package oop.blog.application;

import oop.blog.domain.BlogPost;
import oop.blog.domain.BlogSortOption;

import java.util.List;

public interface BlogProvider {
    List<BlogPost> fetchPosts(String searchQuery, int limit, BlogSortOption sortOption);
}
