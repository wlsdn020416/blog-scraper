package oop.blog.presentation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import oop.blog.application.BlogService;
import oop.blog.domain.BlogPost;
import oop.blog.domain.BlogSortOption;
import oop.blog.infrastructure.AppEnvironment;
import oop.blog.infrastructure.NaverBlogProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class BlogApiServer {
    private static final Duration IMAGE_FETCH_TIMEOUT = Duration.ofSeconds(5);

    private final BlogService blogService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public BlogApiServer(BlogService blogService) {
        this.blogService = blogService;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/blogs/refresh", this::handleRefresh);
        server.createContext("/api/blogs/image", this::handleBlogImage);
        server.createContext("/api/images", this::handleImageProxy);
        server.createContext("/", this::handleStaticFile);
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
        System.out.println("Blog API server started: http://localhost:" + port);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method Not Allowed\"}");
            return;
        }
        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleRefresh(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
                && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI());
            String query = params.getOrDefault("query", "");
            int limit = parseLimit(params.getOrDefault("limit", "10"));
            int start = parseStart(params.getOrDefault("start", "1"));
            BlogSortOption sortOption = BlogSortOption.from(params.get("sort"));
            List<BlogPost> posts = blogService.refreshPosts(query, limit, start, sortOption);
            sendJson(exchange, 200, toPostsResponse(query, sortOption, start, limit, posts));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (IllegalStateException e) {
            sendJson(exchange, 500, "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleBlogImage(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI());
            String postUrl = params.getOrDefault("url", "");
            String imageUrl = blogService.findImageUrl(postUrl);
            sendJson(exchange, 200, "{\"imageUrl\":\"" + escapeJson(imageUrl) + "\"}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (IllegalStateException e) {
            sendJson(exchange, 500, "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleImageProxy(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method Not Allowed\"}");
            return;
        }

        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI());
            String imageUrl = params.getOrDefault("url", "");
            URI imageUri = URI.create(imageUrl);
            if (!isAllowedImageUri(imageUri)) {
                sendText(exchange, 400, "text/plain; charset=UTF-8", "Invalid image URL");
                return;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(imageUri)
                    .timeout(IMAGE_FETCH_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://blog.naver.com/")
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                sendText(exchange, 502, "text/plain; charset=UTF-8", "Image fetch failed");
                return;
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .filter(type -> type.toLowerCase().startsWith("image/"))
                    .orElse("image/jpeg");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            sendBytes(exchange, 200, contentType, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendText(exchange, 500, "text/plain; charset=UTF-8", "Image fetch interrupted");
        } catch (Exception e) {
            sendText(exchange, 400, "text/plain; charset=UTF-8", "Image fetch failed");
        }
    }

    private void handleStaticFile(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        boolean headRequest = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !headRequest) {
            sendJson(exchange, 405, "{\"message\":\"Method Not Allowed\"}");
            return;
        }

        Path publicRoot = Paths.get("public").toAbsolutePath().normalize();
        String requestPath = exchange.getRequestURI().getPath();
        String fileName = "/".equals(requestPath) ? "/index.html" : requestPath;
        if ("/favicon.ico".equals(fileName)) {
            fileName = "/favicon.png";
        }
        Path filePath = publicRoot.resolve(fileName.substring(1)).normalize();

        if (!filePath.startsWith(publicRoot) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendText(exchange, 404, "text/plain; charset=UTF-8", "Not Found");
            return;
        }

        byte[] bytes = Files.readAllBytes(filePath);
        if (headRequest) {
            addCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", contentType(filePath));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        sendBytes(exchange, 200, contentType(filePath), bytes);
    }

    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int delimiter = pair.indexOf('=');
            String key = delimiter >= 0 ? pair.substring(0, delimiter) : pair;
            String value = delimiter >= 0 ? pair.substring(delimiter + 1) : "";
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean isAllowedImageUri(URI imageUri) {
        String scheme = imageUri.getScheme();
        String host = imageUri.getHost();
        if (scheme == null || host == null) {
            return false;
        }
        boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        boolean naverImageHost = host.endsWith("pstatic.net")
                || host.endsWith("naver.net")
                || host.endsWith("naver.com");
        return httpScheme && naverImageHost;
    }

    private int parseLimit(String rawLimit) {
        try {
            return Integer.parseInt(rawLimit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("검색 건수는 숫자로 입력해주세요.");
        }
    }

    private int parseStart(String rawStart) {
        try {
            return Integer.parseInt(rawStart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("검색 시작 위치는 숫자로 입력해주세요.");
        }
    }

    private String toPostsResponse(String query, BlogSortOption sortOption, int start, int limit, List<BlogPost> posts) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"query\":\"").append(escapeJson(query)).append("\",");
        json.append("\"sort\":\"").append(sortOption.name()).append("\",");
        json.append("\"start\":").append(start).append(",");
        json.append("\"limit\":").append(limit).append(",");
        json.append("\"nextStart\":").append(start + limit).append(",");
        json.append("\"count\":").append(posts.size()).append(",");
        json.append("\"items\":[");
        for (int i = 0; i < posts.size(); i++) {
            BlogPost post = posts.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"title\":\"").append(escapeJson(post.title())).append("\",");
            json.append("\"link\":\"").append(escapeJson(post.link())).append("\",");
            json.append("\"description\":\"").append(escapeJson(post.description())).append("\",");
            json.append("\"bloggerName\":\"").append(escapeJson(post.bloggerName())).append("\",");
            json.append("\"bloggerLink\":\"").append(escapeJson(post.bloggerLink())).append("\",");
            json.append("\"postDate\":\"").append(escapeJson(post.postDate())).append("\",");
            json.append("\"imageUrl\":\"").append(escapeJson(post.imageUrl())).append("\"");
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        addCorsHeaders(exchange);
        sendBytes(exchange, statusCode, "application/json; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private void sendText(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        sendBytes(exchange, statusCode, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void sendBytes(HttpExchange exchange, int statusCode, String contentType, byte[] bytes) throws IOException {
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String contentType(Path filePath) {
        String fileName = filePath.getFileName().toString();
        if (fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(AppEnvironment.getOrDefault("PORT", "8080"));
        BlogService blogService = new BlogService(new NaverBlogProvider());
        new BlogApiServer(blogService).start(port);
    }
}
