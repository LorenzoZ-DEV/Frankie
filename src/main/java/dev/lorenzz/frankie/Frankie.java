package dev.lorenzz.frankie;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lorenzz.frankie.command.CommandHandler;
import dev.lorenzz.frankie.config.YamlConfig;
import dev.lorenzz.frankie.core.FrankieServices;
import dev.lorenzz.frankie.database.Database;
import dev.lorenzz.frankie.manager.ModuleManager;
import dev.lorenzz.frankie.util.AsciiArt;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "frankie", name = "Frankie", version = "1.0.1",
        dependencies = {
                @Dependency(id = "luckperms", optional = true),
                @Dependency(id = "nlogin", optional = true)
        })
public class Frankie {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private Database database;
    private FrankieServices services;

    @Inject
    public Frankie(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        AsciiArt.startup(logger);

        YamlConfig config;
        YamlConfig messagesConfig;
        try {
            config = YamlConfig.load(dataDirectory, "config.yml");
            messagesConfig = YamlConfig.load(dataDirectory, "messages.yml");
        } catch (Exception e) {
            logger.error("Failed to load configuration, Frankie is disabled", e);
            return;
        }

        try {
            database = new Database(config);
            database.createTables();
        } catch (Exception e) {
            logger.error("Failed to connect to MySQL, Frankie is disabled. Check config.yml", e);
            closeDatabase();
            return;
        }

        services = new FrankieServices(this, proxy, logger, dataDirectory,
                config, messagesConfig, database);
        new ModuleManager(services).registerListeners();
        new CommandHandler(services).register();

        logger.info("Frankie enabled successfully.");
        logger.info("Developed by Lorenzz.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (services != null) {
            services.close();
            services = null;
        }
        closeDatabase();
        AsciiArt.shutdown(logger);
    }

    /** Also runs when startup fails, so a half-built pool is not leaked. */
    private void closeDatabase() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
