package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;

import java.util.List;

@CommandAlias("slist|stafflist")
@CommandPermission("frankie.stafflist")
public class StaffListCommand extends BaseCommand {

    public static final String PERMISSION_STAFF = "frankie.staff";

    private final ProxyServer proxy;
    private final Messages messages;

    public StaffListCommand(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Default
    @Description("Mostra i membri dello staff online.")
    public void onStaffList(CommandSource source) {
        List<Player> onlineStaff = proxy.getAllPlayers().stream()
                .filter(player -> player.hasPermission(PERMISSION_STAFF))
                .toList();

        if (onlineStaff.isEmpty()) {
            source.sendMessage(messages.get("stafflist.empty"));
            return;
        }
        source.sendMessage(messages.get("stafflist.header", "%count%", String.valueOf(onlineStaff.size())));
        for (Player staff : onlineStaff) {
            source.sendMessage(messages.get("stafflist.format",
                    "%player%", staff.getUsername(),
                    "%server%", staff.getCurrentServer()
                            .map(connection -> connection.getServerInfo().getName())
                            .orElse("unknown")));
        }
    }
}
