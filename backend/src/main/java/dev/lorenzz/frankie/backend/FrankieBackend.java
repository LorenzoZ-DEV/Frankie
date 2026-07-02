package dev.lorenzz.frankie.backend;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frankie backend companion. The Velocity proxy connects a player to the
 * target's server and then sends a "frankie:tpto" plugin message; this plugin
 * runs the actual entity teleport, which the proxy cannot do.
 */
public class FrankieBackend extends JavaPlugin implements Listener, PluginMessageListener {

    private static final String CHANNEL = "frankie:tpto";

    // Requests that arrived before the player finished joining (name -> target).
    private final Map<String, String> pending = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String who = in.readUTF();
        String target = in.readUTF();
        // Cross-server tp: the mover may not have spawned here yet -> defer to join.
        if (!teleport(who, target)) {
            pending.put(who.toLowerCase(), target);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String target = pending.remove(event.getPlayer().getName().toLowerCase());
        if (target != null) {
            // ponytail: 5-tick delay lets the target load in; bump if teleports miss.
            Bukkit.getScheduler().runTaskLater(this,
                    () -> teleport(event.getPlayer().getName(), target), 5L);
        }
    }

    /** @return true if both players were online here and the teleport ran. */
    private boolean teleport(String whoName, String targetName) {
        Player who = Bukkit.getPlayerExact(whoName);
        Player target = Bukkit.getPlayerExact(targetName);
        if (who == null || target == null) {
            return false;
        }
        who.teleport(target);
        return true;
    }
}