package dev.lorenzz.frankie.blacklist;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.blacklist.BlacklistAgent;
import dev.lorenzz.frankie.util.Text;

import java.util.concurrent.CompletableFuture;

public class BlacklistCommand extends BaseCommand {
    private final BlacklistAgent agent;

    public BlacklistCommand(BlacklistAgent agent) {
        this.agent = agent;
    }

    @CommandAlias("blacklist|bl|blist")
    @CommandPermission("frankie.admin")
    public void onBlacklistCommand(Player player, String... args) {
        String victimName = null;
        boolean silent = false;
        StringBuilder reasonBuilder = new StringBuilder();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-s")) {
                silent = true;
                i++;
            } else if (arg.equalsIgnoreCase("-p")) {
                // collect remaining tokens as reason
                i++;
                while (i < args.length) {
                    if (reasonBuilder.length() > 0) {
                        reasonBuilder.append(' ');
                    }
                    reasonBuilder.append(args[i]);
                    i++;
                }
                break; // reason consumes rest
            } else if (victimName == null) {
                victimName = arg;
                i++;
            } else {
                // unexpected extra token; treat as part of reason if no -p given? ignore for simplicity
                i++;
            }
        }
        if (victimName == null) {
            player.sendMessage(Text.colorize("&8‹&4⚠&8›&f &7Utilizzo: &f/blacklist (-p -s) <giocatore>"));
            return;
        }

        // Resolve victim player (must be online)
        Player victim = agent.getProxy().getPlayer(victimName).orElse(null);
        if (victim == null) {
            player.sendMessage(Text.colorize("&8‹&4⚠&8›&cGiocatore non online: &f" + victimName));
            return;
        }
        final String reason = reasonBuilder.toString();
        final boolean isSilent = silent;

        // Call blacklist and handle future
        CompletableFuture<Long> future = agent.blacklist(player, victim, reason);
        future.whenComplete((generatedId, throwable) -> {
            if (throwable != null) {
                player.sendMessage(Text.colorize("&8‹&4⚠&8›&cErrore durante il blacklist: &f" + throwable.getMessage()));
                return;
            }
            long id = generatedId;

            // Gather data for messages
            String issuerName = player.getUsername();
            String issuerSuffix = agent.getLuckPermsHook().suffix(player);
            String victimNameActual = victim.getUsername();
            String victimSuffix = agent.getLuckPermsHook().suffix(victim);
            String ip = victim.getRemoteAddress().getAddress().getHostAddress();

            // Server origin: server where the issuer is currently on
            String serverOrigin = player.getCurrentServer()
                    .map(conn -> conn.getServerInfo().getName())
                    .orElse("unknown");

            // Duration: since IP ban happens after 1 minute, we show "1m"
            String tempDuration = "1m";

            // Build hover text for broadcast
            String hoverText = "&7&m---------------\n" +
                               "&7Motivo: &c" + reason + "\n" +
                               "&7Durata: &c" + tempDuration + "\n" +
                               "&7Da: " + serverOrigin + "\n" +
                               "&7Ipban: &aSi\n" +
                               "&7&m---------------";

            // Build response message for issuer
            String responseMsg = "&8‹&4⚠&8›&f &aHai eseguito il blacklist con successo &r" +
                    victimSuffix + victimNameActual + " &aper &r" + reason +
                    "&aper la durata di " + tempDuration + " &aID: " + id;
            if (isSilent) {
                responseMsg = "&9[S] &r" + responseMsg;
            }
            player.sendMessage(Text.colorize(responseMsg));

            // If not silent, broadcast to all players
            if (!isSilent) {
                String broadcastMsg = "&f" + victimSuffix + victimNameActual +
                        " &aè stato bannato &fIP ban &fda &f" +
                        issuerSuffix + issuerName + " {hoverText: " + hoverText + "}";
                // Send to all players
                agent.getProxy().getAllPlayers().forEach(p -> p.sendMessage(Text.colorize(broadcastMsg)));
            }
        });
    }
}