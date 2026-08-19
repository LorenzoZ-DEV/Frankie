package dev.lorenzz.frankie.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * UUID/name/IP cache, used to resolve players who are not currently online.
 */
public class PlayerRepository {

    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    /**
     * Records a login. {@code last_seen} is set explicitly: relying on
     * {@code ON UPDATE CURRENT_TIMESTAMP} silently skipped the update whenever
     * the row was unchanged (the usual case — same uuid, same name), which left
     * {@code findByName}'s ordering stale.
     */
    public CompletableFuture<Void> upsert(UUID uuid, String name, String ip) {
        return database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO frankie_players (uuid, name, last_ip, last_seen)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        last_ip = COALESCE(VALUES(last_ip), last_ip),
                        last_seen = CURRENT_TIMESTAMP""")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setString(3, ip);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<CachedPlayer>> findByName(String name) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, name FROM frankie_players WHERE name = ? ORDER BY last_seen DESC LIMIT 1")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new CachedPlayer(
                                UUID.fromString(result.getString("uuid")),
                                result.getString("name")));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    /**
     * Everything the cache knows about one account, for the {@code /check}
     * summary screen: an offline player has no address nor last-seen date
     * anywhere else.
     */
    public CompletableFuture<Optional<PlayerProfile>> findProfile(UUID uuid) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, name, last_ip, last_seen FROM frankie_players WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.<PlayerProfile>empty();
                    }
                    String ip = result.getString("last_ip");
                    Timestamp seen = result.getTimestamp("last_seen");
                    return Optional.of(new PlayerProfile(
                            UUID.fromString(result.getString("uuid")),
                            result.getString("name"),
                            ip == null ? "" : ip,
                            seen == null ? null : seen.toLocalDateTime()));
                }
            }
        });
    }

    /** Last IP seen for a player name, used to blacklist offline accounts. */
    public CompletableFuture<Optional<String>> lastIpByName(String name) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT last_ip FROM frankie_players WHERE name = ? ORDER BY last_seen DESC LIMIT 1")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        String ip = result.getString("last_ip");
                        return ip == null || ip.isBlank() ? Optional.<String>empty() : Optional.of(ip);
                    }
                    return Optional.<String>empty();
                }
            }
        });
    }

    public record CachedPlayer(UUID uuid, String name) {
    }

    /** A cache row in full; {@code lastIp} is empty and {@code lastSeen} null when unknown. */
    public record PlayerProfile(UUID uuid, String name, String lastIp, LocalDateTime lastSeen) {
    }
}
