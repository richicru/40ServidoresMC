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

        // saveDefaultConfig() crea el directorio del plugin si no existe y copia
        // config.yml desde los resources del JAR si el usuario aún no lo tiene.
        // Sin esto, los nuevos installs se quedan sin carpeta y sin config (issue v3.0.2).
        saveDefaultConfig();

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
     * Ejecutar un comando de consola delegando al scheduler correcto y devuelve
     * si se pudo despachar y ejecutar sin error.
     *
     * <p><b>Bug real corregido 2026-09-06</b> (detectado por el operador de
     * 40servidoresmc.es antes de publicar v3.1.1): la versión anterior SIEMPRE
     * devolvía {@code false}, sin importar si el comando funcionaba. Programaba
     * {@code r} vía {@code runTask()}/{@code GlobalRegionScheduler.run()} —que
     * NUNCA ejecutan inline, ni siquiera llamados desde el main thread; como
     * poco esperan al siguiente tick— y devolvía {@code result[0]} justo
     * después, antes de que {@code r} llegara a correr. El comando SÍ se
     * despachaba (un tick más tarde), pero el método mentía sobre el
     * resultado.
     *
     * <p>Consecuencia real: {@code dispatchRewards()} (VoteCMD) siempre veía
     * {@code allOk=false} → el ack de v3 siempre viajaba con
     * {@code entregado:false} → el server SIEMPRE liberaba la reserva del
     * voto → el mismo voto volvía a ofrecerse en el siguiente
     * {@code /voto40}. Un jugador podía cobrar el mismo voto web una y otra
     * vez sin límite, exactamente lo contrario de lo que v3 se diseñó para
     * evitar. Ningún test lo detectó porque los 140 unitarios mockean este
     * método (siempre {@code true}) y el suite de Docker sólo golpea el mock
     * HTTP, nunca ejecuta {@code /voto40} de verdad contra un Paper/Folia real
     * (ver {@code scripts/test-vote-scenarios.sh}, escenario marcado como
     * manual).
     *
     * <p>La corrección: si ya estamos en un thread síncrono válido
     * (comprobado con {@link Bukkit#isPrimaryThread()} — cierto siempre en
     * Bukkit/Paper clásico, y cierto en Folia sólo para el hilo de la región
     * global), ejecutamos directo, sin reprogramar nada, y el resultado es
     * real. Si no (Folia: llamado desde el {@code EntityScheduler} de un
     * jugador, que NO es el hilo primario), aplicamos el mismo patrón
     * bloqueante que ya usa {@link #runSyncForPlayerWithResult} más abajo:
     * programar y esperar con un {@code CountDownLatch} hasta tener el
     * resultado real, en vez de leerlo antes de tiempo.</p>
     */
    @Override
    public boolean dispatchCommand(final String command) {
        // OJO: en Folia, Bukkit.isPrimaryThread() NO significa "seguro para
        // despachar un comando de consola" -- devuelve true tambien dentro
        // del thread de region de un jugador (llamado desde
        // runOnEntityScheduler(), el caso real de dispatchRewards()), pero
        // Folia exige el thread de la REGION GLOBAL especificamente para
        // comandos de consola. Bug real corregido 2026-09-06, verificado con
        // un Folia real: la version anterior tomaba este atajo tambien en
        // Folia y Bukkit.dispatchCommand() lanzaba "Dispatching command
        // async" en cada /voto40 (el premio no se entregaba nunca). En
        // Folia SIEMPRE se pasa por runOnGlobalScheduler(); el atajo directo
        // queda solo para clasico, donde isPrimaryThread() si es inequivoco.
        if (!folia && Bukkit.isPrimaryThread()) {
            return runConsoleCommand(command);
        }

        final boolean[] result = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Runnable r = () -> {
            try {
                result[0] = runConsoleCommand(command);
            } finally {
                latch.countDown();
            }
        };
        if (folia) {
            runOnGlobalScheduler(r);
        } else {
            getServer().getScheduler().runTask(this, r);
        }
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
        return result[0];
    }

    /** Ejecuta el comando de consola de verdad. Debe llamarse ya en un thread válido. */
    private boolean runConsoleCommand(String command) {
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Comando '" + command + "' lanzó excepción: " + t.getMessage());
            return false;
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
                runOnEntityScheduler(p, () -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', message)));
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
        runOnEntityScheduler(p, task);
    }

    /**
     * Versión síncrona con retorno. En Bukkit clásico: runTask bloqueante y devuelve
     * el resultado. En Folia: usamos {@code CompletableFuture.get()} sobre la entity
     * scheduler para bloquear el thread actual hasta que termine. Esto es seguro
     * porque:
     * <ul>
     *   <li>En Bukkit clásico corremos en main thread del scheduler (no hay riesgo
     *       de deadlock).</li>
     *   <li>En Folia corremos desde el thread I/O (cservidoresmc-io) que no es la
     *       región dueña del jugador, así que bloquear hasta que la tarea en su
     *       scheduler termine es la forma correcta de hacer bridging.</li>
     * </ul>
     */
    @Override
    public <T> T runSyncForPlayerWithResult(String playerName, java.util.function.Supplier<T> task) {
        if (task == null) return null;
        if (!folia) {
            // Clásico: usamos CountDownLatch para garantizar que podemos devolver el resultado.
            final java.util.concurrent.atomic.AtomicReference<T> ref = new java.util.concurrent.atomic.AtomicReference<>();
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            getServer().getScheduler().runTask(this, () -> {
                ref.set(task.get());
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
            return ref.get();
        }
        org.bukkit.entity.Player p = getServer().getPlayerExact(playerName);
        if (p == null) {
            // Jugador offline: el caller asume que ya manejamos el caso; ejecutamos directo.
            return task.get();
        }
        // Folia: usamos entity scheduler vía reflection + CompletableFuture para
        // esperar el resultado. No podemos bloquear el thread principal pero sí
        // el thread I/O (nunca es la región del jugador).
        final java.util.concurrent.CompletableFuture<T> fut = new java.util.concurrent.CompletableFuture<>();
        boolean scheduled = runOnEntityScheduler(p, () -> {
            try {
                fut.complete(task.get());
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        if (!scheduled) {
            // El jugador se retiró (se desconectó) antes de que le tocara turno,
            // o el EntityScheduler no está disponible (Folia demasiado viejo).
            // Sin esto, `fut.get()` bloquearía este thread PARA SIEMPRE: es
            // exactamente el bug real de 2026-09-06 (dos hilos de
            // cservidoresmc-io colgados sin límite en fut.get(), verificado con
            // un thread dump contra un Folia real) -- runOnEntityScheduler()
            // pasaba el trabajo real como callback de "retirado" en vez de
            // como el trabajo, así que nunca se ejecutaba mientras el jugador
            // siguiera conectado (el caso normal).
            return task.get();
        }
        try {
            return fut.get();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isPlayerOnline(String playerName) {
        return playerName != null && getServer().getPlayerExact(playerName) != null;
    }

    @Override
    public void runSyncGlobal(Runnable task) {
        if (task == null) return;
        if (!folia) {
            getServer().getScheduler().runTask(this, task);
            return;
        }
        runOnGlobalScheduler(task);
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
            runOnEntityScheduler(p, () -> action.accept(new BukkitCommandSender(p, this)));
        }
    }

    /**
     * Agenda {@code task} en el {@code EntityScheduler} de {@code player} (Folia).
     *
     * <p><b>Bug real corregido 2026-09-06</b> (detectado con un thread dump
     * contra un Folia real: dos hilos de {@code cservidoresmc-io} bloqueados
     * para siempre en {@code fut.get()}, ver
     * {@link #runSyncForPlayerWithResult}): la firma real de
     * {@code EntityScheduler.run} (verificada extrayendo la clase del propio
     * jar de Folia, no de memoria) es</p>
     * <pre>
     * ScheduledTask run(Plugin plugin, Consumer&lt;ScheduledTask&gt; task, Runnable retired)
     * </pre>
     * <p>El trabajo real va en el {@code Consumer} (2º parámetro); el
     * {@code Runnable} (3º) es "retired" -- SOLO se ejecuta si la entidad se
     * retira (jugador se desconecta) ANTES de que le toque turno. La versión
     * anterior los tenía invertidos: pasaba un no-op como el trabajo real y
     * el trabajo real como "retired", así que mientras el jugador siguiera
     * conectado (el caso normal) NUNCA se ejecutaba nada.</p>
     *
     * @return true si se programó en el EntityScheduler de verdad (Folia
     *         disponible); false si tocó el fallback al scheduler clásico
     *         (jugador retirado antes de programar, o Folia no disponible) --
     *         el caller debe entonces asumir que {@code task} pudo NO
     *         haberse ejecutado todavía de forma síncrona con esta llamada.
     */
    private boolean runOnEntityScheduler(Player player, Runnable task) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            java.util.function.Consumer<Object> realWork = scheduledTask -> task.run();
            Object scheduled = scheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                    .invoke(scheduler, this, realWork, RETIRED_NOOP);
            // run() devuelve null si la entidad ya estaba retirada -- Folia
            // documenta esto como "no se programó, ejecuta tu propio fallback".
            if (scheduled == null) {
                getServer().getScheduler().runTask(this, task);
                return false;
            }
            return true;
        } catch (Throwable t) {
            getServer().getScheduler().runTask(this, task);
            return false;
        }
    }

    /**
     * Agenda {@code task} en el {@code GlobalRegionScheduler} (Folia): consola,
     * updater, broadcasts que no pertenecen a ningún jugador concreto.
     *
     * <p>Bug real corregido 2026-09-06 (mismo día que {@link #runOnEntityScheduler}):
     * la firma real de {@code GlobalRegionScheduler.run} (verificada contra el
     * jar real) es {@code ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task)}
     * -- SOLO DOS parámetros, sin "retired" (no tiene sentido para un scheduler
     * que no pertenece a una entidad que pueda retirarse). El código buscaba
     * este método con TRES parámetros (arrastrado del patrón de
     * EntityScheduler, que sí lleva "retired"); esa búsqueda por reflection
     * fallaba SIEMPRE con NoSuchMethodException y caía al scheduler clásico
     * de Bukkit -- que Folia rechaza activamente (UnsupportedOperationException),
     * así que esta rama nunca llegó a funcionar en un Folia real.</p>
     */
    private void runOnGlobalScheduler(Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            java.util.function.Consumer<Object> realWork = scheduledTask -> task.run();
            scheduler.getClass()
                    .getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class)
                    .invoke(scheduler, this, realWork);
        } catch (Throwable t) {
            getServer().getScheduler().runTask(this, task);
        }
    }

    /** "Retired" no-op para EntityScheduler.run(): no hacemos nada especial si la entidad se retira antes de su turno. */
    private static final Runnable RETIRED_NOOP = () -> {};
}
