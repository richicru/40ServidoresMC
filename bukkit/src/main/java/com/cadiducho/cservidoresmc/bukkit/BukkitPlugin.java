package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.StatsCache;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommandManager;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.google.gson.Gson;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Implementación para Bukkit, Spigot, Paper y Folia.
 *
 * <p>El plugin detecta la presencia de Folia al arrancar (clase
 * {@code io.papermc.paper.threadedregions.RegionizedServer}) y bifurca los
 * puntos de acceso a la API: la red (ApiClient/Updater) sigue ejecutándose en
 * nuestro propio {@code ExecutorService}; toda llamada a la API de Bukkit desde
 * esa red se enruta al scheduler apropiado vía los métodos
 * {@link #runSyncForPlayer}, {@link #runSyncGlobal}, {@link #runForEachOnlinePlayer}.</p>
 *
 * @author Cadiducho (fork mantenido por richicru)
 */
public class BukkitPlugin extends JavaPlugin implements CSPlugin {

    @Getter private ApiClient apiClient;
    @Getter private Updater updater;
    @Getter private StatsCache statsCache;
    @Getter private StatsCache statsCmdCache;

    private static BukkitPlugin instance;

    private CSConfiguration csConfiguration;
    private CSCommandManager commandManager;

    /** {@code true} si el servidor es Folia. */
    @Getter private final boolean folia = FoliaDetector.isFoliaServer();

    @Override
    public void onEnable() {
        instance = this;

        if (folia) {
            getLogger().info("Folia detectado: usando schedulers region-aware.");
        }

        csConfiguration = new BukkitConfigurationAdapter(instance, new File(getDataFolder() + File.separator + "config.yml"));

        apiClient = new ApiClient(instance, new Gson());
        statsCache = new StatsCache(instance);
        int statsCmdTtl = csConfiguration.getInt("stats-cmd-cache-seconds", 30);
        if (statsCmdTtl > 0) {
            statsCmdCache = new StatsCache(instance, statsCmdTtl);
        }

        debugLog("Registrando comandos y eventos...");
        registerCommands();

        installPlaceholderAPI();

        Metrics metrics = new Metrics(instance, 3909);

        String repo = csConfiguration.getString("update-repo", Updater.DEFAULT_REPO);
        String branch = csConfiguration.getString("update-branch", Updater.DEFAULT_BRANCH);
        updater = Updater.forGitHub(instance, getPluginVersion(), getServer().getBukkitVersion().split("-")[0],
                repo, branch);
        debugLog("Checkeando nuevas versiones (" + repo + "@" + branch + ")...");
        updater.checkearVersion(null);
        log("Plugin 40ServidoresMC v" + getPluginVersion() + " cargado completamente");

        checkDefaultKey();
    }

    @Override
    public void registerCommands() {
        this.commandManager = new CSCommandManager(instance);
    }

    private void installPlaceholderAPI() {
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
        }
    }

    @Override
    public boolean onCommand(CommandSender bukkitSender, Command cmd, String label, String[] args) {
        if (label.startsWith(("40ServidoresMC:").toLowerCase())) {
            label = label.substring(("40ServidoresMC:").length());
        }
        CSCommandSender csCommandSender;
        if (bukkitSender instanceof ConsoleCommandSender) {
            csCommandSender = new CSConsoleSender(instance);
        } else {
            csCommandSender = new BukkitCommandSender(bukkitSender, this);
        }

        try {
            commandManager.executeCommand(csCommandSender, label, Arrays.asList(args));
        } catch (Exception ex) {
            logError("Error al ejecutar el comando '/" + label + Arrays.toString(args)+"'");
            debugLog(ex.getMessage());
            if (ex.getCause() != null) debugLog(ex.getCause().getMessage());
        }
        return true;
    }

    @Override
    public CSConfiguration getCSConfiguration() {
        return this.csConfiguration;
    }

    @Override
    public void log(String s) {
        getLogger().log(Level.INFO, s);
    }

    @Override
    public void logError(String s) {
       getLogger().log(Level.SEVERE, s);
    }

    @Override
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    /**
     * Ejecutar un comando de consola delegando al scheduler correcto.
     *
     * <p>Si Folia: {@code GlobalRegionScheduler} (la consola no pertenece a una región).</p>
     * <p>Si clásico: main thread scheduler.</p>
     */
    @Override
    public void dispatchCommand(String command) {
        Runnable r = () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (folia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                scheduler.getClass()
                        .getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                        .invoke(scheduler, this, CANCEL_TASK_SILENTLY, r);
            } catch (Throwable t) {
                // Folia scheduler no disponible (versión antigua) — fallback
                getServer().getScheduler().runTask(this, r);
            }
        } else {
            getServer().getScheduler().runTask(this, r);
        }
    }

    /**
     * Broadcast al servidor, enrutando a cada jugador por su EntityScheduler en Folia.
     */
    @Override
    public void broadcastMessage(String message) {
        if (folia) {
            // En Folia, BroadcastUtils.broadcastMessage sería lo ideal, pero como no existe
            // en Paper API 1.20.x en versiones antiguas, iteramos manualmente enroutando.
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    Object scheduler = p.getClass().getMethod("getScheduler").invoke(p);
                    Runnable send = () -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    scheduler.getClass()
                            .getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                            .invoke(scheduler, this, CANCEL_TASK_SILENTLY, send);
                } catch (Throwable t) {
                    // Fallback a main thread
                    Bukkit.getScheduler().runTask(this, sendColor(message, p));
                }
            }
            return;
        }
        // Clásico
        getServer().getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        });
    }

    @Override
    public String getPlayerIp(String playerName) {
        Player player = getServer().getPlayerExact(playerName);
        if (player == null) return null;
        java.net.InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) return null;
        return address.getAddress().getHostAddress();
    }

    @Override
    public String getServerPlatform() {
        return folia ? "Folia" : "Bukkit";
    }

    @Override
    public String getServerVersion() {
        String version = getServer().getBukkitVersion();
        if (version == null || version.isEmpty()) return "unknown";
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    // ---------- Scheduler abstraction (CSPlugin) ----------

    @Override
    public void runSyncForPlayer(String playerName, Runnable task) {
        if (task == null) return;
        if (!folia) {
            // Clásico: main-thread scheduler. Si ya estamos en main thread, runTask es válido.
            getServer().getScheduler().runTask(this, task);
            return;
        }
        Player p = getServer().getPlayerExact(playerName);
        if (p == null) {
            // Jugador offline: ejecutar "best-effort" aquí (los llamadores deben haber manejado este caso).
            task.run();
            return;
        }
        scheduleForEntity(p, task);
    }

    @Override
    public void runSyncGlobal(Runnable task) {
        if (task == null) return;
        if (!folia) {
            getServer().getScheduler().runTask(this, task);
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                    .invoke(scheduler, this, CANCEL_TASK_SILENTLY, task);
        } catch (Throwable t) {
            getServer().getScheduler().runTask(this, task);
        }
    }

    @Override
    public void runForEachOnlinePlayer(Consumer<CSCommandSender> action) {
        if (action == null) return;
        if (!folia) {
            getServer().getScheduler().runTask(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    action.accept(new BukkitCommandSender(p, this));
                }
            });
            return;
        }
        // Folia: enrutar a cada EntityScheduler
        for (Player p : Bukkit.getOnlinePlayers()) {
            scheduleForEntity(p, () -> action.accept(new BukkitCommandSender(p, this)));
        }
    }

    /**
     * Agenda una tarea en el EntityScheduler del jugador (Folia).
     * Si algo va mal (sin Folia, etc.), cae al main thread.
     */
    private void scheduleForEntity(Player player, Runnable task) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            scheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                    .invoke(scheduler, this, CANCEL_TASK_SILENTLY, task);
        } catch (Throwable t) {
            getServer().getScheduler().runTask(this, task);
        }
    }

    private static Runnable sendColor(String message, Player p) {
        return () -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /** Consumer que ignora la cancelación de la tarea (para schedulers Folia). */
    @SuppressWarnings("unchecked")
    private static final java.util.function.Consumer<Object> CANCEL_TASK_SILENTLY = t -> {};
}
