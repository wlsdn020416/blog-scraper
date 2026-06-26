package oop.blog.application;

import oop.blog.domain.BlogSortOption;

public record BlogSearchCommand(
        String searchQuery,
        int limit,
        BlogSortOption sortOption
) {
    public BlogSearchCommand {
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("검색 건수는 1부터 100 사이여야 합니다.");
        }
        if (sortOption == null) {
            sortOption = BlogSortOption.DATE;
        }
    }
}
