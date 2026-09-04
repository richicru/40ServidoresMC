package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.StatsCache;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConfiguration;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommandManager;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.google.gson.Gson;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * Implementación para Bukkit, Spigot y Glowstone
 * @author Cadiducho
 */
public class BukkitPlugin extends JavaPlugin implements CSPlugin {

    @Getter private ApiClient apiClient;
    @Getter private Updater updater;
    @Getter private StatsCache statsCache;

    private static BukkitPlugin instance;

    private CSConfiguration csConfiguration;
    private CSCommandManager commandManager;
    
    @Override
    public void onEnable() {
        instance = this;

        /*
         * Generar y cargar Config.yml
         */
        csConfiguration = new BukkitConfigurationAdapter(instance, new File(getDataFolder() + File.separator + "config.yml"));

        apiClient = new ApiClient(instance, new Gson());
        statsCache = new StatsCache(instance);

        /*
         * Comandos y eventos
         */
        debugLog("Registrando comandos y eventos...");
        registerCommands();

        installPlaceholderAPI();
        
        Metrics metrics = new Metrics(instance, 3909);

        /*
         * Finalizar...
         */
        String branch = csConfiguration.getString("update-branch", Updater.DEFAULT_BRANCH);
        updater = new Updater(instance, getPluginVersion(), getServer().getBukkitVersion().split("-")[0],
                Updater.DEFAULT_REPO, branch);
        debugLog("Checkeando nuevas versiones (branch=" + branch + ")...");
        updater.checkearVersion(null);
        log("Plugin 40ServidoresMC v" + getPluginVersion() + " cargado completamente");

        checkDefaultKey();
    }

    @Override
    public void registerCommands() {
        this.commandManager = new CSCommandManager(instance);
    }

    /**
     * Comprobar si el plugin PlaceholderAPI está activo, y si es así registrar la extensión
     */
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
    public void logError(String s){
       getLogger().log(Level.SEVERE, s);
    }

    @Override
    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }

    @Override
    public void dispatchCommand(String command) {
        getServer().getScheduler().callSyncMethod(instance, () -> getServer().dispatchCommand(getServer().getConsoleSender(), command));
    }

    @Override
    public void broadcastMessage(String message) {
        getServer().getScheduler().runTask(instance, () -> {
            getServer().getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', message)));
        });
    }

    @Override
    public String getPlayerIp(String playerName) {
        org.bukkit.entity.Player player = getServer().getPlayerExact(playerName);
        if (player == null) return null;
        java.net.InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) return null;
        return address.getAddress().getHostAddress();
    }

}
