package dev.lorenzz.frankie.blacklist;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
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
    public void onUnblacklistCommand(Player player, String... args) {
        String victimName = null;
        boolean silent = false;
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
                i++;
            } else if (victimName == null) {
                victimName = arg;
                i++;
            } else {
                // unexpected extra token; ignore for simplicity
                i++;
            }
        }
        if (victimName == null) {
            player.sendMessage(Text.colorize("&8‹&4⚠&8›&f &7Utilizzo: &f/unblacklist (-s) <giocatore>"));
            return;
        }
        final String finalVictimName = victimName;
        final boolean isSilent = silent;

        // Call unblacklistByName and handle future
        CompletableFuture<Optional<BlacklistRecord>> future = agent.unblacklistByName(player, finalVictimName);
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                player.sendMessage(Text.colorize("&8‹&4⚠&8›&cErrore durante l'unblacklist: &f" + throwable.getMessage()));
                return;
            }

            if (result.isEmpty()) {
                player.sendMessage(Text.colorize("&8‹&4⚠&8›&f &cIl giocatore &f" + finalVictimName + " &cnon è in blacklist."));
                return;
            }

            BlacklistRecord record = result.get();
            // We have the record: victimUuid, victimName, luckpermsSuffix, ip
            // Note: the victimName in the record is the name at the time of blacklist.
            // We'll use that for the message.

            String issuerName = player.getUsername();
            String issuerSuffix = agent.getLuckPermsHook().suffix(player);
            String victimNameActual = record.victimName; // from record
            String victimSuffix = record.luckpermsSuffix; // from record

            // Build response message for issuer
            String responseMsg = "&8‹&4⚠&8›&f &aHai rimosso il blacklist con successo &r" +
                    victimSuffix + victimNameActual + " &aper";
            if (isSilent) {
                responseMsg = "&9[S] &r" + responseMsg;
            }
            player.sendMessage(Text.colorize(responseMsg));

            // If not silent, broadcast to all players
            if (!isSilent) {
                String broadcastMsg = "&f" + victimSuffix + victimNameActual +
                        " &aè stato rimosso dalla blacklist &fda &f" +
                        issuerSuffix + issuerName;
                // Send to all players
                agent.getProxy().getAllPlayers().forEach(p -> p.sendMessage(Text.colorize(broadcastMsg)));
            }
        });
    }
}