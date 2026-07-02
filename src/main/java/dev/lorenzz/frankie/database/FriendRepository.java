package dev.lorenzz.frankie.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FriendRepository {

    private final Database database;

    public FriendRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Boolean> areFriends(UUID player, UUID other) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM frankie_friends WHERE player_uuid = ? AND friend_uuid = ? LIMIT 1")) {
                statement.setString(1, player.toString());
                statement.setString(2, other.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    public CompletableFuture<Integer> countFriends(UUID player) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM frankie_friends WHERE player_uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
    }

    /** Friendship stored in both directions so lookups stay single-column. */
    public CompletableFuture<Void> addFriends(UUID player, UUID other) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO frankie_friends (player_uuid, friend_uuid) VALUES (?, ?), (?, ?)")) {
                statement.setString(1, player.toString());
                statement.setString(2, other.toString());
                statement.setString(3, other.toString());
                statement.setString(4, player.toString());
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> removeFriends(UUID player, UUID other) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM frankie_friends WHERE (player_uuid = ? AND friend_uuid = ?) OR (player_uuid = ? AND friend_uuid = ?)")) {
                statement.setString(1, player.toString());
                statement.setString(2, other.toString());
                statement.setString(3, other.toString());
                statement.setString(4, player.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<PlayerRepository.CachedPlayer>> getFriends(UUID player) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT f.friend_uuid, COALESCE(p.name, '?') AS name
                    FROM frankie_friends f
                    LEFT JOIN frankie_players p ON p.uuid = f.friend_uuid
                    WHERE f.player_uuid = ?
                    ORDER BY name""")) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<PlayerRepository.CachedPlayer> friends = new ArrayList<>();
                    while (result.next()) {
                        friends.add(new PlayerRepository.CachedPlayer(
                                UUID.fromString(result.getString("friend_uuid")),
                                result.getString("name")));
                    }
                    return friends;
                }
            }
        });
    }

    public CompletableFuture<Boolean> hasRequest(UUID sender, UUID target) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM frankie_friend_requests WHERE sender_uuid = ? AND target_uuid = ? LIMIT 1")) {
                statement.setString(1, sender.toString());
                statement.setString(2, target.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    public CompletableFuture<Void> addRequest(UUID sender, UUID target) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO frankie_friend_requests (sender_uuid, target_uuid) VALUES (?, ?)")) {
                statement.setString(1, sender.toString());
                statement.setString(2, target.toString());
                statement.executeUpdate();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> removeRequest(UUID sender, UUID target) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM frankie_friend_requests WHERE sender_uuid = ? AND target_uuid = ?")) {
                statement.setString(1, sender.toString());
                statement.setString(2, target.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<PlayerRepository.CachedPlayer>> getIncomingRequests(UUID target) {
        return supply(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT r.sender_uuid, COALESCE(p.name, '?') AS name
                    FROM frankie_friend_requests r
                    LEFT JOIN frankie_players p ON p.uuid = r.sender_uuid
                    WHERE r.target_uuid = ?
                    ORDER BY r.created_at""")) {
                statement.setString(1, target.toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<PlayerRepository.CachedPlayer> requests = new ArrayList<>();
                    while (result.next()) {
                        requests.add(new PlayerRepository.CachedPlayer(
                                UUID.fromString(result.getString("sender_uuid")),
                                result.getString("name")));
                    }
                    return requests;
                }
            }
        });
    }

    private interface Query<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> CompletableFuture<T> supply(Query<T> query) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                return query.run(connection);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}