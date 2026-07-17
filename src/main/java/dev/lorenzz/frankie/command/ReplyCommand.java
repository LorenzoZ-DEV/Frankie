package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Syntax;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.privatemessage.PrivateMessageService;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles private message replies: /reply, /r.
 */
@CommandAlias("reply|r")
@CommandPermission("frankie.privatemessage")
public class ReplyCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;
    private final PrivateMessageService pmService;

    public ReplyCommand(ProxyServer proxy, Messages messages, PrivateMessageService pmService) {
        this.proxy = proxy;
        this.messages = messages;
        this.pmService = pmService;
    }

    @Default
    @Syntax("<message>")
    public void onReply(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(messages.get("general.frankie_usage"));
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(' ');
        }
        String message = messageBuilder.toString().trim();

        UUID playerUuid = player.getUniqueId();
        UUID targetUuid = pmService.getLastTalker(playerUuid);
        if (targetUuid == null) {
            player.sendMessage(Component.text(messages.raw("privatemessages.no-reply")));
            return;
        }

        Player target = proxy.getPlayer(targetUuid).orElse(null);
        if (target == null) {
            player.sendMessage(Component.text(messages.raw("friend.player_not_found", "%target%", targetUuid.toString())));
            // Clear the dead target
            pmService.clear(playerUuid);
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(messages.raw("privatemessages.self")));
            return;
        }

        String senderName = player.getUsername();
        String targetNameActual = target.getUsername();

        String sentFormat = messages.raw("privatemessages.format-sent");
        String receivedFormat = messages.raw("privatemessages.format-received");

        String sentMessage = sentFormat
                .replace("%target%", targetNameActual)
                .replace("%message%", message);
        String receivedMessage = receivedFormat
                .replace("%target%", senderName)
                .replace("%message%", message);

        player.sendMessage(Component.text(sentMessage));
        target.sendMessage(Component.text(receivedMessage));

        // Update last talkers (refresh)
        pmService.setLastTalker(playerUuid, targetUuid);
        pmService.setLastTalker(targetUuid, playerUuid);

        // Spy
        String spyFormat = messages.raw("privatemessages.spy-format");
        String spyMessage = spyFormat
                .replace("%player%", senderName)
                .replace("%target%", targetNameActual)
                .replace("%message%", message);
        Component spyComponent = Component.text(spyMessage);
        for (Player p : proxy.getAllPlayers()) {
            if (p.hasPermission("frankie.privatemessage.spy")) {
                p.sendMessage(spyComponent);
            }
        }
    }
}