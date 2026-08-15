package dev.lorenzz.frankie.database;

import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists /report and /request entries.
 */
public class ReportRepository {

    private final Database database;

    public ReportRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Void> saveReport(UUID reporter, String reporterName,
                                              UUID reported, String reportedName,
                                              String reason, String server) {
        return database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO frankie_reports
                        (reporter_uuid, reporter_name, reported_uuid, reported_name, reason, server)
                    VALUES (?, ?, ?, ?, ?, ?)""")) {
                statement.setString(1, reporter.toString());
                statement.setString(2, reporterName);
                statement.setString(3, reported.toString());
                statement.setString(4, reportedName);
                statement.setString(5, reason);
                statement.setString(6, server);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> saveRequest(UUID player, String playerName, String message, String server) {
        return database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO frankie_requests (player_uuid, player_name, message, server) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, player.toString());
                statement.setString(2, playerName);
                statement.setString(3, message);
                statement.setString(4, server);
                statement.executeUpdate();
            }
        });
    }
}
