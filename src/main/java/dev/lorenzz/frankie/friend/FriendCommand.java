package dev.lorenzz.frankie.friend;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.database.FriendRepository;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.database.PlayerRepository.CachedPlayer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@CommandAlias("friend|friends|amici")
public class FriendCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;
    private final FriendRepository friends;
    private final PlayerRepository players;
    private final YamlConfig config;

    public FriendCommand(ProxyServer proxy, Messages messages, FriendRepository friends,
                         PlayerRepository players, YamlConfig config) {
        this.proxy = proxy;
        this.messages = messages;
        this.friends = friends;
        this.players = players;
        this.config = config;
    }

    @Default
    @HelpCommand
    public void onHelp(Player player) {
        messages.getList("friend.help").forEach(player::sendMessage);
    }

    @Subcommand("add")
    @CommandCompletion("@players")
    public void onAdd(Player player, String targetName) {
        if (targetName.equalsIgnoreCase(player.getUsername())) {
            player.sendMessage(messages.get("friend.self"));
            return;
        }
        resolve(targetName).thenAccept(resolved -> {
            if (resolved.isEmpty()) {
                player.sendMessage(messages.get("friend.player_not_found", "%target%", targetName));
                return;
            }
            CachedPlayer target = resolved.get();

            friends.areFriends(player.getUniqueId(), target.uuid()).thenAccept(alreadyFriends -> {
                if (alreadyFriends) {
                    player.sendMessage(messages.get("friend.already_friends", "%target%", target.name()));
                    return;
                }
                int maxFriends = config.getInt("friends.max-friends", 50);
                friends.countFriends(player.getUniqueId()).thenAccept(count -> {
                    if (count >= maxFriends) {
                        player.sendMessage(messages.get("friend.limit_reached",
                                "%limit%", String.valueOf(maxFriends)));
                        return;
                    }
                    // Reverse request pending? Adding back counts as accepting.
                    friends.hasRequest(target.uuid(), player.getUniqueId()).thenAccept(reverse -> {
                        if (reverse) {
                            accept(player, target);
                            return;
                        }
                        friends.hasRequest(player.getUniqueId(), target.uuid()).thenAccept(pending -> {
                            if (pending) {
                                player.sendMessage(messages.get("friend.already_requested",
                                        "%target%", target.name()));
                                return;
                            }
                            friends.addRequest(player.getUniqueId(), target.uuid()).thenRun(() -> {
                                player.sendMessage(messages.get("friend.request_sent",
                                        "%target%", target.name()));
                                proxy.getPlayer(target.uuid()).ifPresent(online ->
                                        online.sendMessage(messages.get("friend.request_received",
                                                "%user%", player.getUsername())));
                            });
                        });
                    });
                });
            });
        });
    }

    @Subcommand("accept")
    @CommandCompletion("@players")
    public void onAccept(Player player, String targetName) {
        resolve(targetName).thenAccept(resolved -> {
            if (resolved.isEmpty()) {
                player.sendMessage(messages.get("friend.player_not_found", "%target%", targetName));
                return;
            }
            CachedPlayer target = resolved.get();
            friends.hasRequest(target.uuid(), player.getUniqueId()).thenAccept(pending -> {
                if (!pending) {
                    player.sendMessage(messages.get("friend.no_request", "%target%", target.name()));
                    return;
                }
                accept(player, target);
            });
        });
    }

    private void accept(Player player, CachedPlayer target) {
        friends.removeRequest(target.uuid(), player.getUniqueId())
                .thenCompose(ignored -> friends.addFriends(player.getUniqueId(), target.uuid()))
                .thenRun(() -> {
                    player.sendMessage(messages.get("friend.request_accepted", "%target%", target.name()));
                    proxy.getPlayer(target.uuid()).ifPresent(online ->
                            online.sendMessage(messages.get("friend.request_accepted_other",
                                    "%user%", player.getUsername())));
                });
    }

    @Subcommand("deny")
    @CommandCompletion("@players")
    public void onDeny(Player player, String targetName) {
        resolve(targetName).thenAccept(resolved -> {
            if (resolved.isEmpty()) {
                player.sendMessage(messages.get("friend.player_not_found", "%target%", targetName));
                return;
            }
            CachedPlayer target = resolved.get();
            friends.removeRequest(target.uuid(), player.getUniqueId()).thenAccept(removed -> {
                if (!removed) {
                    player.sendMessage(messages.get("friend.no_request", "%target%", target.name()));
                    return;
                }
                player.sendMessage(messages.get("friend.request_denied", "%target%", target.name()));
            });
        });
    }

    @Subcommand("remove")
    @CommandCompletion("@players")
    public void onRemove(Player player, String targetName) {
        resolve(targetName).thenAccept(resolved -> {
            if (resolved.isEmpty()) {
                player.sendMessage(messages.get("friend.player_not_found", "%target%", targetName));
                return;
            }
            CachedPlayer target = resolved.get();
            friends.removeFriends(player.getUniqueId(), target.uuid()).thenAccept(removed -> {
                if (!removed) {
                    player.sendMessage(messages.get("friend.not_friends", "%target%", target.name()));
                    return;
                }
                player.sendMessage(messages.get("friend.removed", "%target%", target.name()));
                proxy.getPlayer(target.uuid()).ifPresent(online ->
                        online.sendMessage(messages.get("friend.removed_by",
                                "%user%", player.getUsername())));
            });
        });
    }

    @Subcommand("list")
    public void onList(Player player) {
        friends.getFriends(player.getUniqueId()).thenAccept(list -> {
            if (list.isEmpty()) {
                player.sendMessage(messages.get("friend.list_empty"));
                return;
            }
            player.sendMessage(messages.get("friend.list_header", "%count%", String.valueOf(list.size())));
            for (CachedPlayer friend : list) {
                boolean online = proxy.getPlayer(friend.uuid()).isPresent();
                Component status = messages.get(online ? "friend.status_online" : "friend.status_offline");
                player.sendMessage(messages.get("friend.list_entry", "%user%", friend.name())
                        .append(status));
            }
        });
    }

    @Subcommand("requests")
    public void onRequests(Player player) {
        friends.getIncomingRequests(player.getUniqueId()).thenAccept(list -> {
            if (list.isEmpty()) {
                player.sendMessage(messages.get("friend.requests_empty"));
                return;
            }
            player.sendMessage(messages.get("friend.requests_header", "%count%", String.valueOf(list.size())));
            for (CachedPlayer sender : list) {
                player.sendMessage(messages.get("friend.requests_entry", "%user%", sender.name()));
            }
        });
    }

    /** Online lookup first, then the players table for offline targets. */
    private CompletableFuture<Optional<CachedPlayer>> resolve(String name) {
        Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            Player found = online.get();
            return CompletableFuture.completedFuture(
                    Optional.of(new CachedPlayer(found.getUniqueId(), found.getUsername())));
        }
        return players.findByName(name);
    }
}