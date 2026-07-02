package dev.lorenzz.frankie.teleport;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.lorenzz.frankie.config.Messages;

/**
 * /tpto <player> — connects you to the target's server, then asks the Frankie
 * backend plugin to teleport you onto the target (the proxy cannot set
 * coordinates itself). Without the backend companion you still get moved to the
 * right server, just not to the exact position.
 */
@CommandAlias("tpto")
@CommandPermission("frankie.tpto")
public class TpToCommand extends BaseCommand {

    /** Backend companion listens here; see the frankie-backend module. */
    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("frankie", "tpto");

    private final ProxyServer proxy;
    private final Messages messages;

    public TpToCommand(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Default
    @CommandCompletion("@players")
    public void onTpTo(Player player, @Optional String targetName) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(messages.get("teleport.tpto_usage"));
            return;
        }
        if (targetName.equalsIgnoreCase(player.getUsername())) {
            player.sendMessage(messages.get("teleport.self"));
            return;
        }
        java.util.Optional<Player> targetOptional = proxy.getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            player.sendMessage(messages.get("teleport.player_not_found", "%target%", targetName));
            return;
        }
        Player target = targetOptional.get();
        java.util.Optional<RegisteredServer> targetServer = target.getCurrentServer()
                .map(connection -> connection.getServer());
        if (targetServer.isEmpty()) {
            player.sendMessage(messages.get("teleport.player_not_found", "%target%", targetName));
            return;
        }
        String serverName = targetServer.get().getServerInfo().getName();
        boolean sameServer = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equals(serverName))
                .orElse(false);
        // Already together: no server switch, just ask the backend to teleport.
        if (sameServer) {
            requestTeleport(targetServer.get(), player, target);
            player.sendMessage(messages.get("teleport.tpto_success",
                    "%target%", target.getUsername(),
                    "%server%", serverName));
            return;
        }
        player.createConnectionRequest(targetServer.get()).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                requestTeleport(targetServer.get(), player, target);
                player.sendMessage(messages.get("teleport.tpto_success",
                        "%target%", target.getUsername(),
                        "%server%", serverName));
            } else {
                player.sendMessage(messages.get("teleport.failed", "%server%", serverName));
            }
        });
    }

    private void requestTeleport(RegisteredServer server, Player who, Player target) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(who.getUsername());
        out.writeUTF(target.getUsername());
        server.sendPluginMessage(CHANNEL, out.toByteArray());
    }
}