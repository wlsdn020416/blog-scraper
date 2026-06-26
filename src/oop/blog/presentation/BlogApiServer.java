package oop.blog.presentation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import oop.blog.application.BlogService;
import oop.blog.domain.BlogPost;
import oop.blog.domain.BlogSortOption;
import oop.blog.infrastructure.NaverBlogProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlogApiServer {
    private final BlogService blogService;

    public BlogApiServer(BlogService blogService) {
        this.blogService = blogService;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/blogs/refresh", this::handleRefresh);
        server.createContext("/", this::handleStaticFile);
        server.setExecutor(null);
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
            BlogSortOption sortOption = BlogSortOption.from(params.get("sort"));
            List<BlogPost> posts = blogService.refreshPosts(query, limit, sortOption);
            sendJson(exchange, 200, toPostsResponse(query, sortOption, posts));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (IllegalStateException e) {
            sendJson(exchange, 500, "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleStaticFile(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method Not Allowed\"}");
            return;
        }

        Path publicRoot = Paths.get("public").toAbsolutePath().normalize();
        String requestPath = exchange.getRequestURI().getPath();
        String fileName = "/".equals(requestPath) ? "/index.html" : requestPath;
        Path filePath = publicRoot.resolve(fileName.substring(1)).normalize();

        if (!filePath.startsWith(publicRoot) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendText(exchange, 404, "text/plain; charset=UTF-8", "Not Found");
            return;
        }

        byte[] bytes = Files.readAllBytes(filePath);
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

    private int parseLimit(String rawLimit) {
        try {
            return Integer.parseInt(rawLimit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("검색 건수는 숫자로 입력해주세요.");
        }
    }

    private String toPostsResponse(String query, BlogSortOption sortOption, List<BlogPost> posts) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"query\":\"").append(escapeJson(query)).append("\",");
        json.append("\"sort\":\"").append(sortOption.name()).append("\",");
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
            json.append("\"postDate\":\"").append(escapeJson(post.postDate())).append("\"");
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
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        BlogService blogService = new BlogService(new NaverBlogProvider());
        new BlogApiServer(blogService).start(port);
    }
}
