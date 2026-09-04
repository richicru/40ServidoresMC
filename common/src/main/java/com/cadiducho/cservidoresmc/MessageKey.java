package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Catálogo de mensajes traducibles del plugin.
 * Los textos por defecto están en español; pueden sobreescribirse desde la configuración
 * bajo la clave {@code messages.<key>}.
 */
public enum MessageKey {
    VOTE_FETCHING("vote-fetching", "&7Obteniendo voto..."),
    VOTE_NOT_VOTED_PREFIX("vote-not-voted-prefix", "&6No has votado hoy! Puedes hacerlo en &a"),
    VOTE_ALREADY_REWARDED("vote-already-rewarded", "&aGracias por votar, pero ya has obtenido tu premio!"),
    VOTE_INVALID_KEY("vote-invalid-key", "&cClave incorrecta. Entra en &bhttps://40servidoresmc.es/miservidor.php &cy cambia esta."),
    VOTE_ERROR("vote-error", "&7Ha ocurrido un error. Prueba más tarde o avisa a un administrador"),
    VOTE_EXCEPTION("vote-exception", "&cHa ocurrido una excepción. Avisa a un administrador"),

    STATS_INVALID_KEY("stats-invalid-key", "&cClave incorrecta. Entra en &bhttps://40servidoresmc.es/miservidor.php &cy cambia esta."),
    STATS_HEADER("stats-header", "&9==> &7{server} &festá en el TOP &a{position}"),
    STATS_DAY_VOTES("stats-day-votes", "&bVotos hoy: &6{count}"),
    STATS_DAY_VOTES_REWARDED("stats-day-votes-rewarded", "&bVotos premiados hoy: &6{count}"),
    STATS_WEEK_VOTES("stats-week-votes", "&bVotos semanales: &6{count}"),
    STATS_WEEK_VOTES_REWARDED("stats-week-votes-rewarded", "&bVotos premiados semanales: &6{count}"),
    STATS_LAST_VOTES("stats-last-votes", "&bÚltimos 20 votos: {votes}"),
    STATS_EXCEPTION("stats-exception", "&cHa ocurrido una excepción. Revisa la consola o avisa a un administrador"),

    CMD_NO_PERMISSION("cmd-no-permission", "&cNo tienes permiso para usar este comando"),
    CMD_COOLDOWN("cmd-cooldown", "&6No puedes ejecutar este comando tantas veces seguidas!"),
    CMD_ERROR("cmd-error", "&cHa ocurrido un error inesperado"),
    CMD_ONLY_PLAYER("cmd-only-player", "&cEste comando sólo puede ser ejecutado por usuarios"),

    RELOAD_SUCCESS("reload-success", "&aConfiguración recargada correctamente"),
    RELOAD_VERSION("reload-version", "&aFuncionando la versión {version}"),

    TEST_HEADER("test-header", "&bPlataforma de test para 40ServidoresMC:"),

    UPDATE_NO_INFO("update-no-info", "No se pudo obtener la información de versiones."),
    UPDATE_NO_NEW("update-no-new", "No hay versión más moderna recomendada para tu versión de Minecraft.");

    private final String key;
    private final String defaultValue;

    MessageKey(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public String resolve(CSConfiguration config) {
        return config.getString("messages." + key, defaultValue);
    }

    /**
     * Resolver y aplicar sustituciones de placeholders en el formato {nombre}.
     */
    public String resolve(CSConfiguration config, Map<String, String> placeholders) {
        String msg = resolve(config);
        if (placeholders == null || placeholders.isEmpty()) return msg;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
        }
        return msg;
    }

    public String resolve(CSConfiguration config, String placeholder, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(placeholder, value);
        return resolve(config, map);
    }
}
