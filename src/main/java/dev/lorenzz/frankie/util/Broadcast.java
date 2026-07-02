package dev.lorenzz.frankie.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

/** Send to console + every online player holding a permission. */
public final class Broadcast {

    private Broadcast() {
    }

    public static void toPermission(ProxyServer proxy, String permission, Component message) {
        proxy.getConsoleCommandSource().sendMessage(message);
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(message);
            }
        }
    }
}