package dev.lorenzz.frankie.report;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.database.ReportRepository;
import dev.lorenzz.frankie.util.Broadcast;
import dev.lorenzz.frankie.util.Cooldowns;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CommandAlias("report")
public class ReportCommand extends BaseCommand {

    public static final String PERMISSION_RECEIVE = "frankie.report.receive";

    private final ProxyServer proxy;
    private final Messages messages;
    private final ReportRepository reports;
    private final YamlConfig config;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    public ReportCommand(ProxyServer proxy, Messages messages, ReportRepository reports, YamlConfig config) {
        this.proxy = proxy;
        this.messages = messages;
        this.reports = reports;
        this.config = config;
    }

    @Default
    @CommandCompletion("@players")
    public void onReport(Player player, @Optional String targetName, @Optional String reason) {
        if (targetName == null || reason == null || reason.isBlank()) {
            player.sendMessage(messages.get("report.usage"));
            return;
        }
        if (targetName.equalsIgnoreCase(player.getUsername())) {
            player.sendMessage(messages.get("report.self_report"));
            return;
        }
        long remaining = Cooldowns.remaining(lastReport, player.getUniqueId(),
                config.getLong("report.cooldown-seconds", 60L));
        if (remaining > 0) {
            player.sendMessage(messages.get("report.cooldown", "%time%", String.valueOf(remaining)));
            return;
        }
        java.util.Optional<Player> targetOptional = proxy.getPlayer(targetName);
        if (targetOptional.isEmpty()) {
            player.sendMessage(messages.get("report.player_not_found"));
            return;
        }
        Player target = targetOptional.get();
        String server = target.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("?");

        lastReport.put(player.getUniqueId(), System.currentTimeMillis());
        reports.saveReport(player.getUniqueId(), player.getUsername(),
                target.getUniqueId(), target.getUsername(), reason, server);

        player.sendMessage(messages.get("report.sent", "%target%", target.getUsername()));
        Component staffMessage = messages.get("report.staff_format",
                "%user%", player.getUsername(),
                "%target%", target.getUsername(),
                "%server%", server,
                "%reason%", reason);
        Broadcast.toPermission(proxy, PERMISSION_RECEIVE, staffMessage);
    }
}