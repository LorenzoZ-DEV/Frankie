package dev.lorenzz.frankie.staffchat;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

/** The staff room. Behaviour lives in {@link ChannelChat}. */
@CommandAlias("staffchat|sc")
@CommandPermission("frankie.staffchat")
public class StaffChatCommand extends BaseCommand {

    private final ChannelChat chat;

    public StaffChatCommand(ChannelChat chat) {
        this.chat = chat;
    }

    @Default
    public void onSend(CommandSource source, @Optional String message) {
        chat.send(source, message);
    }

    @Subcommand("talk")
    public void onTalk(Player player) {
        chat.talk(player);
    }

    @Subcommand("toggle")
    public void onToggle(Player player) {
        chat.toggle(player);
    }

    @Subcommand("mute")
    @CommandPermission("frankie.staffchat.mute")
    public void onMute(CommandSource source) {
        chat.mute(source);
    }
}
