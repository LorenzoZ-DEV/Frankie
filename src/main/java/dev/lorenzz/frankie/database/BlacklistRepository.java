package dev.lorenzz.frankie.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
}