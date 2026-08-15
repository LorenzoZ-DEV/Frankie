package dev.lorenzz.frankie.blacklist;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.google.common.net.InetAddresses;
import com.velocitypowered.api.command.CommandSource;
import dev.lorenzz.frankie.blacklist.BlacklistAgent.BlacklistResult;
import dev.lorenzz.frankie.blacklist.BlacklistAgent.UnknownPlayerException;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.hook.LuckPermsHook;
import dev.lorenzz.frankie.util.Futures;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

@CommandAlias("blacklist|bl|blist|spermaban")
@CommandPermission(Issuer.PERMISSION)
public class BlacklistCommand extends BaseCommand {

    private final BlacklistAgent agent;
    private final BlacklistAnnouncer announcer;
    private final Messages messages;
    private final LuckPermsHook luckPerms;
    private final Logger logger;

    public BlacklistCommand(BlacklistAgent agent, BlacklistAnnouncer announcer, Messages messages,
                            LuckPermsHook luckPerms, Logger logger) {
        this.agent = agent;
        this.announcer = announcer;
        this.messages = messages;
        this.luckPerms = luckPerms;
        this.logger = logger;
    }

    @Default
    @Syntax("[-s] <giocatore|ip> <motivo>")
    @Description("Blacklista permanentemente un giocatore o un indirizzo IP.")
    @CommandCompletion("@players")
    public void onBlacklist(CommandIssuer issuer, String[] args) {
        CommandSource source = issuer.getIssuer();
        BlacklistArgs parsed = BlacklistArgs.parse(args, true);
        if (!parsed.valid()) {
            source.sendMessage(messages.get("blacklist.usage"));
            source.sendMessage(messages.get("blacklist.example"));
            return;
        }

        Issuer sender = Issuer.of(source, luckPerms);
        String target = parsed.target();
        String reason = parsed.reason();

        CompletableFuture<BlacklistResult> future = InetAddresses.isInetAddress(target)
                ? agent.blacklistByIp(sender.uuid(), sender.name(), target, reason)
                : agent.blacklistByName(sender.uuid(), sender.name(), target, reason);

        future.whenComplete((result, error) -> {
            if (error != null) {
                fail(source, target, error);
                return;
            }
            announcer.blacklisted(source, sender, result, parsed.silent());
        });
    }

    private void fail(CommandSource source, String target, Throwable error) {
        Throwable cause = Futures.unwrap(error);
        if (cause instanceof UnknownPlayerException unknown) {
            source.sendMessage(messages.get("blacklist.never_seen", "%target%", unknown.playerName()));
            return;
        }
        logger.error("Blacklist of {} failed", target, cause);
        source.sendMessage(messages.get("blacklist.failed", "%target%", target));
    }
}
