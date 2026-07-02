package dev.lorenzz.frankie.staffchat;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.util.Broadcast;
import net.kyori.adventure.text.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffChatManager {

    public static final String PERMISSION = "frankie.staffchat";
    public static final String PERMISSION_MUTE = "frankie.staffchat.mute";
    public static final String PERMISSION_MUTE_BYPASS = "frankie.staffchat.mute.bypass";

    private final ProxyServer proxy;
    private final Messages messages;
    private final LuckPermsHook luckPerms;

    /** Players whose normal chat is redirected to the staffchat. */
    private final Set<UUID> talkMode = ConcurrentHashMap.newKeySet();
    /** Players who hid the staffchat. */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private volatile boolean muted;

    public StaffChatManager(ProxyServer proxy, Messages messages, LuckPermsHook luckPerms) {
        this.proxy = proxy;
        this.messages = messages;
        this.luckPerms = luckPerms;
    }

    public boolean isTalkMode(Player player) {
        return talkMode.contains(player.getUniqueId());
    }

    /** @return true if talk mode is now enabled */
    public boolean toggleTalkMode(Player player) {
        if (!talkMode.remove(player.getUniqueId())) {
            talkMode.add(player.getUniqueId());
            return true;
        }
        return false;
    }

    /** @return true if the staffchat is now visible for the player */
    public boolean toggleVisibility(Player player) {
        if (!hidden.remove(player.getUniqueId())) {
            hidden.add(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean isMuted() {
        return muted;
    }

    /** @return true if the staffchat is now muted */
    public boolean toggleMute() {
        muted = !muted;
        return muted;
    }

    public void cleanup(Player player) {
        talkMode.remove(player.getUniqueId());
        hidden.remove(player.getUniqueId());
    }

    public void sendPlayerMessage(Player sender, String message) {
        String server = sender.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("?");
        broadcast(messages.get("staffchat.staffchat_format",
                "%userprefix%", luckPerms.prefix(sender),
                "%usersuffix%", luckPerms.suffix(sender),
                "%displayname%", luckPerms.displayName(sender),
                "%user%", sender.getUsername(),
                "%server%", server,
                "%message%", message));
    }

    public void sendConsoleMessage(String message) {
        broadcast(messages.get("staffchat.staffchat_console_format",
                "%user%", "Console",
                "%server%", "Proxy",
                "%message%", message));
    }

    /** Sends to console and every staff member who has not hidden the staffchat. */
    public void broadcast(Component component) {
        proxy.getConsoleCommandSource().sendMessage(component);
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(PERMISSION) && !hidden.contains(player.getUniqueId())) {
                player.sendMessage(component);
            }
        }
    }

    /** Sends to console and every staff member, ignoring visibility toggles. */
    public void broadcastStatus(Component component) {
        Broadcast.toPermission(proxy, PERMISSION, component);
    }
}