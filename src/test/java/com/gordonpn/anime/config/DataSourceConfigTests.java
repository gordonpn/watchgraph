package com.gordonpn.anime.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataSourceConfigTests {

    @TempDir
    Path tempDir;

    @Test
    void appliesWalAndBusyTimeoutToEveryConnection() throws SQLException {
        DataSource dataSource = dataSource();
        for (int i = 0; i < 3; i++) {
            try (Connection connection = dataSource.getConnection()) {
                assertEquals("wal", query(connection, "PRAGMA journal_mode"));
                assertEquals("5000", query(connection, "PRAGMA busy_timeout"));
            }
        }
    }

    @Test
    void writerCommitsWhileReaderRetainsItsSnapshot() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection reader = dataSource.getConnection()) {
            try (var statement = reader.createStatement()) {
                statement.executeUpdate("CREATE TABLE entries (value INTEGER)");
                statement.executeUpdate("INSERT INTO entries VALUES (1)");
            }
            reader.setAutoCommit(false);
            assertEquals("1", query(reader, "SELECT COUNT(*) FROM entries"));

            // Keep the read transaction open until the independent writer commits.
            try (var executor = Executors.newSingleThreadExecutor()) {
                var write = executor.submit(() -> {
                    try (Connection writer = dataSource.getConnection();
                         var statement = writer.createStatement()) {
                        return statement.executeUpdate("INSERT INTO entries VALUES (2)");
                    }
                });
                assertEquals(1, write.get(10, TimeUnit.SECONDS));
            }
            assertEquals("1", query(reader, "SELECT COUNT(*) FROM entries"));
            reader.commit();
            assertEquals("2", query(reader, "SELECT COUNT(*) FROM entries"));
        }
    }

    private DataSource dataSource() {
        DataSourceConfig config = new DataSourceConfig();
        ReflectionTestUtils.setField(config, "dbUrl", "jdbc:sqlite:" + tempDir.resolve("anime.db"));
        return config.dataSource();
    }

    private String query(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
