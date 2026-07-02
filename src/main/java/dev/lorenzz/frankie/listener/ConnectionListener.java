package dev.lorenzz.frankie.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.database.FriendRepository;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.staffchat.StaffChatManager;
import net.kyori.adventure.text.Component;

/**
 * Staff join/quit/switch broadcasts, friend online/offline notifications and
 * the uuid/name cache updates.
 */
public class ConnectionListener {

    private final ProxyServer proxy;
    private final Messages messages;
    private final StaffChatManager staffChat;
    private final FriendRepository friends;
    private final PlayerRepository players;
    private final LuckPermsHook luckPerms;

    public ConnectionListener(ProxyServer proxy, Messages messages, StaffChatManager staffChat,
                              FriendRepository friends, PlayerRepository players, LuckPermsHook luckPerms) {
        this.proxy = proxy;
        this.messages = messages;
        this.staffChat = staffChat;
        this.friends = friends;
        this.players = players;
        this.luckPerms = luckPerms;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        players.upsert(event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();

        if (event.getPreviousServer().isPresent()) {
            if (player.hasPermission(StaffChatManager.PERMISSION)) {
                staffChat.broadcastStatus(messages.get("staffchat.staff_switch_message_format",
                        "%userprefix%", luckPerms.prefix(player),
                        "%usersuffix%", luckPerms.suffix(player),
                        "%user%", player.getUsername(),
                        "%serverbefore%", event.getPreviousServer().get().getServerInfo().getName(),
                        "%server%", server));
            }
            return;
        }

        // First server after login: network join.
        if (player.hasPermission(StaffChatManager.PERMISSION)) {
            staffChat.broadcastStatus(messages.get("staffchat.staff_join_message_format",
                    "%userprefix%", luckPerms.prefix(player),
                    "%usersuffix%", luckPerms.suffix(player),
                    "%user%", player.getUsername(),
                    "%server%", server));
        }
        notifyFriends(player, true);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        staffChat.cleanup(player);
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return;
        }
        if (player.hasPermission(StaffChatManager.PERMISSION)) {
            staffChat.broadcastStatus(messages.get("staffchat.staff_quit_message_format",
                    "%userprefix%", luckPerms.prefix(player),
                    "%usersuffix%", luckPerms.suffix(player),
                    "%user%", player.getUsername()));
        }
        notifyFriends(player, false);
    }

    private void notifyFriends(Player player, boolean online) {
        friends.getFriends(player.getUniqueId()).thenAccept(list -> {
            if (list.isEmpty()) {
                return;
            }
            Component notification = messages.get(online ? "friend.friend_online" : "friend.friend_offline",
                    "%user%", player.getUsername());
            for (PlayerRepository.CachedPlayer friend : list) {
                proxy.getPlayer(friend.uuid()).ifPresent(target -> target.sendMessage(notification));
            }
        });
    }
}