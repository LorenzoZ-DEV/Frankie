package dev.lorenzz.frankie.request;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
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

/**
 * /request <reason> — helpop-style assistance request sent to the staff.
 */
@CommandAlias("request|helpop")
public class RequestCommand extends BaseCommand {

    public static final String PERMISSION_RECEIVE = "frankie.request.receive";

    private final ProxyServer proxy;
    private final Messages messages;
    private final ReportRepository reports;
    private final YamlConfig config;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    public RequestCommand(ProxyServer proxy, Messages messages, ReportRepository reports, YamlConfig config) {
        this.proxy = proxy;
        this.messages = messages;
        this.reports = reports;
        this.config = config;
    }

    @Default
    public void onRequest(Player player, @Optional String reason) {
        if (reason == null || reason.isBlank()) {
            player.sendMessage(messages.get("request.usage"));
            return;
        }
        long remaining = Cooldowns.remaining(lastRequest, player.getUniqueId(),
                config.getLong("request.cooldown-seconds", 60L));
        if (remaining > 0) {
            player.sendMessage(messages.get("request.cooldown", "%time%", String.valueOf(remaining)));
            return;
        }
        String server = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("?");

        lastRequest.put(player.getUniqueId(), System.currentTimeMillis());
        reports.saveRequest(player.getUniqueId(), player.getUsername(), reason, server);

        player.sendMessage(messages.get("request.sent"));
        Component staffMessage = messages.get("request.staff_format",
                "%user%", player.getUsername(),
                "%server%", server,
                "%reason%", reason);
        Broadcast.toPermission(proxy, PERMISSION_RECEIVE, staffMessage);
    }
}