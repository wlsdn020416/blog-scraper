package oop.blog.infrastructure;

import oop.blog.application.BlogProvider;
import oop.blog.domain.BlogPost;
import oop.blog.domain.BlogSortOption;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class NaverBlogProvider extends AbstractHttpClient implements BlogProvider {
    private static final String BLOG_API_URL = "https://openapi.naver.com/v1/search/blog.json";
    private static final Duration PAGE_FETCH_TIMEOUT = Duration.ofSeconds(4);

    private final String clientId;
    private final String clientSecret;

    public NaverBlogProvider() {
        super(BLOG_API_URL);
        this.clientId = AppEnvironment.require("NAVER_CLIENT_ID");
        this.clientSecret = AppEnvironment.require("NAVER_CLIENT_SECRET");
    }

    @Override
    public List<BlogPost> fetchPosts(String searchQuery, int limit, int start, BlogSortOption sortOption) {
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("검색 건수는 1부터 100 사이여야 합니다.");
        }
        if (start <= 0 || start > 1000) {
            throw new IllegalArgumentException("검색 시작 위치는 1부터 1000 사이여야 합니다.");
        }

        String url = endpoint + "?query="
                + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8)
                + "&display=" + limit
                + "&sort=" + sortOption.getQueryValue()
                + "&start=" + start;

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
            String link = getJsonString(item, "link");
            posts.add(new BlogPost(
                    cleanNaverText(getJsonString(item, "title")),
                    link,
                    cleanNaverText(getJsonString(item, "description")),
                    cleanNaverText(getJsonString(item, "bloggername")),
                    getJsonString(item, "bloggerlink"),
                    getJsonString(item, "postdate"),
                    ""
            ));
        }
        return posts;
    }

    @Override
    public String fetchImageUrl(String postUrl) {
        return findImageUrl(postUrl);
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

    private String findImageUrl(String postUrl) {
        if (postUrl == null || postUrl.isBlank()) {
            return "";
        }

        String imageUrl = fetchImageUrlFromPage(postUrl);
        if (!imageUrl.isBlank()) {
            return imageUrl;
        }

        String mobileUrl = toMobileBlogUrl(postUrl);
        if (!mobileUrl.equals(postUrl)) {
            return fetchImageUrlFromPage(mobileUrl);
        }

        return "";
    }

    private String fetchImageUrlFromPage(String postUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(postUrl))
                    .timeout(PAGE_FETCH_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            return extractRepresentativeImage(response.body(), postUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String extractRepresentativeImage(String html, String baseUrl) {
        String ogImage = extractMetaContent(html, "property", "og:image");
        if (!ogImage.isBlank()) {
            return resolveUrl(baseUrl, cleanHtmlText(ogImage));
        }

        String twitterImage = extractMetaContent(html, "name", "twitter:image");
        if (!twitterImage.isBlank()) {
            return resolveUrl(baseUrl, cleanHtmlText(twitterImage));
        }

        String firstImage = extractFirstImageSrc(html);
        if (!firstImage.isBlank()) {
            return resolveUrl(baseUrl, cleanHtmlText(firstImage));
        }

        String encodedImage = extractEncodedImageUrl(html);
        if (!encodedImage.isBlank()) {
            return resolveUrl(baseUrl, cleanHtmlText(encodedImage));
        }

        return "";
    }

    private String extractMetaContent(String html, String keyAttribute, String keyValue) {
        String lowerHtml = html.toLowerCase();
        int cursor = 0;
        while (cursor >= 0 && cursor < html.length()) {
            int metaStart = lowerHtml.indexOf("<meta", cursor);
            if (metaStart < 0) {
                return "";
            }
            int metaEnd = lowerHtml.indexOf(">", metaStart);
            if (metaEnd < 0) {
                return "";
            }
            String tag = html.substring(metaStart, metaEnd + 1);
            if (keyValue.equalsIgnoreCase(extractAttribute(tag, keyAttribute))) {
                return extractAttribute(tag, "content");
            }
            cursor = metaEnd + 1;
        }
        return "";
    }

    private String extractFirstImageSrc(String html) {
        String lowerHtml = html.toLowerCase();
        int cursor = 0;
        while (cursor >= 0 && cursor < html.length()) {
            int imgStart = lowerHtml.indexOf("<img", cursor);
            if (imgStart < 0) {
                return "";
            }
            int imgEnd = lowerHtml.indexOf(">", imgStart);
            if (imgEnd < 0) {
                return "";
            }
            String tag = html.substring(imgStart, imgEnd + 1);
            for (String attribute : List.of("src", "data-src", "data-lazy-src", "data-original", "data-url")) {
                String src = extractAttribute(tag, attribute);
                if (isUsableImageUrl(src)) {
                    return src;
                }
            }
            cursor = imgEnd + 1;
        }
        return "";
    }

    private String extractEncodedImageUrl(String html) {
        for (String marker : List.of("https://blogthumb.pstatic.net", "https://postfiles.pstatic.net", "https://blogfiles.pstatic.net")) {
            String imageUrl = extractImageUrlAfterMarker(html, marker);
            if (!imageUrl.isBlank()) {
                return imageUrl;
            }
        }
        return "";
    }

    private String extractImageUrlAfterMarker(String html, String marker) {
        int start = html.indexOf(marker);
        if (start < 0) {
            return "";
        }

        int end = start;
        while (end < html.length()) {
            char current = html.charAt(end);
            if (Character.isWhitespace(current) || current == '"' || current == '\'' || current == '<' || current == '\\') {
                break;
            }
            end++;
        }

        String imageUrl = html.substring(start, end);
        if (isUsableImageUrl(imageUrl)) {
            return imageUrl;
        }
        return "";
    }

    private String extractAttribute(String tag, String attributeName) {
        String lowerTag = tag.toLowerCase();
        String lowerAttribute = attributeName.toLowerCase();
        int nameStart = lowerTag.indexOf(lowerAttribute);
        while (nameStart >= 0) {
            int nameEnd = nameStart + lowerAttribute.length();
            boolean validStart = nameStart == 0 || !Character.isLetterOrDigit(lowerTag.charAt(nameStart - 1));
            boolean validEnd = nameEnd >= lowerTag.length() || !Character.isLetterOrDigit(lowerTag.charAt(nameEnd));
            if (validStart && validEnd) {
                int equalsIndex = lowerTag.indexOf('=', nameEnd);
                if (equalsIndex >= 0) {
                    int valueStart = equalsIndex + 1;
                    while (valueStart < tag.length() && Character.isWhitespace(tag.charAt(valueStart))) {
                        valueStart++;
                    }
                    if (valueStart < tag.length()) {
                        char quote = tag.charAt(valueStart);
                        if (quote == '"' || quote == '\'') {
                            int valueEnd = tag.indexOf(quote, valueStart + 1);
                            if (valueEnd >= 0) {
                                return tag.substring(valueStart + 1, valueEnd);
                            }
                        } else {
                            int valueEnd = valueStart;
                            while (valueEnd < tag.length()
                                    && !Character.isWhitespace(tag.charAt(valueEnd))
                                    && tag.charAt(valueEnd) != '>') {
                                valueEnd++;
                            }
                            return tag.substring(valueStart, valueEnd);
                        }
                    }
                }
            }
            nameStart = lowerTag.indexOf(lowerAttribute, nameStart + lowerAttribute.length());
        }
        return "";
    }

    private String resolveUrl(String baseUrl, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "";
        }
        try {
            return URI.create(baseUrl).resolve(imageUrl).toString();
        } catch (Exception e) {
            return imageUrl;
        }
    }

    private boolean isUsableImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }

        String normalized = imageUrl.trim().toLowerCase();
        return !normalized.startsWith("data:")
                && !normalized.endsWith(".gif")
                && !normalized.contains("blank.gif")
                && !normalized.contains("spacer")
                && (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("//")
                || normalized.startsWith("/"));
    }

    private String toMobileBlogUrl(String postUrl) {
        try {
            URI uri = URI.create(postUrl);
            if (!"blog.naver.com".equalsIgnoreCase(uri.getHost())) {
                return postUrl;
            }

            String[] parts = uri.getPath().split("/");
            if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
                return postUrl;
            }

            return "https://m.blog.naver.com/" + parts[1] + "/" + parts[2];
        } catch (Exception e) {
            return postUrl;
        }
    }

    private String cleanHtmlText(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
