package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.*;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.bstats.sponge.Metrics;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandManager;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameLoadCompleteEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Plugin(id = "cservidoresmc", name = "40ServidoresMC", version = SpongePlugin.PLUGIN_VERSION)
public class SpongePlugin implements CSPlugin {

    public static final String PLUGIN_VERSION = "3.1.1";
    @Inject private Logger logger;
    @Inject private Game game;

    private final Metrics metrics;

    private ApiClient apiClient;
    private Updater updater;
    private StatsCache statsCache;
    private StatsCache statsCmdCache;
    private CSConfiguration csConfiguration;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDirectory;
    private CSCommandManager csCommandManager;

    @Inject
    public SpongePlugin(Metrics.Factory metricsFactory) {
        int pluginId = 10604;
        metrics = metricsFactory.make(pluginId);
    }


    @Listener
    public void onServerLoad(GameLoadCompleteEvent event) {
        this.csConfiguration = new SpongeConfigAdapter(this, resolveConfig());

        registerCommands();
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        apiClient = new ApiClient(this, new Gson());
        statsCache = new StatsCache(this);
        int statsCmdTtl = csConfiguration.getInt("stats-cmd-cache-seconds", 30);
        if (statsCmdTtl > 0) {
            statsCmdCache = new StatsCache(this, statsCmdTtl);
        }
        String repo = csConfiguration.getString("update-repo", Updater.DEFAULT_REPO);
        String branch = csConfiguration.getString("update-branch", Updater.DEFAULT_BRANCH);
        updater = Updater.forGitHub(this, getPluginVersion(), this.game.getPlatform().getMinecraftVersion().getName(),
                repo, branch);
        updater.checkearVersion(new CSConsoleSender(this));

        checkDefaultKey();
    }

    private Path resolveConfig() {
        Path path = this.configDirectory.resolve("40ServidoresMC.conf");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(this.configDirectory);
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("40ServidoresMC.conf")) {
                    Files.copy(is, path);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return path;
    }

    @Override
    public void registerCommands() {
        this.csCommandManager = new CSCommandManager(this);
        CommandManager cmdService = Sponge.getCommandManager();
        this.csCommandManager.getCommands().forEach(cmd -> {
            List<String> alias = new ArrayList<>(cmd.getAliases());
            alias.add(cmd.getName());
            cmdService.register(this, new SpongeCommandExecutor(csCommandManager, cmd), alias);
        });
    }

    @Override
    public void log(String text) {
        logger.info(text);
    }

    @Override
    public void logError(String text) {
        logger.error(text);
    }

    @Override
    public CSConfiguration getCSConfiguration() {
        return this.csConfiguration;
    }

    @Override
    public ApiClient getApiClient() {
        return apiClient;
    }

    @Override
    public Updater getUpdater() {
        return updater;
    }

    @Override
    public StatsCache getStatsCache() {
        return statsCache;
    }

    @Override
    public StatsCache getStatsCmdCache() {
        return statsCmdCache;
    }

    @Override
    public String getPluginVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public boolean dispatchCommand(String command) {
        try {
            int success = Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command)
                    .getSuccessCount().orElse(0);
            return success > 0;
        } catch (Throwable t) {
            logger.warn("Comando '" + command + "' lanzó excepción: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void broadcastMessage(String message) {
        Sponge.getServer().getBroadcastChannel().send(TextSerializers.FORMATTING_CODE.deserialize(message));
    }

    @Override
    public String getPlayerIp(String playerName) {
        org.spongepowered.api.entity.living.player.Player player = Sponge.getServer().getPlayer(playerName).orElse(null);
        if (player == null) return null;
        java.net.InetSocketAddress address = player.getConnection().getAddress();
        if (address == null || address.getAddress() == null) return null;
        return address.getAddress().getHostAddress();
    }

    @Override
    public String getServerPlatform() {
        return "Sponge";
    }

    @Override
    public String getServerVersion() {
        try {
            String v = game.getPlatform().getMinecraftVersion().getName();
            return v != null && !v.isEmpty() ? v : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}