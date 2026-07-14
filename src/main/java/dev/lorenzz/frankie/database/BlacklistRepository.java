package dev.lorenzz.frankie.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

/**
 * Persists blacklist records.
 */
public class BlacklistRepository {

    private final Database database;

    public BlacklistRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Long> insert(UUID issuer, String issuerName,
                                          UUID victim, String victimName,
                                          String ip, String luckpermsSuffix,
                                          String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO frankie_blacklist (issuer_uuid, issuer_name, victim_uuid, victim_name, ip, luckperms_suffix, reason, created_at) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                         PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, issuer.toString());
                statement.setString(2, issuerName);
                statement.setString(3, victim.toString());
                statement.setString(4, victimName);
                statement.setString(5, ip);
                statement.setString(6, luckpermsSuffix);
                statement.setString(7, reason);
                statement.setObject(8, LocalDateTime.now());
                int affected = statement.executeUpdate();
                if (affected == 0) {
                    throw new SQLException("Creating blacklist failed, no rows affected.");
                }
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Creating blacklist failed, no ID obtained.");
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Boolean> isVictimBlacklisted(UUID victimUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT 1 FROM frankie_blacklist WHERE victim_uuid = ? LIMIT 1")) {
                statement.setString(1, victimUuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Returns the IP address of the most recent blacklist entry for the given victim,
     * or empty if none.
     */
    public CompletableFuture<Optional<String>> getLatestIpByVictim(UUID victimUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT ip FROM frankie_blacklist WHERE victim_uuid = ? ORDER BY created_at DESC LIMIT 1")) {
                statement.setString(1, victimUuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.ofNullable(result.getString("ip"));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Deletes all blacklist records for the given victim.
     * Returns the number of rows deleted.
     */
    public CompletableFuture<Integer> deleteByVictim(UUID victimUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM frankie_blacklist WHERE victim_uuid = ?")) {
                statement.setString(1, victimUuid.toString());
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Simple record to hold blacklist data.
     */
    public static class BlacklistRecord {
        public final UUID victimUuid;
        public final String victimName;
        public final String luckpermsSuffix;
        public final String ip;

        public BlacklistRecord(UUID victimUuid, String victimName, String luckpermsSuffix, String ip) {
            this.victimUuid = victimUuid;
            this.victimName = victimName;
            this.luckpermsSuffix = luckpermsSuffix;
            this.ip = ip;
        }
    }

    /**
     * Returns the most recent blacklist record for the given victim name,
     * or empty if none.
     */
    public CompletableFuture<Optional<BlacklistRecord>> findLatestByVictimName(String victimName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT victim_uuid, victim_name, luckperms_suffix, ip FROM frankie_blacklist WHERE victim_name = ? ORDER BY created_at DESC LIMIT 1")) {
                statement.setString(1, victimName);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new BlacklistRecord(
                                UUID.fromString(result.getString("victim_uuid")),
                                result.getString("victim_name"),
                                result.getString("luckperms_suffix"),
                                result.getString("ip")));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}