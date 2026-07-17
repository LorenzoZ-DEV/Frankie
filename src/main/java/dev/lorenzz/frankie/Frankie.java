package dev.lorenzz.frankie;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.blacklist.BlacklistAgent;
import dev.lorenzz.frankie.command.CommandHandler;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.database.*;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.listener.BlacklistListener;
import dev.lorenzz.frankie.manager.ModuleManager;
import dev.lorenzz.frankie.privatemessage.PrivateMessageService;
import dev.lorenzz.frankie.staffchat.StaffChatManager;
import dev.lorenzz.frankie.util.AsciiArt;
import dev.lorenzz.frankie.util.ConfigProvider;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "frankie", name = "Frankie", version = "1.0.1",
        dependencies = {@Dependency(id = "luckperms", optional = true)})
public class Frankie {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private Database database;

    @Inject
    public Frankie(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        AsciiArt.startup(logger);

        YamlConfig config;
        YamlConfig messagesConfig;
        Messages messages;
        try {
            config = YamlConfig.load(dataDirectory, "config.yml");
            messagesConfig = YamlConfig.load(dataDirectory, "messages.yml");
            messages = new Messages(messagesConfig);
            ConfigProvider.setConfig(config);
        } catch (Exception e) {
            logger.error("Failed to load configuration, Frankie is disabled", e);
            return;
        }

        try {
            database = new Database(config);
            database.createTables();
        } catch (Exception e) {
            logger.error("Failed to connect to MySQL, Frankie is disabled. Check config.yml", e);
            return;
        }

        PlayerRepository players = new PlayerRepository(database);
        FriendRepository friends = new FriendRepository(database);
        ReportRepository reports = new ReportRepository(database);
        StaffNoteRepository staffNotes = new StaffNoteRepository(database);
        BlacklistRepository blacklistRepo = new BlacklistRepository(database);
        LuckPermsHook luckPerms = new LuckPermsHook(proxy, logger);
        StaffChatManager staffChat = new StaffChatManager(proxy, messages, luckPerms);
        PrivateMessageService pmService = new PrivateMessageService();
        BlacklistListener blacklistListener = new BlacklistListener(blacklistRepo);
        blacklistListener.loadIpCache();
        BlacklistAgent blacklistAgent = new BlacklistAgent(blacklistRepo, players, luckPerms, proxy, blacklistListener);

        // 5. Registrazione Moduli (Listener)
        ModuleManager moduleManager = new ModuleManager(this, proxy, messages, staffChat, friends, players, luckPerms, blacklistListener);
        moduleManager.registerListeners();

        // 6. Registrazione Comandi
        CommandHandler commandHandler = new CommandHandler(this, proxy, logger, messages, config, messagesConfig, staffChat, reports, friends, players, staffNotes, pmService, blacklistAgent);
        commandHandler.register();

        // 7. Conferma avvio
        logger.info("Frankie enabled successfully.");
        logger.info("Developed by Lorenzz.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
        AsciiArt.shutdown(logger);
    }
}