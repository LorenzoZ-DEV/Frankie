package dev.lorenzz.frankie.hook;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;

/**
 * Optional LuckPerms integration for %userprefix%, %usersuffix% and
 * %displayname%. Falls back to empty prefix/suffix when LuckPerms is not
 * installed.
 */
public class LuckPermsHook {

    private final boolean available;
    private final ProxyServer proxy;

    public LuckPermsHook(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.available = proxy.getPluginManager().getPlugin("luckperms").isPresent();
        if (available) {
            logger.info("LuckPerms found: %userprefix% and %usersuffix% are enabled.");
        } else {
            logger.info("LuckPerms not found: %userprefix% and %usersuffix% will be empty.");
        }
    }

    public String prefix(Player player) {
        return meta(player, true);
    }

    public String suffix(Player player) {
        return meta(player, false);
    }

    /** Prefix + name + suffix, ready for %displayname%. */
    public String displayName(Player player) {
        return prefix(player) + player.getUsername() + suffix(player);
    }

    /**
     * Get suffix for a player by name. Returns empty string if player not online
     * or LuckPerms not available.
     */
    public String getSuffixForName(String name) {
        if (!available) {
            return "";
        }
        Player player = proxy.getPlayer(name).orElse(null);
        if (player == null) {
            return "";
        }
        return suffix(player);
    }

    private String meta(Player player, boolean prefix) {
        if (!available) {
            return "";
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return "";
            }
            CachedMetaData meta = user.getCachedData().getMetaData();
            String value = prefix ? meta.getPrefix() : meta.getSuffix();
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }
}