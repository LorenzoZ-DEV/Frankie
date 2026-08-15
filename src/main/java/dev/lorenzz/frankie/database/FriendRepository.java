package dev.lorenzz.frankie.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FriendRepository {

    private final Database database;

    public FriendRepository(Database database) {
        this.database = database;
    }

    /**
     * Everything {@code /friend add} needs to decide what to do, in one round
     * trip. The command used to chain four separate queries through nested
     * callbacks, each hopping threads and each able to fail unnoticed.
     */
    public CompletableFuture<FriendState> state(UUID player, UUID target) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                        (SELECT COUNT(*) FROM frankie_friends WHERE player_uuid = ?) AS friend_count,
                        EXISTS(SELECT 1 FROM frankie_friends
                               WHERE player_uuid = ? AND friend_uuid = ?) AS are_friends,
                        EXISTS(SELECT 1 FROM frankie_friend_requests
                               WHERE sender_uuid = ? AND target_uuid = ?) AS outgoing,
                        EXISTS(SELECT 1 FROM frankie_friend_requests
                               WHERE sender_uuid = ? AND target_uuid = ?) AS incoming""")) {
                String playerId = player.toString();
                String targetId = target.toString();
                statement.setString(1, playerId);
                statement.setString(2, playerId);
                statement.setString(3, targetId);
                statement.setString(4, playerId);
                statement.setString(5, targetId);
                statement.setString(6, targetId);
                statement.setString(7, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return new FriendState(
                            result.getInt("friend_count"),
                            result.getBoolean("are_friends"),
                            result.getBoolean("outgoing"),
                            result.getBoolean("incoming"));
                }
            }
        });
    }

    /** Friendship stored in both directions so lookups stay single-column. */
    public CompletableFuture<Void> addFriends(UUID player, UUID other) {
        return database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO frankie_friends (player_uuid, friend_uuid) VALUES (?, ?), (?, ?)")) {
                statement.setString(1, player.toString());
                statement.setString(2, other.toString());
                statement.setString(3, other.toString());
                statement.setString(4, player.toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> removeFriends(UUID player, UUID other) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM frankie_friends
                    WHERE (player_uuid = ? AND friend_uuid = ?)
                       OR (player_uuid = ? AND friend_uuid = ?)""")) {
                statement.setString(1, player.toString());
                statement.setString(2, other.toString());
                statement.setString(3, other.toString());
                statement.setString(4, player.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<PlayerRepository.CachedPlayer>> getFriends(UUID player) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT f.friend_uuid, COALESCE(p.name, '?') AS name
                    FROM frankie_friends f
                    LEFT JOIN frankie_players p ON p.uuid = f.friend_uuid
                    WHERE f.player_uuid = ?
                    ORDER BY name""")) {
                statement.setString(1, player.toString());
                return readPlayers(statement, "friend_uuid");
            }
        });
    }

    public CompletableFuture<Boolean> hasRequest(UUID sender, UUID target) {
        return database.query(connection -> {
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
        return database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO frankie_friend_requests (sender_uuid, target_uuid) VALUES (?, ?)")) {
                statement.setString(1, sender.toString());
                statement.setString(2, target.toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> removeRequest(UUID sender, UUID target) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM frankie_friend_requests WHERE sender_uuid = ? AND target_uuid = ?")) {
                statement.setString(1, sender.toString());
                statement.setString(2, target.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public CompletableFuture<List<PlayerRepository.CachedPlayer>> getIncomingRequests(UUID target) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT r.sender_uuid, COALESCE(p.name, '?') AS name
                    FROM frankie_friend_requests r
                    LEFT JOIN frankie_players p ON p.uuid = r.sender_uuid
                    WHERE r.target_uuid = ?
                    ORDER BY r.created_at""")) {
                statement.setString(1, target.toString());
                return readPlayers(statement, "sender_uuid");
            }
        });
    }

    private static List<PlayerRepository.CachedPlayer> readPlayers(PreparedStatement statement, String uuidColumn)
            throws java.sql.SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<PlayerRepository.CachedPlayer> players = new ArrayList<>();
            while (result.next()) {
                players.add(new PlayerRepository.CachedPlayer(
                        UUID.fromString(result.getString(uuidColumn)),
                        result.getString("name")));
            }
            return players;
        }
    }

    /**
     * @param friendCount how many friends the requesting player already has
     * @param areFriends  the two are already friends
     * @param outgoing    the player already sent a request to the target
     * @param incoming    the target already sent a request to the player
     */
    public record FriendState(int friendCount, boolean areFriends, boolean outgoing, boolean incoming) {
    }
}
