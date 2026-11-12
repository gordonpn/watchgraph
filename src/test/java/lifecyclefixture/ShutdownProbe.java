package lifecyclefixture;

import com.gordonpn.anime.config.AuthorizationState;
import com.gordonpn.anime.models.dto.TokenResponse;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Observes the packaged application's real lifecycle; no production lifecycle replacement. */
@org.springframework.context.annotation.Configuration(value = "shutdownProbe", proxyBeanMethods = false)
public class ShutdownProbe implements org.springframework.beans.factory.config.BeanFactoryPostProcessor, BeanPostProcessor, DisposableBean {
    private final AtomicInteger connections = new AtomicInteger();
    private EntityManagerFactory entityManagerFactory;
    private ThreadPoolTaskScheduler scheduler;

    @Override
    public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory factory) {
        // Depend only on this observer, preserving the framework's existing resource order.
        for (String name : new String[]{"entityManagerFactory", "taskScheduler"}) {
            var definition = factory.getBeanDefinition(name);
            String[] previous = definition.getDependsOn();
            String[] dependencies = previous == null ? new String[1] : Arrays.copyOf(previous, previous.length + 1);
            dependencies[dependencies.length - 1] = "shutdownProbe";
            definition.setDependsOn(dependencies);
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof AuthorizationState state) {
            TokenResponse token = new TokenResponse();
            token.setAccessToken("disposable-test-token");
            token.setRefreshToken("disposable-test-refresh");
            token.setExpiresAt(LocalDateTime.now().plusDays(1));
            state.setTokenResponse(token);
        }
        if (bean instanceof EntityManagerFactory emf) entityManagerFactory = emf;
        if (bean instanceof ThreadPoolTaskScheduler taskScheduler) scheduler = taskScheduler;
        if (bean instanceof DataSource source) return trackedDataSource(source);
        if (name.equals("oauthClient")) {
            // Any accidental OAuth request is rejected locally, never sent to MAL.
            return ((RestClient) bean).mutate().requestFactory((uri, method) -> {
                throw new java.io.IOException("OAuth network access disabled in lifecycle fixture");
            }).build();
        }
        if (name.equals("apiKeyClient")) {
            return ((RestClient) bean).mutate()
                    .requestFactory((uri, method) -> request(uri, method))
                    .requestInterceptors(interceptors -> interceptors.addFirst((request, body, execution) -> {
                        try {
                            return execution.execute(request, body);
                        } catch (RuntimeException | java.io.IOException failure) {
                            record("INTERRUPTED=" + Thread.currentThread().isInterrupted());
                            throw failure;
                        }
                    })).build();
        }
        return bean;
    }

    private ClientHttpRequest request(URI uri, HttpMethod method) {
        return new ClientHttpRequest() {
            private final HttpHeaders headers = new HttpHeaders();
            public HttpMethod getMethod() { return method; }
            public URI getURI() { return uri; }
            public HttpHeaders getHeaders() { return headers; }
            public java.util.Map<String, Object> getAttributes() { return new java.util.HashMap<>(); }
            public OutputStream getBody() { return new ByteArrayOutputStream(); }
            public ClientHttpResponse execute() {
                record("HTTP_ATTEMPT");
                return new ClientHttpResponse() {
                    public HttpStatus getStatusCode() { return HttpStatus.SERVICE_UNAVAILABLE; }
                    public String getStatusText() { return "Controlled unavailable"; }
                    public HttpHeaders getHeaders() { return new HttpHeaders(); }
                    public InputStream getBody() { return new ByteArrayInputStream(new byte[0]); }
                    public void close() { }
                };
            }
        };
    }

    private DataSource trackedDataSource(DataSource source) {
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
            try {
                Object result = method.invoke(source, args);
                if (!(result instanceof Connection connection)) return result;
                connections.incrementAndGet();
                AtomicBoolean closed = new AtomicBoolean();
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (connectionProxy, connectionMethod, connectionArgs) -> {
                    try {
                        Object value = connectionMethod.invoke(connection, connectionArgs);
                        if (connectionMethod.getName().equals("close") && closed.compareAndSet(false, true)) connections.decrementAndGet();
                        return value;
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    @Override
    public void destroy() {
        boolean terminated = false;
        if (scheduler != null) {
            try {
                // Shutdown is asynchronous. Observe it; never initiate shutdown or cancel a task here.
                terminated = scheduler.getScheduledThreadPoolExecutor().awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        }
        record("SCHEDULER_TERMINATED=" + terminated);
        record("ENTITY_MANAGER_CLOSED=" + (entityManagerFactory != null && !entityManagerFactory.isOpen()));
        record("OPEN_CONNECTIONS=" + connections.get());
    }

    private static synchronized void record(String event) {
        try {
            Files.writeString(Path.of(System.getProperty("shutdown.probe.events")), event + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
