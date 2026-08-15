package dev.lorenzz.frankie.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import com.velocitypowered.api.command.CommandSource;
import dev.lorenzz.frankie.config.Messages;
import dev.lorenzz.frankie.core.FrankieServices;
import org.slf4j.Logger;

/**
 * /frankie — admin command. "reload" re-reads config.yml and messages.yml
 * live. MySQL credentials still need a proxy restart (the pool is not rebuilt).
 */
@CommandAlias("frankie")
@CommandPermission("frankie.admin")
public class FrankieCommand extends BaseCommand {

    private final FrankieServices services;
    private final Messages messages;
    private final Logger logger;

    public FrankieCommand(FrankieServices services) {
        this.services = services;
        this.messages = services.messages;
        this.logger = services.logger;
    }

    @Default
    public void onDefault(CommandSource source) {
        source.sendMessage(messages.get("general.credits"));
        source.sendMessage(messages.get("general.frankie_usage"));
    }

    @Subcommand("reload")
    public void onReload(CommandSource source) {
        try {
            services.reloadConfigs();
            source.sendMessage(messages.get("general.reload_success"));
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            source.sendMessage(messages.get("general.reload_failed"));
        }
    }
}