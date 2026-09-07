package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSConfiguration;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommandManager;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.bstats.sponge.Metrics;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;

@Plugin(id = "cservidoresmc", name = "40ServidoresMC", version = SpongePlugin.PLUGIN_VERSION)
public class SpongePlugin implements CSPlugin {

    public static final String PLUGIN_VERSION = "3.0";
    @Inject private Logger logger;
    @Inject private Game game;

    private final Metrics metrics;

    private ApiClient apiClient;
    private Updater updater;
    private CSConfiguration csConfiguration;
    private CSCommandManager csCommandManager;

    @Inject
    public SpongePlugin(Metrics.Factory metricsFactory) {
        int pluginId = 10604;
        metrics = metricsFactory.make(pluginId);
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        csConfiguration = null; //new CSBukkitConfig(instance, getDataFolder() + File.separator + "config.yml");
        //csConfiguration.load(; // FixMe: Confdig

        apiClient = new ApiClient(this, new Gson());
        updater = new Updater(this, getPluginVersion(), this.game.getPlatform().getMinecraftVersion().getName());
        updater.checkearVersion(new CSConsoleSender(this));
    }

    @Override
    public void registerCommands() {
        this.csCommandManager = new CSCommandManager(this);
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
        return csConfiguration;
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
    public String getPluginVersion() {
        return PLUGIN_VERSION;
    }

    @Override
    public boolean dispatchCommand(String command) {
        // Módulo excluido del build activo (ver settings.gradle: "//include
        // 'sponge'"; vive sponge/api7 en su lugar). Se corrige igualmente la
        // firma 2026-09-06: CSPlugin.dispatchCommand() es `boolean` desde
        // v3.1.0 y esto no compilaría si algún día se reactiva. A diferencia
        // de BukkitPlugin (que necesitó reprogramar en el scheduler
        // correcto), Sponge.getCommandManager().process() ya es síncrono, así
        // que basta con leer su resultado real -- ver sponge/api7 para la
        // versión mantenida de este mismo patrón.
        try {
            int success = Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command)
                    .getSuccessCount().orElse(0);
            return success > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void broadcastMessage(String message) {
        Sponge.getServer().getBroadcastChannel().send(Text.of(message));
    }
}