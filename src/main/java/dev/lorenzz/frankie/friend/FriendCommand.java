package dev.lorenzz.frankie.friend;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpCommand;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.database.FriendRepository;
import dev.lorenzz.frankie.database.PlayerRepository.CachedPlayer;
import dev.lorenzz.frankie.player.PlayerResolver;
import dev.lorenzz.frankie.util.Futures;
import net.kyori.adventure.text.Component;

import java.util.function.Consumer;

@CommandAlias("friend|friends|amici")
public class FriendCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;
    private final FriendRepository friends;
    private final PlayerResolver resolver;
    private final YamlConfig config;
    private final Futures futures;

    public FriendCommand(ProxyServer proxy, Messages messages, FriendRepository friends,
                         PlayerResolver resolver, YamlConfig config, Futures futures) {
        this.proxy = proxy;
        this.messages = messages;
        this.friends = friends;
        this.resolver = resolver;
        this.config = config;
        this.futures = futures;
    }

    @Default
    @HelpCommand
    @Description("Mostra i comandi del sistema amici.")
    public void onHelp(Player player) {
        messages.getList("friend.help").forEach(player::sendMessage);
    }

    @Subcommand("add")
    @CommandCompletion("@players")
    @Description("Invia una richiesta di amicizia.")
    public void onAdd(Player player, String targetName) {
        if (targetName.equalsIgnoreCase(player.getUsername())) {
            player.sendMessage(messages.get("friend.self"));
            return;
        }
        withTarget(player, targetName, target ->
                futures.handle(friends.state(player.getUniqueId(), target.uuid()), player,
                        "friend state " + player.getUsername(), state -> {
                            if (state.areFriends()) {
                                player.sendMessage(messages.get("friend.already_friends",
                                        "%target%", target.name()));
                                return;
                            }
                            int maxFriends = config.getInt("friends.max-friends", 50);
                            if (state.friendCount() >= maxFriends) {
                                player.sendMessage(messages.get("friend.limit_reached",
                                        "%limit%", String.valueOf(maxFriends)));
                                return;
                            }
                            // They already asked us: adding back is an accept.
                            if (state.incoming()) {
                                accept(player, target);
                                return;
                            }
                            if (state.outgoing()) {
                                player.sendMessage(messages.get("friend.already_requested",
                                        "%target%", target.name()));
                                return;
                            }
                            futures.handle(friends.addRequest(player.getUniqueId(), target.uuid()), player,
                                    "friend request", done -> {
                                        player.sendMessage(messages.get("friend.request_sent",
                                                "%target%", target.name()));
                                        proxy.getPlayer(target.uuid()).ifPresent(online ->
                                                online.sendMessage(messages.get("friend.request_received",
                                                        "%user%", player.getUsername())));
                                    });
                        }));
    }

    @Subcommand("accept")
    @CommandCompletion("@players")
    @Description("Accetta una richiesta di amicizia.")
    public void onAccept(Player player, String targetName) {
        withTarget(player, targetName, target ->
                futures.handle(friends.hasRequest(target.uuid(), player.getUniqueId()), player,
                        "friend accept", pending -> {
                            if (!pending) {
                                player.sendMessage(messages.get("friend.no_request",
                                        "%target%", target.name()));
                                return;
                            }
                            accept(player, target);
                        }));
    }

    @Subcommand("deny")
    @CommandCompletion("@players")
    @Description("Rifiuta una richiesta di amicizia.")
    public void onDeny(Player player, String targetName) {
        withTarget(player, targetName, target ->
                futures.handle(friends.removeRequest(target.uuid(), player.getUniqueId()), player,
                        "friend deny", removed -> player.sendMessage(removed
                                ? messages.get("friend.request_denied", "%target%", target.name())
                                : messages.get("friend.no_request", "%target%", target.name()))));
    }

    @Subcommand("remove")
    @CommandCompletion("@players")
    @Description("Rimuove un amico.")
    public void onRemove(Player player, String targetName) {
        withTarget(player, targetName, target ->
                futures.handle(friends.removeFriends(player.getUniqueId(), target.uuid()), player,
                        "friend remove", removed -> {
                            if (!removed) {
                                player.sendMessage(messages.get("friend.not_friends",
                                        "%target%", target.name()));
                                return;
                            }
                            player.sendMessage(messages.get("friend.removed", "%target%", target.name()));
                            proxy.getPlayer(target.uuid()).ifPresent(online ->
                                    online.sendMessage(messages.get("friend.removed_by",
                                            "%user%", player.getUsername())));
                        }));
    }

    @Subcommand("list")
    @Description("Elenca i tuoi amici.")
    public void onList(Player player) {
        futures.handle(friends.getFriends(player.getUniqueId()), player, "friend list", list -> {
            if (list.isEmpty()) {
                player.sendMessage(messages.get("friend.list_empty"));
                return;
            }
            player.sendMessage(messages.get("friend.list_header", "%count%", String.valueOf(list.size())));
            for (CachedPlayer friend : list) {
                Component status = messages.get(proxy.getPlayer(friend.uuid()).isPresent()
                        ? "friend.status_online"
                        : "friend.status_offline");
                player.sendMessage(messages.get("friend.list_entry", "%user%", friend.name()).append(status));
            }
        });
    }

    @Subcommand("requests")
    @Description("Mostra le richieste di amicizia in sospeso.")
    public void onRequests(Player player) {
        futures.handle(friends.getIncomingRequests(player.getUniqueId()), player, "friend requests", list -> {
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

    private void accept(Player player, CachedPlayer target) {
        futures.handle(friends.removeRequest(target.uuid(), player.getUniqueId())
                        .thenCompose(ignored -> friends.addFriends(player.getUniqueId(), target.uuid())),
                player, "friend accept " + target.name(), done -> {
                    player.sendMessage(messages.get("friend.request_accepted", "%target%", target.name()));
                    proxy.getPlayer(target.uuid()).ifPresent(online ->
                            online.sendMessage(messages.get("friend.request_accepted_other",
                                    "%user%", player.getUsername())));
                });
    }

    /** Resolves a name, reporting "not found" once instead of at six call sites. */
    private void withTarget(Player player, String targetName, Consumer<CachedPlayer> action) {
        futures.handle(resolver.resolve(targetName), player, "resolve " + targetName, target -> {
            if (target.isEmpty()) {
                player.sendMessage(messages.get("friend.player_not_found", "%target%", targetName));
                return;
            }
            if (target.get().uuid().equals(player.getUniqueId())) {
                player.sendMessage(messages.get("friend.self"));
                return;
            }
            action.accept(target.get());
        });
    }
}
