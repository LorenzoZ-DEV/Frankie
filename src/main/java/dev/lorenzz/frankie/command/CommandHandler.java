package dev.lorenzz.frankie.command;

import co.aikar.commands.VelocityCommandManager;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.antiswear.AntiSwearCommand;
import dev.lorenzz.frankie.blacklist.BlacklistBrowser;
import dev.lorenzz.frankie.blacklist.BlacklistCommand;
import dev.lorenzz.frankie.blacklist.BlacklistUsersCommand;
import dev.lorenzz.frankie.blacklist.UnblacklistCommand;
import dev.lorenzz.frankie.core.FrankieServices;
import dev.lorenzz.frankie.friend.FriendCommand;
import dev.lorenzz.frankie.report.ReportCommand;
import dev.lorenzz.frankie.request.RequestCommand;
import dev.lorenzz.frankie.staffchat.StaffChatCommand;
import dev.lorenzz.frankie.staffnotes.StaffNotesCommand;
import dev.lorenzz.frankie.teleport.GotoCommand;
import dev.lorenzz.frankie.teleport.TpToCommand;

/**
 * Registers commands and tab completions for every enabled module.
 */
public class CommandHandler {

    private final FrankieServices services;

    public CommandHandler(FrankieServices services) {
        this.services = services;
    }

    public void register() {
        VelocityCommandManager commands = new VelocityCommandManager(services.proxy, services.plugin);
        registerCompletions(commands);

        // Core commands, always available.
        commands.registerCommand(new FrankieCommand(services));

        if (services.module("staffchat")) {
            commands.registerCommand(new StaffChatCommand(services.staffChat, services.messages));
        }
        if (services.module("reports")) {
            commands.registerCommand(new ReportCommand(
                    services.proxy, services.messages, services.reports, services.config, services.futures));
        }
        if (services.module("requests")) {
            commands.registerCommand(new RequestCommand(
                    services.proxy, services.messages, services.reports, services.config, services.futures));
        }
        if (services.module("friends")) {
            commands.registerCommand(new FriendCommand(services.proxy, services.messages,
                    services.friends, services.resolver, services.config, services.futures));
        }
        if (services.module("staffnotes")) {
            commands.registerCommand(new StaffNotesCommand(
                    services.messages, services.staffNotes, services.resolver, services.futures));
        }
        if (services.module("teleport")) {
            services.proxy.getChannelRegistrar().register(TpToCommand.CHANNEL);
            commands.registerCommand(new TpToCommand(services.proxy, services.messages));
            commands.registerCommand(new GotoCommand(services.proxy, services.messages));
        }
        // The toggle existed in config.yml but was never read: /global registered
        // unconditionally.
        if (services.module("global")) {
            commands.registerCommand(new GlobalCommand(services.proxy, services.messages));
        }
        if (services.module("blacklist")) {
            // The GUI lives on the backend companion; the proxy only serves it.
            services.proxy.getChannelRegistrar().register(BlacklistBrowser.CHANNEL);
            commands.registerCommand(new BlacklistCommand(services.blacklistAgent,
                    services.blacklistAnnouncer, services.messages, services.luckPerms,
                    services.logger));
            commands.registerCommand(new UnblacklistCommand(services.blacklistAgent,
                    services.blacklistAnnouncer, services.messages, services.luckPerms,
                    services.logger));
            commands.registerCommand(new BlacklistUsersCommand(services.blacklistBrowser,
                    services.messages, services.futures));
        }
        if (services.module("privatemessages")) {
            commands.registerCommand(new MsgCommand(
                    services.proxy, services.messages, services.privateMessages));
            commands.registerCommand(new ReplyCommand(
                    services.proxy, services.messages, services.privateMessages));
        }
        if (services.module("antiswear")) {
            commands.registerCommand(new AntiSwearCommand(
                    services.antiSwear, services.antiSwearSync, services.messages));
        }
        if (services.module("stafflist")) {
            commands.registerCommand(new StaffListCommand(services.proxy, services.messages));
        }
    }

    private void registerCompletions(VelocityCommandManager commands) {
        commands.getCommandCompletions().registerAsyncCompletion("players", context ->
                services.proxy.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .toList());
        commands.getCommandCompletions().registerAsyncCompletion("servers", context ->
                services.proxy.getAllServers().stream()
                        .map(server -> server.getServerInfo().getName())
                        .toList());
    }
}
