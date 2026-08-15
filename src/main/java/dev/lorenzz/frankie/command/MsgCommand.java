package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.privatemessage.PrivateMessageService;
import dev.lorenzz.frankie.util.Args;

/**
 * Private messages: /msg, /tell, /w.
 */
@CommandAlias("msg|tell|w")
@CommandPermission("frankie.privatemessage")
public class MsgCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;
    private final PrivateMessageService privateMessages;

    public MsgCommand(ProxyServer proxy, Messages messages, PrivateMessageService privateMessages) {
        this.proxy = proxy;
        this.messages = messages;
        this.privateMessages = privateMessages;
    }

    @Default
    @Syntax("<giocatore> <messaggio>")
    @Description("Invia un messaggio privato a un giocatore.")
    @CommandCompletion("@players")
    public void onMessage(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.get("privatemessages.usage"));
            return;
        }
        String message = Args.join(args, 1);
        if (message.isEmpty()) {
            player.sendMessage(messages.get("privatemessages.usage"));
            return;
        }

        Player target = proxy.getPlayer(args[0]).orElse(null);
        if (target == null) {
            player.sendMessage(messages.get("privatemessages.player_not_found", "%target%", args[0]));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(messages.get("privatemessages.self"));
            return;
        }
        privateMessages.send(player, target, message);
    }
}
