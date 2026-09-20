package dev.lorenzz.frankie.staffchat;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.config.Messages;

/**
 * Redirects normal chat into a staff room, for the clients this proxy is still
 * allowed to touch.
 *
 * <p>Up to 1.19, chat is unsigned and denying it here is legal. From 1.19.1 it
 * is signed for the backend server and cancelling it at the proxy kicks the
 * player — <em>"A plugin tried to cancel a signed chat message"</em>, the crash
 * this listener used to cause on every message typed in talk mode. Those clients
 * are handled by the backend companion instead; see {@link ChatChannelSync}.
 *
 * <p>The split is by protocol version only, like the antiswear filter: Velocity
 * exposes no way to ask whether a given message carries a signature.
 */
public class StaffChatListener {

    private final StaffChatManager staffChat;
    private final Messages messages;

    public StaffChatListener(StaffChatManager staffChat, Messages messages) {
        this.staffChat = staffChat;
        this.messages = messages;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!canDeny(player)) {
            return;
        }
        StaffChannel channel = staffChat.talkChannel(player).orElse(null);
        if (channel == null || !player.hasPermission(channel.permission())) {
            return;
        }
        event.setResult(PlayerChatEvent.ChatResult.denied());
        if (!staffChat.canSpeak(player, channel)) {
            player.sendMessage(messages.get(channel.key("muted")));
            return;
        }
        staffChat.sendPlayerMessage(channel, player, event.getMessage());
    }

    /** Whether this player's chat can still be cancelled at the proxy. */
    private static boolean canDeny(Player player) {
        return player.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_19_1) < 0;
    }
}
