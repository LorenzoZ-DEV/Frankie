package dev.lorenzz.frankie.staffnotes;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.database.PlayerRepository;
import dev.lorenzz.frankie.database.PlayerRepository.CachedPlayer;
import dev.lorenzz.frankie.database.StaffNoteRepository;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;


@CommandAlias("staffnotes|notes")
@CommandPermission("frankie.staffnotes")
public class StaffNotesCommand extends BaseCommand {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProxyServer proxy;
    private final Messages messages;
    private final StaffNoteRepository notes;
    private final PlayerRepository players;

    public StaffNotesCommand(ProxyServer proxy, Messages messages,
                             StaffNoteRepository notes, PlayerRepository players) {
        this.proxy = proxy;
        this.messages = messages;
        this.notes = notes;
        this.players = players;
    }

    @Default
    @CommandCompletion("@players")
    public void onNotes(Player player, @Optional String targetName, @Optional String note) {
        if (targetName == null) {
            player.sendMessage(messages.get("staffnotes.usage"));
            return;
        }
        resolve(targetName).thenAccept(resolved -> {
            if (resolved.isEmpty()) {
                player.sendMessage(messages.get("staffnotes.player_not_found", "%target%", targetName));
                return;
            }
            CachedPlayer target = resolved.get();
            if (note == null || note.isBlank()) {
                list(player, target);
                return;
            }
            notes.add(target.uuid(), target.name(), player.getUniqueId(), player.getUsername(), note)
                    .thenRun(() -> player.sendMessage(messages.get("staffnotes.added",
                            "%target%", target.name())));
        });
    }

    private void list(Player player, CachedPlayer target) {
        notes.getByTarget(target.uuid()).thenAccept(list -> {
            if (list.isEmpty()) {
                player.sendMessage(messages.get("staffnotes.list_empty", "%target%", target.name()));
                return;
            }
            player.sendMessage(messages.get("staffnotes.list_header",
                    "%target%", target.name(), "%count%", String.valueOf(list.size())));
            list.forEach(entry -> player.sendMessage(messages.get("staffnotes.list_entry",
                    "%author%", entry.author(),
                    "%date%", entry.createdAt().format(DATE),
                    "%note%", entry.note())));
        });
    }

    private CompletableFuture<java.util.Optional<CachedPlayer>> resolve(String name) {
        java.util.Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            Player found = online.get();
            return CompletableFuture.completedFuture(
                    java.util.Optional.of(new CachedPlayer(found.getUniqueId(), found.getUsername())));
        }
        return players.findByName(name);
    }
}