package dev.lorenzz.frankie.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * UUID/name cache table, used to resolve offline players by name.
 */
public class PlayerRepository {

    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public void upsert(UUID uuid, String name) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO frankie_players (uuid, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Optional<CachedPlayer>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
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
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public record CachedPlayer(UUID uuid, String name) {
    }
}