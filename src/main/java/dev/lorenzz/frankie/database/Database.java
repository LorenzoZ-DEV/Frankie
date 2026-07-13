package dev.lorenzz.frankie.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lorenzz.frankie.config.YamlConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MySQL connection pool (HikariCP) + schema bootstrap. Queries run async on
 * the common pool; Hikari bounds real concurrency to the pool size.
 */
public class Database {

    private final HikariDataSource dataSource;

    public Database(YamlConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("Frankie-Pool");
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8".formatted(
                config.getString("mysql.host", "localhost"),
                config.getInt("mysql.port", 3306),
                config.getString("mysql.database", "frankie")));
        hikari.setUsername(config.getString("mysql.username", "root"));
        hikari.setPassword(config.getString("mysql.password", ""));
        hikari.setMaximumPoolSize(config.getInt("mysql.pool.maximum-pool-size", 10));
        hikari.setMinimumIdle(config.getInt("mysql.pool.minimum-idle", 2));
        hikari.setConnectionTimeout(config.getLong("mysql.pool.connection-timeout", 5000L));
        hikari.setMaxLifetime(config.getLong("mysql.pool.max-lifetime", 1800000L));

        this.dataSource = new HikariDataSource(hikari);
    }

    public void createTables() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS frankie_players (
                        uuid CHAR(36) NOT NULL PRIMARY KEY,
                        name VARCHAR(16) NOT NULL,
                        last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_players_name (name)
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
                        INDEX idx_blacklist_victim (victim_uuid)
                    )""");
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }
}