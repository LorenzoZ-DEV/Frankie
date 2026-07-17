package dev.lorenzz.frankie.blacklist;

import com.nickuc.login.api.nLoginAPI;
import com.nickuc.login.api.types.AccountData;
import com.nickuc.login.api.types.Identity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.database.BlacklistRepository;
import dev.lorenzz.frankie.database.BlacklistRepository.BlacklistRecord;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.listener.BlacklistListener;
import dev.lorenzz.frankie.util.Text;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles blacklisting logic: persists records, applies firewall blocks immediately,
 * and tracks in-memory blacklist state via listener.
 * Supports blacklisting by player name (online or offline via nLogin/PlayerRepository)
 * and by raw IP address.
 */
public class BlacklistAgent {
    private final BlacklistRepository blacklistRepo;
    private final PlayerRepository playerRepo;
    private final LuckPermsHook luckPermsHook;
    private final ProxyServer proxy;
    private final BlacklistListener blacklistListener;

    // nLogin API — optional, null if nLogin not installed
    private final nLoginAPI nloginApi;

    public BlacklistAgent(BlacklistRepository blacklistRepo,
                          PlayerRepository playerRepo,
                          LuckPermsHook luckPermsHook,
                          ProxyServer proxy,
                          BlacklistListener blacklistListener) {
        this.blacklistRepo = blacklistRepo;
        this.playerRepo = playerRepo;
        this.luckPermsHook = luckPermsHook;
        this.proxy = proxy;
        this.blacklistListener = blacklistListener;

        // Try to get nLogin API; null if plugin not loaded
        nLoginAPI api = null;
        try {
            api = nLoginAPI.getApi();
        } catch (Exception ignored) {
        }
        this.nloginApi = api;
    }

    // --- Online player blacklist (kept for internal use) ---

    public CompletableFuture<Long> blacklist(UUID issuerUuid, String issuerName, Player victim, String reason) {
        return doBlacklist(issuerUuid, issuerName, victim.getUniqueId(), victim.getUsername(),
                victim.getRemoteAddress().getAddress().getHostAddress(), reason);
    }

    // --- Blacklist by name (online or offline) ---

    public CompletableFuture<Long> blacklistByName(UUID issuerUuid, String issuerName, String victimName, String reason) {
        Player onlineVictim = proxy.getPlayer(victimName).orElse(null);
        if (onlineVictim != null) {
            return blacklist(issuerUuid, issuerName, onlineVictim, reason);
        }
        // Offline: resolve UUID and IP
        return playerRepo.findByName(victimName).thenCompose(cached -> {
            if (cached.isEmpty()) {
                throw new RuntimeException("Giocatore " + victimName + " mai visto sul server. Impossibile blacklistare.");
            }
            UUID victimUuid = cached.get().uuid();
            String ip = resolveIp(victimName);
            return doBlacklist(issuerUuid, issuerName, victimUuid, victimName, ip, reason);
        });
    }

    // --- Blacklist by raw IP ---

    public CompletableFuture<Long> blacklistByIp(UUID issuerUuid, String issuerName, String ip, String reason) {
        // Try to find known accounts on this IP via nLogin
        String victimName = ip; // fallback display name
        UUID victimUuid = UUID.nameUUIDFromBytes(ip.getBytes()); // deterministic placeholder

        if (nloginApi != null) {
            try {
                List<AccountData> accounts = nloginApi.getAccountsByIp(ip);
                if (!accounts.isEmpty()) {
                    AccountData first = accounts.get(0);
                    victimName = first.getLastName();
                    victimUuid = first.getUniqueId().orElse(victimUuid);
                }
            } catch (Exception ignored) {
            }
        }

        UUID finalUuid = victimUuid;
        String finalName = victimName;
        return doBlacklist(issuerUuid, issuerName, finalUuid, finalName, ip, reason);
    }

    // --- Core blacklist logic ---

    private CompletableFuture<Long> doBlacklist(UUID issuerUuid, String issuerName,
                                                 UUID victimUuid, String victimName,
                                                 String ip, String reason) {
        String suffix = luckPermsHook.getSuffixForName(victimName);

        return blacklistRepo.insert(issuerUuid, issuerName,
                victimUuid, victimName,
                ip, suffix, reason != null ? reason : "")
                .thenApplyAsync(generatedId -> {
                    // Mark the IP as blacklisted: ProxyPing + PreLogin listeners enforce it natively
                    blacklistListener.addIp(ip);

                    // Kick the victim if currently online
                    proxy.getPlayer(victimUuid).ifPresent(v -> {
                        blacklistListener.setBlacklisted(v, true);
                        v.disconnect(Text.colorize("&cIl tuo account è stato blacklistato da VenrixMC."));
                    });

                    return generatedId;
                });
    }

    // --- IP resolution helpers ---

    /** Try nLogin first, fall back to last known IP from blacklist records. */
    private String resolveIp(String playerName) {
        if (nloginApi != null) {
            try {
                String ip = nloginApi.getAddress(playerName);
                if (ip != null && !ip.isBlank()) {
                    return ip;
                }
            } catch (Exception ignored) {
            }
        }
        // Fallback: check if this player was previously blacklisted (has a known IP)
        try {
            Optional<BlacklistRecord> prev = blacklistRepo.findLatestByVictimName(playerName).get();
            if (prev.isPresent() && prev.get().ip != null && !prev.get().ip.isBlank()) {
                return prev.get().ip;
            }
        } catch (Exception ignored) {
        }
        // ponytail: last resort — firewall block won't work but DB record exists
        return "0.0.0.0";
    }

    // --- Unblacklist (unchanged) ---

    public CompletableFuture<Optional<BlacklistRecord>> unblacklistByName(String victimName) {
        return blacklistRepo.findLatestByVictimName(victimName)
                .thenCompose(optionalRecord -> {
                    if (optionalRecord.isPresent()) {
                        BlacklistRecord record = optionalRecord.get();
                        return unblacklist(record.victimUuid)
                                .thenApply(v -> Optional.of(record));
                    } else {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                });
    }

    public CompletableFuture<Void> unblacklist(UUID victimUuid) {
        return blacklistRepo.getLatestIpByVictim(victimUuid)
                .thenCompose(optionalIp -> {
                    optionalIp.ifPresent(blacklistListener::removeIp);
                    return blacklistRepo.deleteByVictim(victimUuid);
                })
                .thenCompose(deleteCount -> {
                    Player victim = proxy.getPlayer(victimUuid).orElse(null);
                    if (victim != null) {
                        blacklistListener.setBlacklisted(victim, false);
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    // --- Getters ---

    public ProxyServer getProxy() { return proxy; }
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public BlacklistListener getBlacklistListener() { return blacklistListener; }
}