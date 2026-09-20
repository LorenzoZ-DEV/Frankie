package dev.lorenzz.frankie.staffchat;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.util.Broadcast;
import net.kyori.adventure.text.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Holds the state of every staff chat room: who is talking into which one, who
 * has hidden which one, and which ones are muted.
 *
 * <p>One manager serves all three {@link StaffChannel}s. Talk mode is
 * deliberately exclusive — a player types into at most one room at a time, so
 * {@code /ac talk} while in staff talk mode moves them rather than duplicating
 * their messages into both.
 */
public class StaffChatManager {

    /** Kept as a constant: the join/quit broadcasts key off the staff room. */
    public static final String PERMISSION = "frankie.staffchat";

    private final ProxyServer proxy;
    private final Messages messages;
    private final LuckPermsHook luckPerms;

    /** The room a player's normal chat is redirected to, if any. */
    private final Map<UUID, StaffChannel> talkMode = new ConcurrentHashMap<>();
    /** Per room, the players who hid it. */
    private final Map<StaffChannel, Set<UUID>> hidden = new EnumMap<>(StaffChannel.class);
    private final Set<StaffChannel> muted = ConcurrentHashMap.newKeySet();

    /**
     * Notified on every talk-mode change: the backend companions enforce talk
     * mode and need the new value, since 1.19.1+ chat cannot be cancelled here.
     */
    private final List<BiConsumer<Player, StaffChannel>> talkListeners = new CopyOnWriteArrayList<>();

    public StaffChatManager(ProxyServer proxy, Messages messages, LuckPermsHook luckPerms) {
        this.proxy = proxy;
        this.messages = messages;
        this.luckPerms = luckPerms;
        for (StaffChannel channel : StaffChannel.values()) {
            hidden.put(channel, ConcurrentHashMap.newKeySet());
        }
    }

    // --- Talk mode ---

    /** The room this player is typing into, empty when they chat normally. */
    public Optional<StaffChannel> talkChannel(Player player) {
        return Optional.ofNullable(talkMode.get(player.getUniqueId()));
    }

    /**
     * Toggles talk mode for one room.
     *
     * @return true when the player is now typing into {@code channel}
     */
    public boolean toggleTalkMode(Player player, StaffChannel channel) {
        StaffChannel previous = talkMode.get(player.getUniqueId());
        StaffChannel now = channel == previous ? null : channel;
        if (now == null) {
            talkMode.remove(player.getUniqueId());
        } else {
            talkMode.put(player.getUniqueId(), now);
        }
        fireTalkChanged(player, now);
        return now != null;
    }

    /** Registers a listener fired whenever a player's talk room changes. */
    public void onTalkChange(BiConsumer<Player, StaffChannel> listener) {
        talkListeners.add(listener);
    }

    private void fireTalkChanged(Player player, StaffChannel channel) {
        for (BiConsumer<Player, StaffChannel> listener : talkListeners) {
            listener.accept(player, channel);
        }
    }

    // --- Visibility and mute ---

    /** @return true if the room is now visible for the player */
    public boolean toggleVisibility(Player player, StaffChannel channel) {
        Set<UUID> viewers = hidden.get(channel);
        if (viewers.remove(player.getUniqueId())) {
            return true;
        }
        viewers.add(player.getUniqueId());
        return false;
    }

    public boolean isMuted(StaffChannel channel) {
        return muted.contains(channel);
    }

    /** @return true if the room is now muted */
    public boolean toggleMute(StaffChannel channel) {
        if (muted.add(channel)) {
            return true;
        }
        muted.remove(channel);
        return false;
    }

    /** Whether a mute currently stops this player from writing in the room. */
    public boolean canSpeak(Player player, StaffChannel channel) {
        return !isMuted(channel) || player.hasPermission(channel.muteBypassPermission());
    }

    public void cleanup(Player player) {
        talkMode.remove(player.getUniqueId());
        for (Set<UUID> viewers : hidden.values()) {
            viewers.remove(player.getUniqueId());
        }
    }

    // --- Sending ---

    public void sendPlayerMessage(StaffChannel channel, Player sender, String message) {
        String server = sender.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("?");
        broadcast(channel, messages.get(channel.key("format"),
                "%userprefix%", luckPerms.prefix(sender),
                "%usersuffix%", luckPerms.suffix(sender),
                "%displayname%", luckPerms.displayName(sender),
                "%user%", sender.getUsername(),
                "%server%", server,
                "%message%", message));
    }

    public void sendConsoleMessage(StaffChannel channel, String message) {
        broadcast(channel, messages.get(channel.key("console_format"),
                "%user%", "Console",
                "%server%", "Proxy",
                "%message%", message));
    }

    /** Sends to console and every member who has not hidden the room. */
    public void broadcast(StaffChannel channel, Component component) {
        Set<UUID> viewers = hidden.get(channel);
        proxy.getConsoleCommandSource().sendMessage(component);
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(channel.permission()) && !viewers.contains(player.getUniqueId())) {
                player.sendMessage(component);
            }
        }
    }

    /** Sends to console and every member, ignoring visibility toggles. */
    public void broadcastStatus(StaffChannel channel, Component component) {
        Broadcast.toPermission(proxy, channel.permission(), component);
    }

    /** The staff room's status broadcast: join, quit and server switch. */
    public void broadcastStatus(Component component) {
        broadcastStatus(StaffChannel.STAFF, component);
    }
}
