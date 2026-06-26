package oop.blog.infrastructure;

import java.net.http.HttpClient;

public abstract class AbstractHttpClient {
    protected final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    protected final String endpoint;

    protected AbstractHttpClient(String endpoint) {
        this.endpoint = endpoint;
    }
}
