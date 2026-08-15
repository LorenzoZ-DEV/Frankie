package dev.lorenzz.frankie.blacklist;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.database.BlacklistRepository;
import dev.lorenzz.frankie.database.BlacklistRepository.BlacklistRecord;
import dev.lorenzz.frankie.database.BlacklistRepository.Snapshot;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.hook.NLoginHook;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Applies and lifts blacklists: resolves the target's identity, persists the
 * record, updates the {@link BlacklistGuard} cache and kicks the victim.
 *
 * <p>Everything is composed asynchronously. The previous version called
 * {@code CompletableFuture.get()} while already running on a pool thread to
 * resolve an IP, blocking that thread on another query from the same pool.
 */
public class BlacklistAgent {

    private final BlacklistRepository blacklistRepo;
    private final PlayerRepository playerRepo;
    private final LuckPermsHook luckPerms;
    private final NLoginHook nLogin;
    private final ProxyServer proxy;
    private final BlacklistGuard guard;

    public BlacklistAgent(BlacklistRepository blacklistRepo, PlayerRepository playerRepo,
                          LuckPermsHook luckPerms, NLoginHook nLogin, ProxyServer proxy,
                          BlacklistGuard guard) {
        this.blacklistRepo = blacklistRepo;
        this.playerRepo = playerRepo;
        this.luckPerms = luckPerms;
        this.nLogin = nLogin;
        this.proxy = proxy;
        this.guard = guard;
    }

    /** Blacklists a player by name, online or not. */
    public CompletableFuture<BlacklistResult> blacklistByName(UUID issuerUuid, String issuerName,
                                                              String victimName, String reason) {
        Player online = proxy.getPlayer(victimName).orElse(null);
        if (online != null) {
            return apply(issuerUuid, issuerName, online.getUniqueId(), online.getUsername(),
                    addressOf(online), reason);
        }
        return playerRepo.findByName(victimName).thenCompose(cached -> {
            if (cached.isEmpty()) {
                throw new UnknownPlayerException(victimName);
            }
            return resolveIp(victimName).thenCompose(ip ->
                    apply(issuerUuid, issuerName, cached.get().uuid(), cached.get().name(), ip, reason));
        });
    }

    /**
     * Blacklists a raw address. When nLogin can name an account behind it the
     * record is attributed to that account, otherwise the IP itself is stored as
     * the victim with a deterministic placeholder uuid.
     */
    public CompletableFuture<BlacklistResult> blacklistByIp(UUID issuerUuid, String issuerName,
                                                            String ip, String reason) {
        Optional<NLoginHook.Account> account = nLogin.accountOnIp(ip);
        String victimName = account.map(NLoginHook.Account::name).filter(n -> n != null && !n.isBlank()).orElse(ip);
        UUID victimUuid = account.map(NLoginHook.Account::uuid)
                .filter(java.util.Objects::nonNull)
                .orElseGet(() -> UUID.nameUUIDFromBytes(("frankie-ip:" + ip).getBytes()));
        return apply(issuerUuid, issuerName, victimUuid, victimName, ip, reason);
    }

    private CompletableFuture<BlacklistResult> apply(UUID issuerUuid, String issuerName,
                                                     UUID victimUuid, String victimName,
                                                     String ip, String reason) {
        String suffix = luckPerms.getSuffixForName(victimName);
        String safeReason = reason == null ? "" : reason;
        return blacklistRepo.insert(issuerUuid, issuerName, victimUuid, victimName, ip, suffix, safeReason)
                .thenApply(id -> {
                    guard.remember(victimUuid, victimName, ip);
                    proxy.getPlayer(victimUuid).ifPresent(victim -> victim.disconnect(guard.denyScreen()));
                    return new BlacklistResult(id, victimUuid, victimName, suffix, ip, safeReason);
                });
    }

    /**
     * Best effort address for an offline player: our own login cache first (it
     * is written on every join), then nLogin, then whatever a previous blacklist
     * recorded.
     */
    private CompletableFuture<String> resolveIp(String victimName) {
        return playerRepo.lastIpByName(victimName).thenCompose(cached -> {
            if (cached.isPresent()) {
                return CompletableFuture.completedFuture(cached.get());
            }
            Optional<String> fromNLogin = nLogin.addressOf(victimName);
            if (fromNLogin.isPresent()) {
                return CompletableFuture.completedFuture(fromNLogin.get());
            }
            return blacklistRepo.findLatestByVictimName(victimName)
                    .thenApply(previous -> previous
                            .map(BlacklistRecord::ip)
                            .filter(recorded -> !recorded.isBlank())
                            // Name and uuid still block them; only the IP is unknown.
                            .orElse(Snapshot.PLACEHOLDER_IP));
        });
    }

    /** Lifts every blacklist on a name. Empty when the player was not listed. */
    public CompletableFuture<Optional<BlacklistRecord>> unblacklistByName(String victimName, String removedBy) {
        return lift(blacklistRepo.findActiveByVictimName(victimName), removedBy);
    }

    /**
     * Lifts every blacklist on an account. Used by the GUI, which identifies a
     * victim by uuid — the only identity that survives a rename.
     */
    public CompletableFuture<Optional<BlacklistRecord>> unblacklistByUuid(UUID victimUuid, String removedBy) {
        return lift(blacklistRepo.findActiveByVictimUuid(victimUuid), removedBy);
    }

    private CompletableFuture<Optional<BlacklistRecord>> lift(
            CompletableFuture<Optional<BlacklistRecord>> lookup, String removedBy) {
        return lookup.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<BlacklistRecord>empty());
            }
            BlacklistRecord record = found.get();
            return blacklistRepo.liftByVictim(record.victimUuid(), removedBy).thenApply(removal -> {
                guard.forget(record.victimUuid(), removal);
                return Optional.of(record);
            });
        });
    }

    private static String addressOf(Player player) {
        if (player.getRemoteAddress() == null || player.getRemoteAddress().getAddress() == null) {
            return Snapshot.PLACEHOLDER_IP;
        }
        return player.getRemoteAddress().getAddress().getHostAddress();
    }

    /** Outcome of a successful blacklist, used to render the confirmation. */
    public record BlacklistResult(long id, UUID victimUuid, String victimName,
                                  String victimSuffix, String ip, String reason) {
    }

    /** The target has never connected, so there is no identity to blacklist. */
    public static class UnknownPlayerException extends RuntimeException {
        private final String playerName;

        public UnknownPlayerException(String playerName) {
            super("Unknown player: " + playerName);
            this.playerName = playerName;
        }

        public String playerName() {
            return playerName;
        }
    }
}
