package oop.blog.infrastructure;

import java.net.http.HttpClient;

public abstract class AbstractHttpClient {
    protected final HttpClient httpClient = HttpClient.newHttpClient();
    protected final String endpoint;

    protected AbstractHttpClient(String endpoint) {
        this.endpoint = endpoint;
    }
}
