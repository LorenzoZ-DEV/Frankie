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
 * Handles private messages: /msg, /tell, /w.
 */
@CommandAlias("msg|tell|w")
@CommandPermission("frankie.privatemessage")
public class MsgCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;
    private final PrivateMessageService pmService;

    public MsgCommand(ProxyServer proxy, Messages messages, PrivateMessageService pmService) {
        this.proxy = proxy;
        this.messages = messages;
        this.pmService = pmService;
    }

    @Default
    @Syntax("<player> <message>")
    public void onMessage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.get("general.frankie_usage"));
            return;
        }

        String targetName = args[0];
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(' ');
        }
        String message = messageBuilder.toString().trim();

        Player target = proxy.getPlayer(targetName).orElse(null);
        if (target == null) {
            player.sendMessage(messages.get("friend.player_not_found", "%target%", targetName));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(messages.get("privatemessages.self"));
            return;
        }

        // Format messages
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

        // Send to sender and target
        player.sendMessage(Component.text(sentMessage));
        target.sendMessage(Component.text(receivedMessage));

        // Update last talkers for both
        pmService.setLastTalker(player.getUniqueId(), target.getUniqueId());
        pmService.setLastTalker(target.getUniqueId(), player.getUniqueId());

        // Spy: send to players with permission frankie.privatemessage.spy
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