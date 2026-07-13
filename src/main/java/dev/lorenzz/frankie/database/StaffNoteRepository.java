package dev.lorenzz.frankie.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists staff notes attached to players (/staffnotes).
 */
public class StaffNoteRepository {

    private final Database database;

    public StaffNoteRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Void> add(UUID target, String targetName,
                                       UUID author, String authorName, String note) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO frankie_staff_notes (target_uuid, target_name, author_uuid, author_name, note) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, target.toString());
                statement.setString(2, targetName);
                statement.setString(3, author.toString());
                statement.setString(4, authorName);
                statement.setString(5, note);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<List<StaffNote>> getByTarget(UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT id, author_name, note, created_at FROM frankie_staff_notes WHERE target_uuid = ? ORDER BY created_at DESC")) {
                statement.setString(1, target.toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<StaffNote> notes = new ArrayList<>();
                    while (result.next()) {
                        notes.add(new StaffNote(
                                result.getInt("id"),
                                result.getString("author_name"),
                                result.getString("note"),
                                result.getTimestamp("created_at").toLocalDateTime()));
                    }
                    return notes;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public record StaffNote(int id, String author, String note, java.time.LocalDateTime createdAt) {
    }
}