package dev.lorenzz.frankie.teleport;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.lorenzz.frankie.config.Messages;

/**
 * /goto <server> — connects you to the given server.
 */
@CommandAlias("goto")
@CommandPermission("frankie.goto")
public class GotoCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;

    public GotoCommand(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Default
    @CommandCompletion("@servers")
    public void onGoto(Player player, @Optional String serverName) {
        if (serverName == null || serverName.isBlank()) {
            player.sendMessage(messages.get("teleport.goto_usage"));
            return;
        }
        java.util.Optional<RegisteredServer> server = proxy.getServer(serverName);
        if (server.isEmpty()) {
            player.sendMessage(messages.get("teleport.server_not_found", "%server%", serverName));
            return;
        }
        String name = server.get().getServerInfo().getName();
        boolean sameServer = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equals(name))
                .orElse(false);
        if (sameServer) {
            player.sendMessage(messages.get("teleport.same_server", "%server%", name));
            return;
        }
        player.createConnectionRequest(server.get()).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(messages.get("teleport.goto_success", "%server%", name));
            } else {
                player.sendMessage(messages.get("teleport.failed", "%server%", name));
            }
        });
    }
}