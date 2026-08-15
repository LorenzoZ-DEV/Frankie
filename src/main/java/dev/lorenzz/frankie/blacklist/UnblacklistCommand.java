package dev.lorenzz.frankie.blacklist;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.velocitypowered.api.command.CommandSource;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.util.Futures;
import org.slf4j.Logger;

@CommandAlias("unblacklist|unbl")
@CommandPermission(Issuer.PERMISSION)
public class UnblacklistCommand extends BaseCommand {

    private final BlacklistAgent agent;
    private final BlacklistAnnouncer announcer;
    private final Messages messages;
    private final LuckPermsHook luckPerms;
    private final Logger logger;

    public UnblacklistCommand(BlacklistAgent agent, BlacklistAnnouncer announcer, Messages messages,
                              LuckPermsHook luckPerms, Logger logger) {
        this.agent = agent;
        this.announcer = announcer;
        this.messages = messages;
        this.luckPerms = luckPerms;
        this.logger = logger;
    }

    @Default
    @Syntax("[-s] <giocatore>")
    @Description("Rimuove un giocatore dalla blacklist.")
    @CommandCompletion("@players")
    public void onUnblacklist(CommandIssuer issuer, String[] args) {
        CommandSource source = issuer.getIssuer();
        BlacklistArgs parsed = BlacklistArgs.parse(args, false);
        if (!parsed.valid()) {
            source.sendMessage(messages.get("blacklist.unblacklist_usage"));
            return;
        }

        Issuer sender = Issuer.of(source, luckPerms);
        String target = parsed.target();

        agent.unblacklistByName(target, sender.name()).whenComplete((removed, error) -> {
            if (error != null) {
                logger.error("Unblacklist of {} failed", target, Futures.unwrap(error));
                source.sendMessage(messages.get("blacklist.failed", "%target%", target));
                return;
            }
            if (removed.isEmpty()) {
                source.sendMessage(messages.get("blacklist.not_blacklisted", "%target%", target));
                return;
            }
            announcer.unblacklisted(source, sender, removed.get(), parsed.silent());
        });
    }
}
