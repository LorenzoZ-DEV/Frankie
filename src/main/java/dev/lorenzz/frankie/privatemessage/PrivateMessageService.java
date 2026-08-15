package dev.lorenzz.frankie.privatemessage;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivery and reply-tracking for private messages.
 *
 * <p>/msg and /reply used to hold identical copies of the formatting, delivery
 * and spy logic; both also sent their output as {@code Component.text(...)} of a
 * legacy-coded string, which showed players the raw {@code &8[&d&l✉} codes.
 */
public class PrivateMessageService {

    public static final String PERMISSION_SPY = "frankie.privatemessage.spy";

    private final ProxyServer proxy;
    private final Messages messages;

    /**
     * Who each player last talked to. Concurrent because chat and command
     * handlers run on different Netty threads; the previous {@link java.util.HashMap}
     * could corrupt under concurrent writes.
     */
    private final Map<UUID, UUID> lastTalker = new ConcurrentHashMap<>();

    public PrivateMessageService(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    /** Formats, delivers and mirrors a private message to spies. */
    public void send(Player sender, Player target, String message) {
        sender.sendMessage(messages.get("privatemessages.format-sent",
                "%target%", target.getUsername(),
                "%message%", message));
        target.sendMessage(messages.get("privatemessages.format-received",
                "%target%", sender.getUsername(),
                "%message%", message));

        lastTalker.put(sender.getUniqueId(), target.getUniqueId());
        lastTalker.put(target.getUniqueId(), sender.getUniqueId());

        spy(sender, target, message);
    }

    private void spy(Player sender, Player target, String message) {
        Component spyMessage = messages.get("privatemessages.spy-format",
                "%player%", sender.getUsername(),
                "%target%", target.getUsername(),
                "%message%", message);
        for (Player watcher : proxy.getAllPlayers()) {
            if (!watcher.hasPermission(PERMISSION_SPY)) {
                continue;
            }
            // Don't echo the conversation back to its own participants.
            if (watcher.getUniqueId().equals(sender.getUniqueId())
                    || watcher.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            watcher.sendMessage(spyMessage);
        }
    }

    public UUID lastTalker(UUID playerUuid) {
        return lastTalker.get(playerUuid);
    }

    /**
     * Drops a player's conversation state on disconnect. Without this the map
     * grew for the lifetime of the proxy, one entry per player ever seen.
     */
    public void cleanup(UUID playerUuid) {
        UUID partner = lastTalker.remove(playerUuid);
        if (partner != null) {
            lastTalker.remove(partner, playerUuid);
        }
    }
}
