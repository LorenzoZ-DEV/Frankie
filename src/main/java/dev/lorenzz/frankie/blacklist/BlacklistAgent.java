package dev.lorenzz.frankie.blacklist;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.database.BlacklistRepository;
import dev.lorenzz.frankie.database.BlacklistRepository.BlacklistRecord;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.listener.BlacklistListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles blacklisting logic: persists records, schedules firewall blocks,
 * and tracks in-memory blacklist state via listener.
 */
public class BlacklistAgent {
    private final ScheduledExecutorService scheduler;
    private final BlacklistRepository blacklistRepo;
    private final LuckPermsHook luckPermsHook;
    private final FirewallUpdater firewallUpdater;
    private final ProxyServer proxy;
    private final BlacklistListener blacklistListener;
    /** Map of IP to scheduled future for pending firewall block (to allow cancellation). */
    private final Map<String, ScheduledFuture<?>> pendingBlocks = new ConcurrentHashMap<>();

    public BlacklistAgent(ScheduledExecutorService scheduler,
                          BlacklistRepository blacklistRepo,
                          LuckPermsHook luckPermsHook,
                          FirewallUpdater firewallUpdater,
                          ProxyServer proxy,
                          BlacklistListener blacklistListener) {
        this.scheduler = scheduler;
        this.blacklistRepo = blacklistRepo;
        this.luckPermsHook = luckPermsHook;
        this.firewallUpdater = firewallUpdater;
        this.proxy = proxy;
        this.blacklistListener = blacklistListener;
    }

    /**
     * Call when a player should be blacklisted.
     * Persists record (issuer, victim, IP, LuckPerms suffix, reason),
     * then schedules firewall block after 1 minute.
     * Updates the blacklist listener cache immediately.
     * Returns a future that completes with the generated database ID.
     */
    public CompletableFuture<Long> blacklist(Player issuer, Player victim, String reason) {
        String issuerName = issuer.getUsername();
        UUID issuerUuid = issuer.getUniqueId();
        UUID victimUuid = victim.getUniqueId();
        String victimNameActual = victim.getUsername();
        String ip = victim.getRemoteAddress().getAddress().getHostAddress();
        String suffix = luckPermsHook.getSuffixForName(victimNameActual);

        // Persist record and schedule firewall block after insert completes
        return blacklistRepo.insert(issuerUuid, issuerName,
                victimUuid, victimNameActual,
                ip, suffix, reason != null ? reason : "")
                .thenApplyAsync(generatedId -> {
                    // Schedule firewall block after 60 seconds from now
                    ScheduledFuture<?> future = scheduler.schedule(() -> {
                        firewallUpdater.blockIp(ip);
                        // Remove from pending map after execution (or cancellation will remove)
                        pendingBlocks.remove(ip);
                    }, 60, TimeUnit.SECONDS);
                    pendingBlocks.put(ip, future);

                    // Update cache
                    blacklistListener.setBlacklisted(victim, true);

                    return generatedId;
                });
    }

    /**
     * Call when a player should be unblacklisted by name (works for offline players).
     * Removes all blacklist records for the victim,
     * cancels any pending firewall block,
     * removes existing firewall block if any,
     * and updates the blacklist listener cache if the victim is online.
     * Returns an Optional containing the blacklist record that was removed (if any).
     */
    public CompletableFuture<Optional<BlacklistRecord>> unblacklistByName(Player issuer, String victimName) {
        return blacklistRepo.findLatestByVictimName(victimName)
                .thenCompose(optionalRecord -> {
                    if (optionalRecord.isPresent()) {
                        BlacklistRecord record = optionalRecord.get();
                        return unblacklist(issuer, record.victimUuid)
                                .thenApply(v -> Optional.of(record));
                    } else {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                });
    }

    /**
     * Call when a player should be unblacklisted.
     * Removes all blacklist records for the victim,
     * cancels any pending firewall block,
     * removes existing firewall block if any,
     * and updates the blacklist listener cache if the victim is online.
     * Returns a future that completes when all async operations finish.
     */
    public CompletableFuture<Void> unblacklist(Player issuer, UUID victimUuid) {
        // Get the most recent IP for this victim (if any)
        return blacklistRepo.getLatestIpByVictim(victimUuid)
                .thenCompose(optionalIp -> {
                    // Step 1: Cancel any pending scheduled block and remove from map
                    optionalIp.ifPresent(ip -> {
                        ScheduledFuture<?> future = pendingBlocks.remove(ip);
                        if (future != null) {
                            future.cancel(false); // cancel if not already executed
                        }
                        // Step 2: Unblock IP in firewall (ignore errors if not blocked)
                        try {
                            firewallUpdater.unblockIp(ip);
                        } catch (Exception e) {
                            // Log but continue
                            System.err.println("[BlacklistAgent] Failed to unblock IP " + ip + ": " + e);
                        }
                    });
                    // Step 3: Delete blacklist records from database
                    return blacklistRepo.deleteByVictim(victimUuid);
                })
                .thenCompose(deleteCount -> {
                    // Step 4: Update cache if victim is online
                    Player victim = proxy.getPlayer(victimUuid).orElse(null);
                    if (victim != null) {
                        blacklistListener.setBlacklisted(victim, false);
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    private void issuelog(String msg) {
        // Simple logging; replace with proper logger if available
        System.out.println("[BlacklistAgent] " + msg);
    }
}