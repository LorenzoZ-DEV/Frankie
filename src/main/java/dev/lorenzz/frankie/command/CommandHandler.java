package dev.lorenzz.frankie.command;

import co.aikar.commands.VelocityCommandManager;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.Frankie;
import dev.lorenzz.frankie.blacklist.BlacklistAgent;
import dev.lorenzz.frankie.blacklist.BlacklistCommand;
import dev.lorenzz.frankie.blacklist.UnblacklistCommand;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.database.FriendRepository;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.database.ReportRepository;
import dev.lorenzz.frankie.database.StaffNoteRepository;
import dev.lorenzz.frankie.friend.FriendCommand;
import dev.lorenzz.frankie.privatemessage.PrivateMessageService;
import dev.lorenzz.frankie.report.ReportCommand;
import dev.lorenzz.frankie.request.RequestCommand;
import dev.lorenzz.frankie.staffchat.StaffChatManager;
import dev.lorenzz.frankie.staffchat.StaffChatCommand;
import dev.lorenzz.frankie.staffnotes.StaffNotesCommand;
import dev.lorenzz.frankie.teleport.GotoCommand;
import dev.lorenzz.frankie.teleport.TpToCommand;
import dev.lorenzz.frankie.util.ConfigProvider;
import org.slf4j.Logger;

public class CommandHandler {

    private final Frankie plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Messages messages;
    private final YamlConfig config;
    private final YamlConfig messagesConfig;

    // Dipendenze specifiche per i comandi
    private final StaffChatManager staffChat;
    private final ReportRepository reports;
    private final FriendRepository friends;
    private final PlayerRepository players;
    private final StaffNoteRepository staffNotes;
    private final PrivateMessageService pmService;
    private final BlacklistAgent blacklistAgent;

    public CommandHandler(Frankie plugin, ProxyServer proxy, Logger logger, Messages messages,
                          YamlConfig config, YamlConfig messagesConfig, StaffChatManager staffChat,
                          ReportRepository reports, FriendRepository friends, PlayerRepository players,
                          StaffNoteRepository staffNotes, PrivateMessageService pmService,
                          BlacklistAgent blacklistAgent) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.messages = messages;
        this.config = config;
        this.messagesConfig = messagesConfig;
        this.staffChat = staffChat;
        this.reports = reports;
        this.friends = friends;
        this.players = players;
        this.staffNotes = staffNotes;
        this.pmService = pmService;
        this.blacklistAgent = blacklistAgent;
    }

    private boolean isModuleEnabled(String key) {
        return ConfigProvider.getBoolean("modules." + key, true);
    }

    public void register() {
        VelocityCommandManager commandManager = new VelocityCommandManager(proxy, plugin);

        // Registrazione dei completamenti (Tab Completer)
        commandManager.getCommandCompletions().registerAsyncCompletion("players", context ->
                proxy.getAllPlayers().stream()
                        .map(com.velocitypowered.api.proxy.Player::getUsername)
                        .toList());
        commandManager.getCommandCompletions().registerAsyncCompletion("servers", context ->
                proxy.getAllServers().stream()
                        .map(server -> server.getServerInfo().getName())
                        .toList());

        // Registrazione dei comandi in base ai moduli
        if (isModuleEnabled("staffchat")) {
            commandManager.registerCommand(new StaffChatCommand(staffChat, messages));
        }
        if (isModuleEnabled("reports")) {
            commandManager.registerCommand(new ReportCommand(proxy, messages, reports, config));
        }
        if (isModuleEnabled("requests")) {
            commandManager.registerCommand(new RequestCommand(proxy, messages, reports, config));
        }
        if (isModuleEnabled("friends")) {
            commandManager.registerCommand(new FriendCommand(proxy, messages, friends, players, config));
        }
        if (isModuleEnabled("staffnotes")) {
            commandManager.registerCommand(new StaffNotesCommand(proxy, messages, staffNotes, players));
        }
        if (isModuleEnabled("teleport")) {
            proxy.getChannelRegistrar().register(TpToCommand.CHANNEL);
            commandManager.registerCommand(new TpToCommand(proxy, messages));
            commandManager.registerCommand(new GotoCommand(proxy, messages));
        }

        // Comandi core sempre attivi
        commandManager.registerCommand(new FrankieCommand(config, messagesConfig, messages, logger));
        commandManager.registerCommand(new GlobalCommand(proxy, messages));

        if (isModuleEnabled("blacklist")) {
            commandManager.registerCommand(new BlacklistCommand(blacklistAgent, messages));
            commandManager.registerCommand(new UnblacklistCommand(blacklistAgent));
        }
        if (isModuleEnabled("privatemessages")) {
            commandManager.registerCommand(new MsgCommand(proxy, messages, pmService));
            commandManager.registerCommand(new ReplyCommand(proxy, messages, pmService));
        }
        if (isModuleEnabled("stafflist")) {
            commandManager.registerCommand(new StaffListCommand(proxy, messages));
        }
    }
}