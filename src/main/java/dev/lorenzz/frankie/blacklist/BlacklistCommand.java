package dev.lorenzz.frankie.blacklist;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.CommandIssuer;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class BlacklistCommand extends BaseCommand {
    private final BlacklistAgent agent;
    private final Messages messages;

    private static final Pattern IP_PATTERN =
            Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");

    private static final UUID CONSOLE_UUID = UUID.nameUUIDFromBytes("Console".getBytes());

    public BlacklistCommand(BlacklistAgent agent, Messages messages) {
        this.agent = agent;
        this.messages = messages;
    }

    @CommandAlias("blacklist|bl|blist|spermaban")
    @CommandPermission("frankie.admin")
    @CommandCompletion("@players")
    public void onBlacklistCommand(CommandIssuer issuer, String... args) {
        CommandSource source = (CommandSource) issuer.getIssuer();

        String target = null;
        boolean silent = false;
        StringBuilder reasonBuilder = new StringBuilder();

        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (target == null) {
                target = arg;
            } else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(' ');
                reasonBuilder.append(arg);
            }
        }

        if (target == null) {
            source.sendMessage(Text.colorize("&8‹&4⚠&8›&f &7Utilizzo: &f/blacklist (-s) <giocatore|ip> <motivo>"));
            return;
        }

        String reason = reasonBuilder.toString();
        if (reason.isEmpty()) {
            source.sendMessage(Text.colorize("&8‹&4⚠&8›&f &7Utilizzo: &f/blacklist (-s) <giocatore|ip> <motivo>"));
            return;
        }

        Player playerIssuer = issuer.getIssuer() instanceof Player p ? p : null;
        UUID issuerUuid = playerIssuer != null ? playerIssuer.getUniqueId() : CONSOLE_UUID;
        String issuerName = playerIssuer != null ? playerIssuer.getUsername() : "Console";

        final String finalTarget = target;
        final boolean finalSilent = silent;
        boolean isIp = IP_PATTERN.matcher(finalTarget).matches();

        CompletableFuture<Long> future;
        if (isIp) {
            future = agent.blacklistByIp(issuerUuid, issuerName, finalTarget, reason);
        } else {
            future = agent.blacklistByName(issuerUuid, issuerName, finalTarget, reason);
        }
        future.whenComplete((generatedId, throwable) -> {
            if (throwable != null) {
                String errMsg = throwable.getMessage();
                if (throwable instanceof java.util.concurrent.CompletionException
                        && throwable.getCause() != null) {
                    errMsg = throwable.getCause().getMessage();
                }
                source.sendMessage(Text.colorize("&8‹&4⚠&8›&cErrore: &f" + errMsg));
                return;
            }

            String issuerSuffix = playerIssuer != null ? agent.getLuckPermsHook().suffix(playerIssuer) : "";
            String serverOrigin = playerIssuer != null
                    ? playerIssuer.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse("unknown")
                    : "console";

            String responseMsg = messages.raw("blacklist.response",
                    "$playerSuffix", "",
                    "$playerDisplayName", finalTarget,
                    "$reason", reason,
                    "$id", String.valueOf(generatedId));
            if (finalSilent) {
                responseMsg = messages.raw("blacklist.silent_prefix") + responseMsg;
            }
            source.sendMessage(Text.colorize(responseMsg));

            if (!finalSilent) {
                String rawBroadcast = messages.raw("blacklist.broadcast",
                        "$playerSuffix", "",
                        "$playerDisplayName", finalTarget,
                        "$reason", reason,
                        "$serverOrigin", serverOrigin);
                String[] parts = rawBroadcast.split("\\$executorSuffix\\$executor", 2);

                Component broadcastComponent = Component.empty();

                // 1. Aggiungiamo la parte prima del nome
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    broadcastComponent = broadcastComponent.append(Text.colorize(parts[0]));
                }

                Component executorComponent = Text.colorize(issuerSuffix + issuerName);

                if (parts.length > 1 && !parts[1].isEmpty()) {
                    Component hoverText = Text.colorize(parts[1]);
                    executorComponent = executorComponent.hoverEvent(HoverEvent.showText(hoverText));
                }

                broadcastComponent = broadcastComponent.append(executorComponent);

                // 5. Inviamo a tutti i giocatori online
                final Component finalBroadcast = broadcastComponent;
                agent.getProxy().getAllPlayers().forEach(p -> p.sendMessage(finalBroadcast));
            }
        });
    }
}