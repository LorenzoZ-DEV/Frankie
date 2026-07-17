package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Syntax;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import net.kyori.adventure.text.Component;

@CommandAlias("global")
@CommandPermission("frankie.global")
public class GlobalCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;

    public GlobalCommand(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Default
    @Syntax("<message>")
    public void onGlobal(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(messages.get("general.frankie_usage"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            sb.append(arg).append(' ');
        }
        String message = sb.toString().trim();
        Component formatted = messages.get("global.format",
                "%player%", player.getUsername(),
                "%server%", player.getCurrentServer()
                        .map(s -> s.getServerInfo().getName())
                        .orElse("unknown"),
                "%message%", message);
        proxy.getAllPlayers().forEach(p -> p.sendMessage(formatted));
    }
}