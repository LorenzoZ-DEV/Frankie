package dev.lorenzz.frankie.manager;

import com.velocitypowered.api.event.EventManager;
import dev.lorenzz.frankie.antiswear.AntiSwearListener;
import dev.lorenzz.frankie.antiswear.AntiSwearSync;
import dev.lorenzz.frankie.core.FrankieServices;
import dev.lorenzz.frankie.listener.ConnectionListener;
import dev.lorenzz.frankie.staffchat.StaffChatListener;

/**
 * Registers the listeners of every enabled module.
 */
public class ModuleManager {

    private final FrankieServices services;

    public ModuleManager(FrankieServices services) {
        this.services = services;
    }

    public void registerListeners() {
        EventManager events = services.proxy.getEventManager();

        // Core: player cache, staff broadcasts and per-player cleanup.
        events.register(services.plugin, new ConnectionListener(services));

        if (services.module("staffchat")) {
            events.register(services.plugin, new StaffChatListener(services.staffChat, services.messages));
        }
        if (services.module("blacklist")) {
            events.register(services.plugin, services.blacklistGuard);
            // Serves the backend companion's GUI over the plugin channel.
            events.register(services.plugin, services.blacklistBrowser);
            services.blacklistGuard.load();
        }
        if (services.module("check")) {
            // Serves the backend companion's /check GUI over the plugin channel.
            events.register(services.plugin, services.check);
        }
        // Previously registered unconditionally, so the toggle only stopped the
        // filtering while the listener still rewrote every chat message.
        if (services.module("antiswear")) {
            events.register(services.plugin, new AntiSwearListener(
                    services.antiSwear, services.messages, services.config, services.proxy));
            // Signed chat cannot be censored at the proxy: the backend
            // companion enforces the list, this keeps its copy current.
            services.proxy.getChannelRegistrar().register(AntiSwearSync.CHANNEL);
            events.register(services.plugin, services.antiSwearSync);
        }
    }
}
