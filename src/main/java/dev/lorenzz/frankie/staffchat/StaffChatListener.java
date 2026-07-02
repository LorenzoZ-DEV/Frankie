package dev.lorenzz.frankie.staffchat;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.config.Messages;

/**
 * Redirects normal chat to the staffchat for players in talk mode.
 * NOTE: 1.19.1+ clients sign their chat, Velocity cannot cancel those
 * messages — same limitation as noted in messages.yml.
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
        if (!staffChat.isTalkMode(player) || !player.hasPermission(StaffChatManager.PERMISSION)) {
            return;
        }
        event.setResult(PlayerChatEvent.ChatResult.denied());
        if (staffChat.isMuted() && !player.hasPermission(StaffChatManager.PERMISSION_MUTE_BYPASS)) {
            player.sendMessage(messages.get("staffchat.staffchat_muted"));
            return;
        }
        staffChat.sendPlayerMessage(player, event.getMessage());
    }
}