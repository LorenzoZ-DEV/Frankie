package dev.lorenzz.frankie.blacklist;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.database.BlacklistRepository.BlacklistRecord;
import dev.lorenzz.frankie.util.Text;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class UnblacklistCommand extends BaseCommand {
    private final BlacklistAgent agent;

    public UnblacklistCommand(BlacklistAgent agent) {
        this.agent = agent;
    }

    @CommandAlias("unblacklist|unbl")
    @CommandPermission("frankie.admin")
    @CommandCompletion("@players")
    public void onUnblacklistCommand(CommandIssuer issuer, String... args) {
        CommandSource source = (CommandSource) issuer.getIssuer();

        String victimName = null;
        boolean silent = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
            } else if (victimName == null) {
                victimName = arg;
            }
        }
        if (victimName == null) {
            source.sendMessage(Text.colorize("&8‹&4⚠&8›&f &7Utilizzo: &f/unblacklist (-s) <giocatore>"));
            return;
        }
        final String finalVictimName = victimName;
        final boolean isSilent = silent;

        CompletableFuture<Optional<BlacklistRecord>> future = agent.unblacklistByName(finalVictimName);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                String errMsg = throwable.getMessage();
                if (throwable instanceof java.util.concurrent.CompletionException
                        && throwable.getCause() != null) {
                    errMsg = throwable.getCause().getMessage();
                }
                source.sendMessage(Text.colorize("&8‹&4⚠&8›&cErrore durante l'unblacklist: &f" + errMsg));
                return;
            }

            if (result.isEmpty()) {
                source.sendMessage(Text.colorize("&8‹&4⚠&8›&f &cIl giocatore &f" + finalVictimName + " &cnon è in blacklist."));
                return;
            }

            BlacklistRecord record = result.get();
            Player playerIssuer = issuer.getIssuer() instanceof Player p ? p : null;
            String issuerName = playerIssuer != null ? playerIssuer.getUsername() : "Console";
            String issuerSuffix = playerIssuer != null ? agent.getLuckPermsHook().suffix(playerIssuer) : "";
            String victimNameActual = record.victimName;
            String victimSuffix = record.luckpermsSuffix;

            String responseMsg = "&8‹&4⚠&8›&f &aHai rimosso il blacklist con successo &r" +
                    victimSuffix + victimNameActual + " &aper";
            if (isSilent) {
                responseMsg = "&9[S] &r" + responseMsg;
            }
            source.sendMessage(Text.colorize(responseMsg));

            if (!isSilent) {
                String broadcastMsg = "&f" + victimSuffix + victimNameActual +
                        " &aè stato rimosso dalla blacklist &fda &f" +
                        issuerSuffix + issuerName;
                agent.getProxy().getAllPlayers().forEach(p -> p.sendMessage(Text.colorize(broadcastMsg)));
            }
        });
    }
}