package dev.lorenzz.frankie.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persists blacklist records.
 *
 * <p>Lifting a blacklist flips {@code active} instead of deleting the row. The
 * row is the only record that a punishment ever happened, and the GUI reports
 * how many times an account has been listed — deleting made that number reset to
 * zero on every unblacklist.
 */
public class BlacklistRepository {

    /** Columns every record read shares, so the projections cannot drift apart. */
    private static final String RECORD_COLUMNS =
            "id, victim_uuid, victim_name, luckperms_suffix, ip, reason, issuer_name, created_at";

    private final Database database;

    public BlacklistRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Long> insert(UUID issuer, String issuerName,
                                          UUID victim, String victimName,
                                          String ip, String luckpermsSuffix,
                                          String reason) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO frankie_blacklist
                        (issuer_uuid, issuer_name, victim_uuid, victim_name, ip, luckperms_suffix, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, issuer.toString());
                statement.setString(2, issuerName);
                statement.setString(3, victim.toString());
                statement.setString(4, victimName);
                statement.setString(5, ip);
                statement.setString(6, luckpermsSuffix);
                statement.setString(7, reason);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : 0L;
                }
            }
        });
    }

    /**
     * Loads every enforced identity in one query, for the in-memory cache the
     * login listener checks against. Lifted records are skipped.
     */
    public CompletableFuture<Snapshot> snapshot() {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT victim_uuid, victim_name, ip FROM frankie_blacklist WHERE active = 1");
                 ResultSet result = statement.executeQuery()) {
                Set<String> ips = new HashSet<>();
                Set<UUID> uuids = new HashSet<>();
                Set<String> names = new HashSet<>();
                while (result.next()) {
                    String ip = result.getString("ip");
                    if (ip != null && !ip.isBlank() && !Snapshot.PLACEHOLDER_IP.equals(ip)) {
                        ips.add(ip);
                    }
                    String uuid = result.getString("victim_uuid");
                    if (uuid != null) {
                        uuids.add(UUID.fromString(uuid));
                    }
                    String name = result.getString("victim_name");
                    if (name != null && !name.isBlank()) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                }
                return new Snapshot(ips, uuids, names);
            }
        });
    }

    /**
     * Every account currently blacklisted, newest first, each carrying how many
     * times it has ever been listed and when that started. This is what
     * {@code /blacklistusers} paginates.
     */
    public CompletableFuture<List<BlacklistEntry>> listActive() {
        return database.query(connection -> {
            Map<UUID, History> history = historyTotals(connection);
            List<BlacklistEntry> entries = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + RECORD_COLUMNS + " FROM frankie_blacklist"
                            + " WHERE active = 1 ORDER BY created_at DESC, id DESC");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    BlacklistRecord record = read(result);
                    // A victim can hold several active rows (blacklisted again
                    // before being lifted); the newest one represents them.
                    if (!seen.add(record.victimUuid())) {
                        continue;
                    }
                    History totals = history.getOrDefault(record.victimUuid(),
                            new History(1, record.createdAt()));
                    entries.add(new BlacklistEntry(record, totals.times(), totals.firstAt()));
                }
            }
            return entries;
        });
    }

    private Map<UUID, History> historyTotals(Connection connection) throws SQLException {
        Map<UUID, History> totals = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT victim_uuid, COUNT(*) AS times, MIN(created_at) AS first_at"
                        + " FROM frankie_blacklist GROUP BY victim_uuid");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                totals.put(UUID.fromString(result.getString("victim_uuid")),
                        new History(result.getInt("times"), toLocal(result.getTimestamp("first_at"))));
            }
        }
        return totals;
    }

    /** Every record ever written for a victim, newest first. */
    public CompletableFuture<List<HistoryEntry>> history(UUID victimUuid, int limit) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, issuer_name, reason, ip, created_at, active, removed_at, removed_by
                    FROM frankie_blacklist
                    WHERE victim_uuid = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?""")) {
                statement.setString(1, victimUuid.toString());
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet result = statement.executeQuery()) {
                    List<HistoryEntry> entries = new ArrayList<>();
                    while (result.next()) {
                        entries.add(new HistoryEntry(
                                result.getLong("id"),
                                result.getString("issuer_name"),
                                orEmpty(result.getString("reason")),
                                orEmpty(result.getString("ip")),
                                toLocal(result.getTimestamp("created_at")),
                                result.getBoolean("active"),
                                toLocal(result.getTimestamp("removed_at")),
                                orEmpty(result.getString("removed_by"))));
                    }
                    return entries;
                }
            }
        });
    }

    /** Most recent record for a victim name whatever its state, or empty. */
    public CompletableFuture<Optional<BlacklistRecord>> findLatestByVictimName(String victimName) {
        return findOne("victim_name = ?", victimName, false);
    }

    /** Most recent <em>enforced</em> record for a victim name, or empty. */
    public CompletableFuture<Optional<BlacklistRecord>> findActiveByVictimName(String victimName) {
        return findOne("victim_name = ?", victimName, true);
    }

    /** Most recent <em>enforced</em> record for a victim uuid, or empty. */
    public CompletableFuture<Optional<BlacklistRecord>> findActiveByVictimUuid(UUID victimUuid) {
        return findOne("victim_uuid = ?", victimUuid.toString(), true);
    }

    private CompletableFuture<Optional<BlacklistRecord>> findOne(String where, String value, boolean activeOnly) {
        String sql = "SELECT " + RECORD_COLUMNS + " FROM frankie_blacklist WHERE " + where
                + (activeOnly ? " AND active = 1" : "")
                + " ORDER BY created_at DESC, id DESC LIMIT 1";
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(read(result)) : Optional.<BlacklistRecord>empty();
                }
            }
        });
    }

    /**
     * Lifts every enforced record of a victim and reports which identities were
     * freed, so the caller can drop exactly those entries from the cache.
     *
     * <p>An identity shared with a record that stays enforced must keep blocking:
     * lifting Bob must not unblock the IP or the name Alice is still listed
     * under.
     */
    public CompletableFuture<Removal> liftByVictim(UUID victimUuid, String removedBy) {
        return database.query(connection -> {
            Set<String> ips = new HashSet<>();
            Set<String> names = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT victim_name, ip FROM frankie_blacklist WHERE victim_uuid = ? AND active = 1")) {
                statement.setString(1, victimUuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String ip = result.getString("ip");
                        if (ip != null && !ip.isBlank()) {
                            ips.add(ip);
                        }
                        String name = result.getString("victim_name");
                        if (name != null && !name.isBlank()) {
                            names.add(name.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
            int lifted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE frankie_blacklist
                    SET active = 0, removed_at = CURRENT_TIMESTAMP, removed_by = ?
                    WHERE victim_uuid = ? AND active = 1""")) {
                statement.setString(1, removedBy);
                statement.setString(2, victimUuid.toString());
                lifted = statement.executeUpdate();
            }
            ips.removeAll(stillEnforced(connection, "ip", ips));
            names.removeAll(lowercase(stillEnforced(connection, "victim_name", names)));
            return new Removal(lifted, ips, names);
        });
    }

    /** Which of {@code candidates} still appear on an enforced record. */
    private Set<String> stillEnforced(Connection connection, String column, Set<String> candidates)
            throws SQLException {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(candidates.size(), "?"));
        Set<String> inUse = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT DISTINCT " + column + " FROM frankie_blacklist"
                        + " WHERE active = 1 AND " + column + " IN (" + placeholders + ")")) {
            int index = 1;
            for (String candidate : candidates) {
                statement.setString(index++, candidate);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    inUse.add(result.getString(1));
                }
            }
        }
        return inUse;
    }

    private static Set<String> lowercase(Set<String> values) {
        Set<String> lowered = new HashSet<>(values.size());
        for (String value : values) {
            lowered.add(value.toLowerCase(Locale.ROOT));
        }
        return lowered;
    }

    private static BlacklistRecord read(ResultSet result) throws SQLException {
        return new BlacklistRecord(
                result.getLong("id"),
                UUID.fromString(result.getString("victim_uuid")),
                result.getString("victim_name"),
                orEmpty(result.getString("luckperms_suffix")),
                orEmpty(result.getString("ip")),
                orEmpty(result.getString("reason")),
                result.getString("issuer_name"),
                toLocal(result.getTimestamp("created_at")));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record BlacklistRecord(long id, UUID victimUuid, String victimName, String luckpermsSuffix,
                                  String ip, String reason, String issuerName, LocalDateTime createdAt) {
    }

    /** An enforced record plus the totals of the victim's whole history. */
    public record BlacklistEntry(BlacklistRecord record, int times, LocalDateTime firstAt) {
    }

    /** One line of a victim's history, enforced or already lifted. */
    public record HistoryEntry(long id, String issuerName, String reason, String ip,
                               LocalDateTime createdAt, boolean active,
                               LocalDateTime removedAt, String removedBy) {
    }

    /** Identities freed by an unblacklist. */
    public record Removal(int liftedRows, Set<String> freedIps, Set<String> freedNames) {
    }

    /** Everything currently blacklisted, as identity sets. */
    public record Snapshot(Set<String> ips, Set<UUID> uuids, Set<String> names) {

        /**
         * Written when no IP could be resolved for an offline victim. It must
         * never enter the IP cache or it would deny every connection whose
         * address failed to resolve.
         */
        public static final String PLACEHOLDER_IP = "0.0.0.0";

        public static Snapshot empty() {
            return new Snapshot(Set.of(), Set.of(), Set.of());
        }
    }

    /** How many times a victim has been listed, and when it started. */
    private record History(int times, LocalDateTime firstAt) {
    }
}
