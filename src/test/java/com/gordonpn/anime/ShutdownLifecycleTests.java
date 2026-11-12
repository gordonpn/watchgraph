package com.gordonpn.anime;

import lifecyclefixture.ShutdownProbe;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ShutdownLifecycleTests {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(90)
    void packagedCliClosesResourcesDuringActiveRetry(boolean signal) throws Exception {
        Path fixture = fixtureJar();
        Path events = directory.resolve("events.log");
        Path output = directory.resolve("console.log");
        Path applicationLog = directory.resolve("recommendation.log");
        Path jar = Path.of(System.getProperty("shutdown.lifecycle.jar"));
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-Dloader.path=" + fixture,
                "-Dspring.main.sources=lifecyclefixture.ShutdownProbe",
                "-Dshutdown.probe.events=" + events,
                "-Dspring.datasource.url=jdbc:sqlite:" + directory.resolve("disposable.db"),
                "-Dmal.retry.max-attempts=-1", "-Dmal.retry.initial-backoff-ms=60000",
                "-Dspring.main.banner-mode=off",
                "-Dspring.shell.history.enabled=false",
                "-cp", jar.toString(), "org.springframework.boot.loader.launch.PropertiesLauncher");
        builder.environment().put("MAL_CLIENT_ID", "disposable-client");
        builder.environment().put("MAL_CLIENT_SECRET", "disposable-secret");
        Process process = builder.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            await(process, applicationLog, "Retrying in 60000ms", output);
            await(process, output, "shell:", output);
            long started = System.nanoTime();
            if (signal) process.destroy();
            else {
                process.getOutputStream().write("exit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                process.getOutputStream().flush();
            }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), () -> "Shutdown exceeded 30s: " + read(output));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertTrue(elapsed.compareTo(Duration.ofSeconds(30)) < 0);
            System.out.println("Packaged CLI " + (signal ? "SIGTERM" : "exit") + " shutdown: " + elapsed.toMillis() + "ms");
            assertEquals(signal ? 143 : 0, process.exitValue(), () -> read(output));
            String recorded = Files.readString(events);
            assertEquals(1, recorded.lines().filter("HTTP_ATTEMPT"::equals).count(), recorded);
            assertTrue(recorded.contains("INTERRUPTED=true"), recorded);
            assertTrue(recorded.contains("SCHEDULER_TERMINATED=true"), recorded);
            assertTrue(recorded.contains("ENTITY_MANAGER_CLOSED=true"), recorded);
            assertTrue(recorded.contains("OPEN_CONNECTIONS=0"), recorded);
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("disposable.db"));
                 var statement = connection.createStatement()) {
                statement.execute("BEGIN EXCLUSIVE");
                statement.execute("ROLLBACK");
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private Path fixtureJar() throws Exception {
        Path classes = Path.of(ShutdownProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path jar = directory.resolve("shutdown-fixture.jar");
        try (var paths = Files.walk(classes.resolve("lifecyclefixture"));
             var stream = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                stream.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, stream);
                stream.closeEntry();
            }
        }
        return jar;
    }

    private static void await(Process process, Path file, String marker, Path output) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline && process.isAlive()) {
            if (read(file).contains(marker)) return;
            Thread.sleep(25);
        }
        fail("Packaged CLI did not reach " + marker + ": " + read(output) + "\n" + read(file));
    }

    private static String read(Path file) {
        try { return Files.exists(file) ? Files.readString(file) : ""; }
        catch (IOException failure) { return failure.toString(); }
    }
}
