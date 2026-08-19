package dev.lorenzz.frankie.backend;

import dev.lorenzz.frankie.backend.antiswear.AntiSwearGuard;
import dev.lorenzz.frankie.backend.antiswear.LegacyChatFilter;
import dev.lorenzz.frankie.backend.blacklist.BlacklistBridge;
import dev.lorenzz.frankie.backend.blacklist.BlacklistProtocol;
import dev.lorenzz.frankie.backend.blacklist.MenuStyle;
import dev.lorenzz.frankie.backend.check.CheckBridge;
import dev.lorenzz.frankie.backend.check.CheckProtocol;
import dev.lorenzz.frankie.backend.check.CheckStyle;
import dev.lorenzz.frankie.backend.teleport.TeleportBridge;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Frankie backend companion. The proxy owns the data and the permissions; this
 * plugin does the two things a proxy cannot do itself:
 *
 * <ul>
 *   <li>teleport a player onto another player ({@code frankie:tpto}),</li>
 *   <li>open and drive the blacklist inventory ({@code frankie:blacklist}),</li>
 *   <li>open and drive the /check inventory ({@code frankie:check}).</li>
 * </ul>
 */
public class FrankieBackend extends JavaPlugin {

    private BlacklistBridge blacklist;
    private CheckBridge check;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        TeleportBridge teleport = new TeleportBridge(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, TeleportBridge.CHANNEL, teleport);
        getServer().getPluginManager().registerEvents(teleport, this);

        blacklist = new BlacklistBridge(this, new MenuStyle(getConfig()));
        getServer().getMessenger().registerIncomingPluginChannel(
                this, BlacklistProtocol.CHANNEL, blacklist);
        getServer().getMessenger().registerOutgoingPluginChannel(this, BlacklistProtocol.CHANNEL);
        getServer().getPluginManager().registerEvents(blacklist, this);

        check = new CheckBridge(this, new CheckStyle(getConfig()));
        getServer().getMessenger().registerIncomingPluginChannel(
                this, CheckProtocol.CHANNEL, check);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CheckProtocol.CHANNEL);
        getServer().getPluginManager().registerEvents(check, this);

        AntiSwearGuard antiSwear = new AntiSwearGuard(this);
        getServer().getMessenger().registerIncomingPluginChannel(
                this, AntiSwearGuard.CHANNEL, antiSwear);
        getServer().getMessenger().registerOutgoingPluginChannel(this, AntiSwearGuard.CHANNEL);
        getServer().getPluginManager().registerEvents(antiSwear, this);
        if (getConfig().getBoolean("antiswear.legacy-chat-event", false)) {
            getServer().getPluginManager().registerEvents(new LegacyChatFilter(antiSwear), this);
            getLogger().info("Antiswear: filtro anche sull'evento chat legacy.");
        }

        // Says plainly that this build is the one running: "nothing appears in
        // the log" is otherwise indistinguishable from an old jar still loaded.
        getLogger().info("FrankieBackend attivo (canali: tpto, blacklist, check, antiswear).");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (blacklist != null) {
            // Leaving a stale menu open would let clicks act on removed rows.
            blacklist.closeAll();
        }
        if (check != null) {
            check.closeAll();
        }
    }

    /** Re-reads config.yml, including every GUI text. */
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (blacklist != null) {
            blacklist.restyle(new MenuStyle(getConfig()));
        }
        if (check != null) {
            check.restyle(new CheckStyle(getConfig()));
        }
    }
}
