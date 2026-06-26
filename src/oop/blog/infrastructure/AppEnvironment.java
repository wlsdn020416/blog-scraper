package oop.blog.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppEnvironment {
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private AppEnvironment() {
    }

    public static String get(String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return DOT_ENV.get(name);
    }

    public static String getOrDefault(String name, String defaultValue) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    public static String require(String name) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }

    private static Map<String, String> loadDotEnv() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            return Map.of();
        }

        Map<String, String> values = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int delimiter = trimmed.indexOf('=');
                if (delimiter <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, delimiter).trim();
                String value = trimmed.substring(delimiter + 1).trim();
                values.put(key, stripQuotes(value));
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException(".env 파일을 읽을 수 없습니다.", e);
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
