package dev.lorenzz.frankie.staffchat;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.config.Messages;

@CommandAlias("staffchat|sc")
@CommandPermission("frankie.staffchat")
public class StaffChatCommand extends BaseCommand {

    private final StaffChatManager staffChat;
    private final Messages messages;

    public StaffChatCommand(StaffChatManager staffChat, Messages messages) {
        this.staffChat = staffChat;
        this.messages = messages;
    }

    @Default
    public void onSend(CommandSource source, @Optional String message) {
        if (message == null || message.isBlank()) {
            source.sendMessage(messages.get("staffchat.arguments"));
            return;
        }
        if (!(source instanceof Player player)) {
            staffChat.sendConsoleMessage(message);
            return;
        }
        if (staffChat.isMuted() && !player.hasPermission(StaffChatManager.PERMISSION_MUTE_BYPASS)) {
            player.sendMessage(messages.get("staffchat.staffchat_muted"));
            return;
        }
        staffChat.sendPlayerMessage(player, message);
    }

    @Subcommand("talk")
    public void onTalk(Player player) {
        boolean enabled = staffChat.toggleTalkMode(player);
        player.sendMessage(messages.get(enabled
                ? "staffchat.staffchat_talk_enabled"
                : "staffchat.staffchat_talk_disabled"));
    }

    @Subcommand("toggle")
    public void onToggle(Player player) {
        boolean visible = staffChat.toggleVisibility(player);
        player.sendMessage(messages.get(visible
                ? "staffchat.staffchat_toggled_on"
                : "staffchat.staffchat_toggled_off"));
    }

    @Subcommand("mute")
    @CommandPermission(StaffChatManager.PERMISSION_MUTE)
    public void onMute(CommandSource source) {
        boolean muted = staffChat.toggleMute();
        staffChat.broadcastStatus(messages.get(muted
                ? "staffchat.staffchat_muted"
                : "staffchat.staffchat_unmuted"));
    }
}