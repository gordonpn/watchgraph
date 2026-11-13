package com.gordonpn.anime.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import org.springframework.http.HttpHeaders;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MalRetryInterceptorTests {

    @Mock
    private HttpRequest request;

    @Mock
    private ClientHttpRequestExecution execution;

    @Mock
    private ClientHttpResponse mockResponse;

    private List<Long> sleepTimes;
    private int sleepCallCount;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sleepTimes = new ArrayList<>();
        sleepCallCount = 0;
        when(request.getURI()).thenReturn(URI.create("https://api.myanimelist.net/v2/anime/123"));
    }

    private MalRetryInterceptor createInterceptor(int maxAttempts, long initialBackoffMs, double multiplier) {
        return new MalRetryInterceptor(maxAttempts, initialBackoffMs, multiplier) {
            @Override
            protected void sleep(long ms) {
                sleepCallCount++;
                sleepTimes.add(ms);
            }
        };
    }

    @Test
    public void testSuccessFirstAttempt() throws IOException {
        MalRetryInterceptor interceptor = createInterceptor(3, 500, 2.0);
        byte[] body = new byte[0];

        when(mockResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(200));
        when(mockResponse.getStatusText()).thenReturn("OK");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Test", "retained");
        when(mockResponse.getHeaders()).thenReturn(headers);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream("payload".getBytes()));
        when(execution.execute(request, body)).thenReturn(mockResponse);

        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        assertEquals(mockResponse.getStatusCode(), result.getStatusCode());
        verify(execution, times(1)).execute(request, body);
        assertEquals("OK", result.getStatusText());
        assertEquals("retained", result.getHeaders().getFirst("X-Test"));
        assertEquals("payload", new String(result.getBody().readAllBytes()));
        verify(mockResponse).close();
        assertEquals(0, sleepCallCount);
    }

    @Test
    public void testPermanentErrorFailFast404() throws IOException {
        MalRetryInterceptor interceptor = createInterceptor(3, 500, 2.0);
        byte[] body = new byte[0];

        when(mockResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(404));
        when(execution.execute(request, body)).thenReturn(mockResponse);

        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        assertSame(mockResponse, result);
        verify(execution, times(1)).execute(request, body);
        assertEquals(0, sleepCallCount);
    }

    @Test
    public void testTransientErrorRetryThenSuccess() throws IOException {
        MalRetryInterceptor interceptor = createInterceptor(3, 500, 2.0);
        byte[] body = new byte[0];

        ClientHttpResponse transientResponse = mock(ClientHttpResponse.class);
        when(transientResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(429));

        ClientHttpResponse successResponse = mock(ClientHttpResponse.class);
        when(successResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(200));
        when(successResponse.getHeaders()).thenReturn(new HttpHeaders());
        when(successResponse.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        when(execution.execute(request, body))
                .thenReturn(transientResponse)
                .thenReturn(successResponse);

        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        assertEquals(successResponse.getStatusCode(), result.getStatusCode());
        verify(successResponse).close();
        verify(execution, times(2)).execute(request, body);
        verify(transientResponse, times(1)).close();
        assertEquals(1, sleepCallCount);
        assertEquals(500L, sleepTimes.get(0));
    }

    @Test
    public void testTransientErrorExceedMaxAttempts() throws IOException {
        MalRetryInterceptor interceptor = createInterceptor(3, 500, 2.0);
        byte[] body = new byte[0];

        ClientHttpResponse transientResponse1 = mock(ClientHttpResponse.class);
        when(transientResponse1.getStatusCode()).thenReturn(HttpStatusCode.valueOf(503));

        ClientHttpResponse transientResponse2 = mock(ClientHttpResponse.class);
        when(transientResponse2.getStatusCode()).thenReturn(HttpStatusCode.valueOf(503));

        ClientHttpResponse transientResponse3 = mock(ClientHttpResponse.class);
        when(transientResponse3.getStatusCode()).thenReturn(HttpStatusCode.valueOf(503));

        when(execution.execute(request, body))
                .thenReturn(transientResponse1)
                .thenReturn(transientResponse2)
                .thenReturn(transientResponse3);

        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        assertSame(transientResponse3, result);
        verify(execution, times(3)).execute(request, body);
        verify(transientResponse1, times(1)).close();
        verify(transientResponse2, times(1)).close();
        verify(transientResponse3, never()).close();
        assertEquals(2, sleepCallCount);
        assertEquals(500L, sleepTimes.get(0));
        assertEquals(1000L, sleepTimes.get(1));
    }

    @Test
    public void testIOExceptionRetriesThenThrows() throws IOException {
        MalRetryInterceptor interceptor = createInterceptor(3, 500, 2.0);
        byte[] body = new byte[0];

        when(execution.execute(request, body))
                .thenThrow(new IOException("Timeout 1"))
                .thenThrow(new IOException("Timeout 2"))
                .thenThrow(new IOException("Timeout 3"));

        IOException thrown = assertThrows(IOException.class, () -> {
            interceptor.intercept(request, body, execution);
        });

        assertEquals("Timeout 3", thrown.getMessage());
        verify(execution, times(3)).execute(request, body);
        assertEquals(2, sleepCallCount);
        assertEquals(500L, sleepTimes.get(0));
        assertEquals(1000L, sleepTimes.get(1));
    }
}
