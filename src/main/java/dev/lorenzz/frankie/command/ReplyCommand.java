package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.privatemessage.PrivateMessageService;
import dev.lorenzz.frankie.util.Args;

import java.util.UUID;

/**
 * Replies to the last private message: /reply, /r.
 */
@CommandAlias("reply|r")
@CommandPermission("frankie.privatemessage")
public class ReplyCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;
    private final PrivateMessageService privateMessages;

    public ReplyCommand(ProxyServer proxy, Messages messages, PrivateMessageService privateMessages) {
        this.proxy = proxy;
        this.messages = messages;
        this.privateMessages = privateMessages;
    }

    @Default
    @Syntax("<messaggio>")
    @Description("Risponde all'ultimo giocatore che ti ha scritto in privato.")
    public void onReply(Player player, String[] args) {
        String message = Args.join(args, 0);
        if (message.isEmpty()) {
            player.sendMessage(messages.get("privatemessages.reply_usage"));
            return;
        }

        UUID targetUuid = privateMessages.lastTalker(player.getUniqueId());
        if (targetUuid == null) {
            player.sendMessage(messages.get("privatemessages.no-reply"));
            return;
        }
        Player target = proxy.getPlayer(targetUuid).orElse(null);
        if (target == null) {
            // They logged off; drop the stale pointer so the next /r says the same.
            privateMessages.cleanup(player.getUniqueId());
            player.sendMessage(messages.get("privatemessages.target_offline"));
            return;
        }
        privateMessages.send(player, target, message);
    }
}
