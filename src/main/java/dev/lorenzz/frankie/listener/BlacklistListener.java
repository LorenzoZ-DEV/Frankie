package dev.lorenzz.frankie.listener;

import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.database.BlacklistRepository;
import dev.lorenzz.frankie.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks blacklisted players in-memory for quick access by other components,
 * and handles kicking blacklisted players on login.
 */
public class BlacklistListener {

    private final BlacklistRepository blacklistRepository;
    private final Messages messages;
    /** Set of UUIDs of players currently known to be blacklisted (in-memory cache). */
    private final Set<UUID> blacklistedPlayers = ConcurrentHashMap.newKeySet();

    public BlacklistListener(BlacklistRepository blacklistRepository, Messages messages) {
        this.blacklistRepository = blacklistRepository;
        this.messages = messages;
    }

    /**
     * Called when a player logs in. Checks the database and kicks if blacklisted.
     */
    public void onLogin(Player player) {
        UUID uuid = player.getUniqueId();
        // Check database for blacklist entry
        blacklistRepository.isVictimBlacklisted(uuid).thenAccept(isBlacklisted -> {
            if (isBlacklisted) {
                // Kick the player
                Component kickMessage = Component.text()
                    .append(Component.text("Sei stato blacklistato! ").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                    .append(Component.newline())
                    .append(Component.text("Questa punizione è definitiva e non può essere contestata.").color(NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Contatta lo staff per assistenza.").color(NamedTextColor.YELLOW))
                    .build();
                player.disconnect(kickMessage);
                blacklistedPlayers.add(uuid);
            } else {
                // Not blacklisted, remove from cache if present
                blacklistedPlayers.remove(uuid);
            }
        }).exceptionally(ex -> {
            System.err.println("[BlacklistListener] Failed to check blacklist status for " + uuid + ": " + ex);
            return null;
        });
    }

    /**
     * Marks a player as blacklisted or not in the in-memory cache.
     * Called when a blacklist action is performed.
     */
    public void setBlacklisted(Player player, boolean blacklisted) {
        UUID uuid = player.getUniqueId();
        if (blacklisted) {
            blacklistedPlayers.add(uuid);
        } else {
            blacklistedPlayers.remove(uuid);
        }
    }

    /**
     * Checks if a player is currently considered blacklisted (in-memory cache).
     * Note: This may be slightly out-of-sync with the database if the blacklist
     * was modified by another server instance. For authoritative check, use
     * BlacklistRepository.isVictimBlacklisted.
     */
    public boolean isBlacklisted(Player player) {
        return blacklistedPlayers.contains(player.getUniqueId());
    }

    /**
     * Returns the in-memory set of blacklisted player UUIDs.
     * For internal use only.
     */
    public Set<UUID> getBlacklistedPlayers() {
        return blacklistedPlayers;
    }
}