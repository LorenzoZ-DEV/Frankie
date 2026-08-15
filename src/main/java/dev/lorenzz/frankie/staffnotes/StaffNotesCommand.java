package dev.lorenzz.frankie.staffnotes;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import com.velocitypowered.api.proxy.Player;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.database.PlayerRepository.CachedPlayer;
import dev.lorenzz.frankie.database.StaffNoteRepository;
import dev.lorenzz.frankie.player.PlayerResolver;
import dev.lorenzz.frankie.util.Futures;

import java.time.format.DateTimeFormatter;

@CommandAlias("staffnotes|notes")
@CommandPermission("frankie.staffnotes")
public class StaffNotesCommand extends BaseCommand {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Messages messages;
    private final StaffNoteRepository notes;
    private final PlayerResolver resolver;
    private final Futures futures;

    public StaffNotesCommand(Messages messages, StaffNoteRepository notes,
                             PlayerResolver resolver, Futures futures) {
        this.messages = messages;
        this.notes = notes;
        this.resolver = resolver;
        this.futures = futures;
    }

    @Default
    @Syntax("<giocatore> [nota]")
    @Description("Legge o aggiunge una nota staff su un giocatore.")
    @CommandCompletion("@players")
    public void onNotes(Player player, @Optional String targetName, @Optional String note) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(messages.get("staffnotes.usage"));
            return;
        }
        futures.handle(resolver.resolve(targetName), player, "resolve " + targetName, resolved -> {
            if (resolved.isEmpty()) {
                player.sendMessage(messages.get("staffnotes.player_not_found", "%target%", targetName));
                return;
            }
            CachedPlayer target = resolved.get();
            if (note == null || note.isBlank()) {
                list(player, target);
                return;
            }
            futures.handle(
                    notes.add(target.uuid(), target.name(), player.getUniqueId(), player.getUsername(), note),
                    player, "staff note add", done -> player.sendMessage(
                            messages.get("staffnotes.added", "%target%", target.name())));
        });
    }

    private void list(Player player, CachedPlayer target) {
        futures.handle(notes.getByTarget(target.uuid()), player, "staff notes list", list -> {
            if (list.isEmpty()) {
                player.sendMessage(messages.get("staffnotes.list_empty", "%target%", target.name()));
                return;
            }
            player.sendMessage(messages.get("staffnotes.list_header",
                    "%target%", target.name(),
                    "%count%", String.valueOf(list.size())));
            list.forEach(entry -> player.sendMessage(messages.get("staffnotes.list_entry",
                    "%author%", entry.author(),
                    "%date%", entry.createdAt().format(DATE),
                    "%note%", entry.note())));
        });
    }
}
