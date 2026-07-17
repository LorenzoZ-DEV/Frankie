package dev.lorenzz.frankie.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.ResultedEvent.GenericResult;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.database.BlacklistRepository;
import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks blacklisted players and IPs in-memory for quick enforcement:
 * - denies logins from blacklisted IPs (PreLoginEvent)
 * - cancels the server ping for blacklisted IPs so the server looks offline (ProxyPingEvent)
 * The IP cache is loaded at startup and kept in sync when blacklist actions happen.
 */
public class BlacklistListener {

    private final BlacklistRepository blacklistRepository;
    private final Set<UUID> blacklistedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklistedIps = ConcurrentHashMap.newKeySet();

    private static final Component DENY = Component.text("""
            Il tuo account è blacklistato da questo VenrixMC
            
            Questa tipo di punizione è permanente e non può essere revocata.
    
            Se pensi che sia un errore, contatta lo staff tramite Discord: https://discord.gg/uHrkc2g3r
            """);

    public BlacklistListener(BlacklistRepository blacklistRepository) {
        this.blacklistRepository = blacklistRepository;
    }

    // --- IP cache management ---

    /** Loads all currently-blacklisted IPs from the database. Call at proxy init. */
    public void loadIpCache() {
        blacklistRepository.allBlacklistedIps().thenAccept(blacklistedIps::addAll)
                .exceptionally(ex -> {
                    System.err.println("[BlacklistListener] Failed to load IP cache: " + ex);
                    return null;
                });
    }

    public void addIp(String ip) {
        if (ip != null && !ip.isBlank()) {
            blacklistedIps.add(ip);
        }
    }

    public void removeIp(String ip) {
        if (ip != null) {
            blacklistedIps.remove(ip);
        }
    }

    private boolean isIpBlacklisted(InetSocketAddress remote) {
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        String ip = remote.getAddress().getHostAddress();
        return ip != null && blacklistedIps.contains(ip);
    }

    // --- Events ---

    /** Cancel the ping for a blacklisted IP — the server looks unreachable to them. */
    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        if (isIpBlacklisted(event.getConnection().getRemoteAddress())) {
            event.setResult(GenericResult.denied());
        }
    }

    /** Deny login from a blacklisted IP. */
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        InboundConnection conn = event.getConnection();
        InetSocketAddress remote = conn.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return;
        }
        String ip = remote.getAddress().getHostAddress();
        if (blacklistedIps.contains(ip)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(DENY));
        }
    }

    // --- UUID cache for online players (existing behavior) ---

    public void setBlacklisted(Player player, boolean blacklisted) {
        UUID uuid = player.getUniqueId();
        if (blacklisted) {
            blacklistedPlayers.add(uuid);
        } else {
            blacklistedPlayers.remove(uuid);
        }
    }

    public boolean isBlacklisted(Player player) {
        return blacklistedPlayers.contains(player.getUniqueId());
    }

    public Set<UUID> getBlacklistedPlayers() {
        return blacklistedPlayers;
    }
}