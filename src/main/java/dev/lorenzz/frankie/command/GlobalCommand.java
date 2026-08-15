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
import dev.lorenzz.frankie.util.Args;
import net.kyori.adventure.text.Component;

@CommandAlias("global|g")
@CommandPermission("frankie.global")
public class GlobalCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;

    public GlobalCommand(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Default
    @Syntax("<messaggio>")
    @Description("Invia un messaggio a tutta la rete.")
    public void onGlobal(Player player, String[] args) {
        String message = Args.join(args, 0);
        if (message.isEmpty()) {
            player.sendMessage(messages.get("global.usage"));
            return;
        }
        Component formatted = messages.get("global.format",
                "%player%", player.getUsername(),
                "%server%", player.getCurrentServer()
                        .map(connection -> connection.getServerInfo().getName())
                        .orElse("unknown"),
                "%message%", message);
        proxy.getAllPlayers().forEach(online -> online.sendMessage(formatted));
        proxy.getConsoleCommandSource().sendMessage(formatted);
    }
}
