package com.cadiducho.cservidoresmc.api;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.config.CSConfiguration;

import java.util.function.Consumer;

public interface CSPlugin {

    void log(String text);
    void logError(String text);

    default boolean isDebug() {
        return getCSConfiguration().getBoolean("debug");
    }

    default void debugLog(String s) {
        if (isDebug()){
            log("[Debug] " + s);
        }
    }

    /**
     * Registrar los comandos en la plataforma deseada
     */
    void registerCommands();

    /**
     * Datos de configuración del plugin, con implementación para cada tipo de servidor
     * @return config
     */
    CSConfiguration getCSConfiguration();

    /**
     * Obtener la versión de la configuración
     * @return la versión de la configuración
     */
    default int configVersion() {
        return 4;
    }

    /**
     * Comprobar si la configuración tiene una clave válida
     */
    default void checkDefaultKey() {
        if (getCSConfiguration().getInt("configVer", 0) != configVersion()) {
            logError("¡Tu configuración es de una versión más antigua a la de este plugin!");
            logError("Actualiza la configuración para evitar errores.");
        }
        if (getCSConfiguration().getString("clave", "key").equalsIgnoreCase("key")) {
            logError("¡Atención! La clave del servidor no está correctamente configurada");
            logError("Accede a la configuración y modifica 'clave' con el valor correcto obtenido en la página web.");
            logError("Este error hará que el plugin no funcione correctamente.");
        }
    }

    /**
     * Instancia del cliente HTTP para la API
     * @return API client
     */
    ApiClient getApiClient();

    /**
     * Instancia del actualizador
     * @return updater
     */
    Updater getUpdater();

    /**
     * La versión del plugin en String, por ejemplo "3.0"
     * @return versión
     */
    String getPluginVersion();

    /**
     * Ejecutar un comando deseado por la consola del servidor.
     *
     * @return {@code true} si el comando se despachó a un handler y ejecutó sin
     *         lanzar excepciones, {@code false} si no se encontró el comando o si
     *         el handler lanzó. Usado por el flujo v3 para detectar entregas
     *         fallidas y poder ackar con {@code entregado:false}.
     */
    boolean dispatchCommand(String command);

    /**
     * Enviar un mensaje a todos los usuarios
     * @param message el mensaje
     */
    void broadcastMessage(String message);

    /**
     * Obtener la IP del jugador por nombre. Devuelve null si está offline o no se puede determinar.
     * @param playerName Nombre del jugador
     * @return IP del jugador, o null si no está conectado
     */
    default String getPlayerIp(String playerName) {
        return null;
    }

    /**
     * Obtener (o inicializar perezosamente) la caché de estadísticas del servidor.
     * Usado por el sistema de placeholders para evitar llamadas API repetidas.
     */
    com.cadiducho.cservidoresmc.StatsCache getStatsCache();

    /**
     * Identificador corto de la plataforma, usado en el User-Agent HTTP.
     * Por defecto devuelve "Unknown". Las implementaciones Bukkit/Sponge lo sobrescriben.
     */
    default String getServerPlatform() {
        return "Unknown";
    }

    /**
     * Versión del servidor (Bukkit API, Minecraft) usada en el User-Agent HTTP.
     * Por defecto devuelve "unknown". Las implementaciones Bukkit/Sponge lo sobrescriben.
     */
    default String getServerVersion() {
        return "unknown";
    }

    /**
     * Caché de estadísticas para el comando /stats40, con TTL corto (por defecto 30 s).
     *
     * <p>Separada de {@link #getStatsCache()} (que sirve placeholders con TTL largo)
     * porque {@code /stats40} puede ejecutarse varias veces por minuto sin necesidad
     * de golpear la API cada vez.</p>
     *
     * <p>Por defecto devuelve {@code null}: el comando debe entonces llamar a la API
     * directamente. Las implementaciones reales (Bukkit/Sponge) la inicializan perezosamente
     * al arrancar leyendo {@code stats-cmd-cache-seconds} de la configuración.</p>
     */
    default com.cadiducho.cservidoresmc.StatsCache getStatsCmdCache() {
        return null;
    }

    /**
     * Ejecutar una tarea asociada a un jugador (mensaje, recompensa, etc.) en un
     * thread válido para acceder a su API.
     *
     * <p>En <b>Folia</b>: se agenda en el {@code EntityScheduler} del jugador, que
     * corre en el thread propietario de su región. Si el jugador está offline, se
     * ejecuta inmediatamente en el thread actual (responsabilidad de quien llama
     * comprobar si debe hacerlo).</p>
     *
     * <p>En <b>Paper / Bukkit clásico</b>: se agenda en el main thread scheduler.</p>
     *
     * <p>Las implementaciones distintas a Bukkit (Sponge) deben sobrescribir para
     * usar su propio scheduler; la implementación por defecto simplemente ejecuta
     * la tarea en línea.</p>
     */
    default void runSyncForPlayer(String playerName, Runnable task) {
        if (task != null) task.run();
    }

    /**
     * Versión con valor de retorno. Igual que {@link #runSyncForPlayer} pero la
     * tarea devuelve un resultado que el caller recibe en el thread actual.
     * Necesario para el flujo v3 de VoteCMD (decidir entregado:entregado:false
     * según si la entrega tuvo éxito).
     *
     * <p>La implementación por defecto bloquea hasta que la tarea termina si está
     * en un scheduler asíncrono. Las implementaciones Bukkit/Sponge pueden sobrescribir
     * para hacer el bridging correcto (en Bukkit clásico: runTask bloqueante; en Folia:
     * usando la API sincrónica sobre el EntityScheduler).</p>
     */
    default <T> T runSyncForPlayerWithResult(String playerName, java.util.function.Supplier<T> task) {
        return task == null ? null : task.get();
    }

    /**
     * ¿El jugador {@code playerName} está conectado en este momento?
     *
     * <p>Default: devuelve {@code true} (asumimos que sí si no se puede comprobar).
     * Las implementaciones Bukkit/Sponge lo sobrescriben con su API real.</p>
     */
    default boolean isPlayerOnline(String playerName) {
        return true;
    }

    /**
     * Ejecutar una tarea global (consola, updater, broadcast) en el thread principal
     * del servidor.
     *
     * <p>En <b>Folia</b>: {@code GlobalRegionScheduler}. En clásico: main thread.</p>
     */
    default void runSyncGlobal(Runnable task) {
        if (task != null) task.run();
    }

    /**
     * Ejecutar una acción contra cada jugador conectado, enrutando la ejecución
     * al thread correcto de cada uno (importante en Folia).
     *
     * <p>La implementación por defecto itera directamente — válido para plataformas
     * no-regionadas como Sponge o Paper clásico donde siempre se ejecuta desde
     * main thread.</p>
     */
    default void runForEachOnlinePlayer(Consumer<CSCommandSender> action) {
        if (action == null) return;
        // El default no debe iterar sin necesidad; las implementaciones reales (Bukkit)
        // lo sobreescriben.
    }
}
