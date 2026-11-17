package com.gordonpn.anime.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
public class MalRetryInterceptor implements ClientHttpRequestInterceptor {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;

    public MalRetryInterceptor(int maxAttempts, long initialBackoffMs, double backoffMultiplier) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long backoffMs = initialBackoffMs;
        boolean infinite = maxAttempts <= 0;

        for (long attempt = 1; infinite || attempt <= maxAttempts; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException("MAL request interrupted");
            }
            try {
                ClientHttpResponse response = execution.execute(request, body);
                HttpStatusCode status = response.getStatusCode();

                if (status.isError()) {
                    // Fail fast on non-transient errors (404, 401, 403, etc.)
                    if (status.value() == 404 || status.value() == 401 || status.value() == 403) {
                        return response;
                    }

                    // For transient errors (e.g., 429, 5xx), retry if attempts remain
                    boolean shouldRetry = infinite || (attempt < maxAttempts);
                    if (shouldRetry && (status.value() == 429 || status.is5xxServerError())) {
                        log.warn("Transient HTTP error {} on attempt {} for URI: {}. Retrying in {}ms...",
                                status.value(), attempt, request.getURI(), backoffMs);
                        response.close(); // Clean up response resources
                        sleep(backoffMs);
                        backoffMs = Math.min(15000L, (long) (backoffMs * backoffMultiplier));
                        continue;
                    }
                }
                // RestClient converts the body after the interceptor returns. Read successful
                // responses here so network failures share this retry budget and classification.
                return status.isError() ? response : bufferResponse(response);
            } catch (IOException e) {
                boolean shouldRetry = infinite || (attempt < maxAttempts);
                if (shouldRetry) {
                    log.warn("IOException on attempt {} for URI: {} - {}. Retrying in {}ms...",
                            attempt, request.getURI(), e.getMessage(), backoffMs);
                    sleep(backoffMs);
                    backoffMs = Math.min(15000L, (long) (backoffMs * backoffMultiplier));
                } else {
                    throw e;
                }
            }
        }
        throw new IOException("Retry limit exceeded for request to " + request.getURI());
    }

    private ClientHttpResponse bufferResponse(ClientHttpResponse response) throws IOException {
        try (response) {
            HttpStatusCode status = response.getStatusCode();
            String statusText = response.getStatusText();
            HttpHeaders headers = new HttpHeaders();
            headers.addAll(response.getHeaders());
            byte[] bytes = response.getBody().readAllBytes();
            return new ClientHttpResponse() {
                @Override
                public HttpStatusCode getStatusCode() { return status; }

                @Override
                public String getStatusText() { return statusText; }

                @Override
                public HttpHeaders getHeaders() { return headers; }

                @Override
                public InputStream getBody() { return new ByteArrayInputStream(bytes); }

                @Override
                public void close() { /* Network response was closed after buffering. */ }
            };
        }
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry wait interrupted", ie);
        }
    }
}
