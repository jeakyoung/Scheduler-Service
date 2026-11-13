package com.scheduler.infra.http;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Generalizes the legacy executeTask2() HttpURLConnection + custom-header POST pattern into
 * an arbitrary URL/method/headers/body caller (PLAN.md §5, §8) - FCM is just one possible
 * caller of this, never hardcoded here.
 */
@Component
public class GenericHttpClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpCallResult call(String method, String url, Map<String, String> headers, String body) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            headers.forEach(requestBuilder::header);
            requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpCallResult(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new HttpCallException("HTTP call to " + url + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpCallException("HTTP call to " + url + " was interrupted", e);
        }
    }
}
