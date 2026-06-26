package oop.blog.domain;

import java.util.Locale;

public enum BlogSortOption {
    SIM("sim", "정확도순"),
    DATE("date", "최신순");

    private final String queryValue;
    private final String description;

    BlogSortOption(String queryValue, String description) {
        this.queryValue = queryValue;
        this.description = description;
    }

    public static BlogSortOption from(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return DATE;
        }
        return BlogSortOption.valueOf(rawSort.trim().toUpperCase(Locale.ROOT));
    }

    public String getQueryValue() {
        return queryValue;
    }

    public String getDescription() {
        return description;
    }
}
