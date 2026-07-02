package dev.lorenzz.frankie;

import co.aikar.commands.VelocityCommandManager;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.command.FrankieCommand;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.database.Database;
import dev.lorenzz.frankie.database.FriendRepository;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.database.ReportRepository;
import dev.lorenzz.frankie.friend.FriendCommand;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.listener.ConnectionListener;
import dev.lorenzz.frankie.report.ReportCommand;
import dev.lorenzz.frankie.request.RequestCommand;
import dev.lorenzz.frankie.staffchat.StaffChatCommand;
import dev.lorenzz.frankie.staffchat.StaffChatListener;
import dev.lorenzz.frankie.staffchat.StaffChatManager;
import dev.lorenzz.frankie.teleport.GotoCommand;
import dev.lorenzz.frankie.teleport.TpToCommand;
import dev.lorenzz.frankie.util.AsciiArt;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "frankie", name = "Frankie", version = "1.0-SNAPSHOT",
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
        LuckPermsHook luckPerms = new LuckPermsHook(proxy, logger);
        StaffChatManager staffChat = new StaffChatManager(proxy, messages, luckPerms);

        proxy.getEventManager().register(this, new StaffChatListener(staffChat, messages));
        proxy.getEventManager().register(this, new ConnectionListener(proxy, messages, staffChat, friends, players, luckPerms));

        proxy.getChannelRegistrar().register(TpToCommand.CHANNEL);

        VelocityCommandManager commandManager = new VelocityCommandManager(proxy, this);
        commandManager.getCommandCompletions().registerAsyncCompletion("players", context ->
                proxy.getAllPlayers().stream()
                        .map(com.velocitypowered.api.proxy.Player::getUsername)
                        .toList());
        commandManager.getCommandCompletions().registerAsyncCompletion("servers", context ->
                proxy.getAllServers().stream()
                        .map(server -> server.getServerInfo().getName())
                        .toList());

        commandManager.registerCommand(new StaffChatCommand(staffChat, messages));
        commandManager.registerCommand(new ReportCommand(proxy, messages, reports, config));
        commandManager.registerCommand(new RequestCommand(proxy, messages, reports, config));
        commandManager.registerCommand(new FriendCommand(proxy, messages, friends, players, config));
        commandManager.registerCommand(new TpToCommand(proxy, messages));
        commandManager.registerCommand(new GotoCommand(proxy, messages));
        commandManager.registerCommand(new FrankieCommand(config, messagesConfig, messages, logger));

        logger.info("Frankie enabled: staffchat, reports, requests and friends are ready.");
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