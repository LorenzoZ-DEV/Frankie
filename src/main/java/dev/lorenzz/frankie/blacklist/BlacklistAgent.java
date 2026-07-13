package dev.lorenzz.frankie.blacklist;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.database.BlacklistRepository;
import dev.lorenzz.frankie.hook.LuckPermsHook;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BlacklistAgent {
    private final ScheduledExecutorService scheduler;
    private final BlacklistRepository blacklistRepo;
    private final LuckPermsHook luckPermsHook;
    private final FirewallUpdater firewallUpdater;
    private final ProxyServer proxy;

    public BlacklistAgent(ScheduledExecutorService scheduler,
                          BlacklistRepository blacklistRepo,
                          LuckPermsHook luckPermsHook,
                          FirewallUpdater firewallUpdater,
                          ProxyServer proxy) {
        this.scheduler = scheduler;
        this.blacklistRepo = blacklistRepo;
        this.luckPermsHook = luckPermsHook;
        this.firewallUpdater = firewallUpdater;
        this.proxy = proxy;
    }

    /**
     * Call when a player should be blacklisted.
     * Persists record (issuer, victim, IP, LuckPerms suffix, reason),
     * then schedules firewall block after 1 minute.
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
                    scheduler.schedule(() -> firewallUpdater.blockIp(ip), 60, TimeUnit.SECONDS);
                    return generatedId;
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