package dev.lorenzz.frankie.manager;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.Frankie;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.database.FriendRepository;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.listener.BlacklistListener;
import dev.lorenzz.frankie.listener.ChatListener;
import dev.lorenzz.frankie.listener.ConnectionListener;
import dev.lorenzz.frankie.staffchat.StaffChatListener;
import dev.lorenzz.frankie.staffchat.StaffChatManager;
import dev.lorenzz.frankie.util.ConfigProvider;

public class ModuleManager {

    private final Frankie plugin;
    private final ProxyServer proxy;
    private final Messages messages;
    private final StaffChatManager staffChat;
    private final FriendRepository friends;
    private final PlayerRepository players;
    private final LuckPermsHook luckPerms;
    private final BlacklistListener blacklistListener;

    public ModuleManager(Frankie plugin, ProxyServer proxy, Messages messages, StaffChatManager staffChat,
                         FriendRepository friends, PlayerRepository players, LuckPermsHook luckPerms,
                         BlacklistListener blacklistListener) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.messages = messages;
        this.staffChat = staffChat;
        this.friends = friends;
        this.players = players;
        this.luckPerms = luckPerms;
        this.blacklistListener = blacklistListener;
    }

    private boolean isModuleEnabled(String key) {
        return ConfigProvider.getBoolean("modules." + key, true);
    }

    public void registerListeners() {
        if (isModuleEnabled("staffchat")) {
            proxy.getEventManager().register(plugin, new StaffChatListener(staffChat, messages));
        }
        if (isModuleEnabled("friends")) {
            proxy.getEventManager().register(plugin, new ConnectionListener(proxy, messages, staffChat, friends, players, luckPerms));
        }
        if (isModuleEnabled("blacklist")) {
            proxy.getEventManager().register(plugin, blacklistListener);
        }

        // Chat listener sempre attivo (formattazione e antiswear)
        proxy.getEventManager().register(plugin, new ChatListener(messages, proxy));
    }
}