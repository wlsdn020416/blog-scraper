package oop.blog.infrastructure;

import oop.blog.application.BlogProvider;
import oop.blog.domain.BlogPost;
import oop.blog.domain.BlogSortOption;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NaverBlogProvider extends AbstractHttpClient implements BlogProvider {
    private static final String BLOG_API_URL = "https://openapi.naver.com/v1/search/blog.json";

    private final String clientId;
    private final String clientSecret;

    public NaverBlogProvider() {
        super(BLOG_API_URL);
        this.clientId = requireEnv("NAVER_CLIENT_ID");
        this.clientSecret = requireEnv("NAVER_CLIENT_SECRET");
    }

    @Override
    public List<BlogPost> fetchPosts(String searchQuery, int limit, BlogSortOption sortOption) {
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("검색 건수는 1부터 100 사이여야 합니다.");
        }

        String url = endpoint + "?query="
                + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8)
                + "&display=" + limit
                + "&sort=" + sortOption.getQueryValue()
                + "&start=1";

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            String body = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Naver Blog API 요청 실패: HTTP " + response.statusCode() + " - " + body);
            }
            return parseBlogPosts(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("블로그 검색 중 요청이 중단되었습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("블로그 검색 중 오류가 발생했습니다.", e);
        }
    }

    List<BlogPost> parseBlogPosts(String body) {
        List<BlogPost> posts = new ArrayList<>();
        for (String item : extractItemObjects(body)) {
            posts.add(new BlogPost(
                    cleanNaverText(getJsonString(item, "title")),
                    getJsonString(item, "link"),
                    cleanNaverText(getJsonString(item, "description")),
                    cleanNaverText(getJsonString(item, "bloggername")),
                    getJsonString(item, "bloggerlink"),
                    getJsonString(item, "postdate")
            ));
        }
        return posts;
    }

    private List<String> extractItemObjects(String body) {
        int itemsKey = body.indexOf("\"items\"");
        if (itemsKey < 0) {
            return List.of();
        }
        int arrayStart = body.indexOf('[', itemsKey);
        if (arrayStart < 0) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int objectDepth = 0;
        int objectStart = -1;

        for (int i = arrayStart + 1; i < body.length(); i++) {
            char current = body.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                if (objectDepth == 0) {
                    objectStart = i;
                }
                objectDepth++;
            } else if (current == '}') {
                objectDepth--;
                if (objectDepth == 0 && objectStart >= 0) {
                    items.add(body.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            } else if (current == ']' && objectDepth == 0) {
                break;
            }
        }
        return items;
    }

    private String getJsonString(String object, String key) {
        String keyToken = "\"" + key + "\"";
        int keyIndex = object.indexOf(keyToken);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = object.indexOf(':', keyIndex + keyToken.length());
        if (colonIndex < 0) {
            return "";
        }
        int valueStart = object.indexOf('"', colonIndex + 1);
        if (valueStart < 0) {
            return "";
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart + 1; i < object.length(); i++) {
            char current = object.charAt(i);
            if (escaped) {
                value.append(unescape(current));
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return value.toString();
            } else {
                value.append(current);
            }
        }
        return value.toString();
    }

    private char unescape(char current) {
        return switch (current) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> current;
        };
    }

    private String cleanNaverText(String value) {
        return value
                .replace("<b>", "")
                .replace("</b>", "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
