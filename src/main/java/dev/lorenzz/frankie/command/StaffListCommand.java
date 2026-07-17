package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.stream.Collectors;

@CommandAlias("slist")
@CommandPermission("frankie.stafflist")
public class StaffListCommand extends BaseCommand {

    private final ProxyServer proxy;
    private final Messages messages;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public StaffListCommand(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Default
    public void onStaffList(Player player) {
        var onlineStaff = proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission("frankie.staff"))
                .collect(Collectors.toList());

        if (onlineStaff.isEmpty()) {
            player.sendMessage(LEGACY.deserialize(messages.raw("stafflist.empty")));
            return;
        }

        player.sendMessage(LEGACY.deserialize(messages.raw("stafflist.header")
                .replace("%count%", String.valueOf(onlineStaff.size()))));

        for (Player p : onlineStaff) {
            String server = p.getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("unknown");

            String line = messages.raw("stafflist.format")
                    .replace("%player%", p.getUsername())
                    .replace("%server%", server);

            player.sendMessage(LEGACY.deserialize(line));
        }
    }
}