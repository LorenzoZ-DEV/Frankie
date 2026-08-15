package dev.lorenzz.frankie.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.core.FrankieServices;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.staffchat.StaffChatManager;
import net.kyori.adventure.text.Component;

/**
 * Staff join/quit/switch broadcasts, friend notifications, the player cache
 * update and per-player state cleanup.
 *
 * <p>This listener is core, not part of the friends module. It used to be
 * registered only when {@code modules.friends} was on, which silently took the
 * staff broadcasts and — more importantly — the uuid/name/IP cache down with it;
 * that cache is what lets the blacklist reach offline accounts.
 */
public class ConnectionListener {

    private final FrankieServices services;

    public ConnectionListener(FrankieServices services) {
        this.services = services;
    }

    /**
     * Records name and address on every login. The address is what lets a
     * blacklist reach an account that is currently offline.
     */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String ip = player.getRemoteAddress() == null || player.getRemoteAddress().getAddress() == null
                ? null
                : player.getRemoteAddress().getAddress().getHostAddress();
        services.futures.handle(
                services.players.upsert(player.getUniqueId(), player.getUsername(), ip),
                "player cache upsert for " + player.getUsername());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();
        boolean isStaff = player.hasPermission(StaffChatManager.PERMISSION);

        if (event.getPreviousServer().isPresent()) {
            if (isStaff) {
                services.staffChat.broadcastStatus(services.messages.get(
                        "staffchat.staff_switch_message_format",
                        "%userprefix%", services.luckPerms.prefix(player),
                        "%usersuffix%", services.luckPerms.suffix(player),
                        "%user%", player.getUsername(),
                        "%serverbefore%", event.getPreviousServer().get().getServerInfo().getName(),
                        "%server%", server));
            }
            return;
        }

        // First server after login: this is the network join.
        if (isStaff) {
            services.staffChat.broadcastStatus(services.messages.get(
                    "staffchat.staff_join_message_format",
                    "%userprefix%", services.luckPerms.prefix(player),
                    "%usersuffix%", services.luckPerms.suffix(player),
                    "%user%", player.getUsername(),
                    "%server%", server));
        }
        notifyFriends(player, true);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        services.staffChat.cleanup(player);
        services.privateMessages.cleanup(player.getUniqueId());

        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return;
        }
        if (player.hasPermission(StaffChatManager.PERMISSION)) {
            services.staffChat.broadcastStatus(services.messages.get(
                    "staffchat.staff_quit_message_format",
                    "%userprefix%", services.luckPerms.prefix(player),
                    "%usersuffix%", services.luckPerms.suffix(player),
                    "%user%", player.getUsername()));
        }
        notifyFriends(player, false);
    }

    private void notifyFriends(Player player, boolean online) {
        if (!services.module("friends")) {
            return;
        }
        services.futures.handle(services.friends.getFriends(player.getUniqueId()), null,
                "friend notification for " + player.getUsername(), list -> {
                    if (list.isEmpty()) {
                        return;
                    }
                    Component notification = services.messages.get(
                            online ? "friend.friend_online" : "friend.friend_offline",
                            "%user%", player.getUsername());
                    for (PlayerRepository.CachedPlayer friend : list) {
                        services.proxy.getPlayer(friend.uuid())
                                .ifPresent(target -> target.sendMessage(notification));
                    }
                });
    }
}
