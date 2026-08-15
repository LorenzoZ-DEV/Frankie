package dev.lorenzz.frankie.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lorenzz.frankie.config.YamlConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MySQL connection pool (HikariCP) + schema bootstrap.
 *
 * <p>Every query runs on {@link #executor}, a dedicated pool sized to the Hikari
 * pool. JDBC calls block the calling thread, so they must never run on the
 * ForkJoin common pool (shared with the whole JVM) nor on a Netty event loop.
 */
public class Database {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public Database(YamlConfig config) {
        int poolSize = Math.max(1, config.getInt("mysql.pool.maximum-pool-size", 10));

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("Frankie-Pool");
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mysql://%s:%d/%s?%s".formatted(
                config.getString("mysql.host", "localhost"),
                config.getInt("mysql.port", 3306),
                config.getString("mysql.database", "frankie"),
                config.getString("mysql.properties",
                        "useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true")));
        hikari.setUsername(config.getString("mysql.username", "root"));
        hikari.setPassword(config.getString("mysql.password", ""));
        hikari.setMaximumPoolSize(poolSize);
        hikari.setMinimumIdle(config.getInt("mysql.pool.minimum-idle", 2));
        hikari.setConnectionTimeout(config.getLong("mysql.pool.connection-timeout", 5000L));
        hikari.setMaxLifetime(config.getLong("mysql.pool.max-lifetime", 1800000L));
        // Server-side statement caching: every query here is a PreparedStatement.
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikari);
        this.executor = Executors.newFixedThreadPool(poolSize, namedThreads());
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "Frankie-DB-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** A unit of JDBC work returning a value. */
    @FunctionalInterface
    public interface SqlQuery<T> {
        T run(Connection connection) throws SQLException;
    }

    /** A unit of JDBC work returning nothing. */
    @FunctionalInterface
    public interface SqlUpdate {
        void run(Connection connection) throws SQLException;
    }

    /**
     * Runs {@code query} off-thread on a pooled connection. The returned future
     * completes exceptionally with the {@link SQLException} wrapped in a
     * {@link java.util.concurrent.CompletionException}; callers are expected to
     * attach a handler (see {@code Futures}).
     */
    public <T> CompletableFuture<T> query(SqlQuery<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return query.run(connection);
            } catch (SQLException e) {
                throw new DatabaseException(e);
            }
        }, executor);
    }

    /** Same as {@link #query} for statements with no result. */
    public CompletableFuture<Void> update(SqlUpdate update) {
        return query(connection -> {
            update.run(connection);
            return null;
        });
    }

    public void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_players (
                        uuid CHAR(36) NOT NULL PRIMARY KEY,
                        name VARCHAR(16) NOT NULL,
                        last_ip VARCHAR(45) NULL,
                        last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_players_name (name),
                        INDEX idx_players_ip (last_ip)
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_friends (
                        player_uuid CHAR(36) NOT NULL,
                        friend_uuid CHAR(36) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, friend_uuid)
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_friend_requests (
                        sender_uuid CHAR(36) NOT NULL,
                        target_uuid CHAR(36) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (sender_uuid, target_uuid)
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_reports (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        reporter_uuid CHAR(36) NOT NULL,
                        reporter_name VARCHAR(16) NOT NULL,
                        reported_uuid CHAR(36) NOT NULL,
                        reported_name VARCHAR(16) NOT NULL,
                        reason TEXT NOT NULL,
                        server VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_staff_notes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        target_uuid CHAR(36) NOT NULL,
                        target_name VARCHAR(16) NOT NULL,
                        author_uuid CHAR(36) NOT NULL,
                        author_name VARCHAR(16) NOT NULL,
                        note TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_notes_target (target_uuid)
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_requests (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid CHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        message TEXT NOT NULL,
                        server VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_blacklist (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        issuer_uuid CHAR(36) NOT NULL,
                        issuer_name VARCHAR(16) NOT NULL,
                        victim_uuid CHAR(36) NOT NULL,
                        victim_name VARCHAR(16) NOT NULL,
                        ip VARCHAR(45) NOT NULL,
                        luckperms_suffix VARCHAR(64) DEFAULT '',
                        reason TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        active TINYINT(1) NOT NULL DEFAULT 1,
                        removed_at TIMESTAMP NULL DEFAULT NULL,
                        removed_by VARCHAR(16) NULL DEFAULT NULL,
                        INDEX idx_blacklist_victim (victim_uuid),
                        INDEX idx_blacklist_name (victim_name),
                        INDEX idx_blacklist_ip (ip),
                        INDEX idx_blacklist_active (active, victim_uuid)
                    )""");

            migrate(connection);
        }
    }

    /**
     * Brings schemas created by older Frankie versions up to date. Each step is
     * a no-op when the column or index already exists, so this is safe to run on
     * every boot.
     */
    private void migrate(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "frankie_players", "last_ip", "VARCHAR(45) NULL AFTER name");
        addIndexIfMissing(connection, "frankie_players", "idx_players_ip", "(last_ip)");
        addIndexIfMissing(connection, "frankie_blacklist", "idx_blacklist_name", "(victim_name)");
        addIndexIfMissing(connection, "frankie_blacklist", "idx_blacklist_ip", "(ip)");
        // Blacklists are lifted by flipping `active`, not by deleting the row:
        // the history is what /blacklistusers counts to show how many times an
        // account has been listed. Rows written before this column existed are
        // all currently-enforced ones, so defaulting them to 1 is correct.
        addColumnIfMissing(connection, "frankie_blacklist", "active",
                "TINYINT(1) NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, "frankie_blacklist", "removed_at",
                "TIMESTAMP NULL DEFAULT NULL");
        addColumnIfMissing(connection, "frankie_blacklist", "removed_by",
                "VARCHAR(16) NULL DEFAULT NULL");
        addIndexIfMissing(connection, "frankie_blacklist", "idx_blacklist_active",
                "(active, victim_uuid)");
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE %s ADD COLUMN %s %s".formatted(table, column, definition));
        }
    }

    private void addIndexIfMissing(Connection connection, String table, String index, String columns)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX %s ON %s %s".formatted(index, table, columns));
        }
    }

    /** Drains in-flight queries, then closes the pool. */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    /** Unchecked wrapper so JDBC failures travel through {@link CompletableFuture}. */
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(SQLException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
