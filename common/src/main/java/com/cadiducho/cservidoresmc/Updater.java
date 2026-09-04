package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSConsoleSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Clase para comprobar las actualizaciones a través de Github
 */
public class Updater {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final ExecutorService SHARED_EXECUTOR = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "cservidoresmc-updater");
        t.setDaemon(true);
        return t;
    });

    private final CSPlugin plugin;
    private final String versionInstalada;
    private final String versionMinecraft;
    private final String updateUrl;

    private final String ERROR = "Error obteniendo la versión.";
    private final String UPDATED = "Versión actualizada";
    private final String NEW_VERSION = "Versión desactualizada. Nueva versión: %s. Changelog: %s. Descarga en: %s";

    public static final String DEFAULT_REPO = "richicru/40ServidoresMC";
    public static final String DEFAULT_BRANCH = "dev";
    public static final String DEFAULT_UPDATE_PATH = "etc/v3.json";

    private final String repo;

    /**
     * Crea un updater con los defaults (richicru/40ServidoresMC @ dev).
     */
    public Updater(CSPlugin instance, String vInstalada, String vMinecraft) {
        this(instance, vInstalada, vMinecraft,
                buildUpdateUrl(DEFAULT_REPO, DEFAULT_BRANCH), DEFAULT_REPO);
    }

    /**
     * Crea un updater apuntando a un repo y branch específicos de GitHub.
     * La URL se construye automáticamente.
     */
    public static Updater forGitHub(CSPlugin instance, String vInstalada, String vMinecraft,
                                    String repo, String branch) {
        return new Updater(instance, vInstalada, vMinecraft, buildUpdateUrl(repo, branch), repo);
    }

    /**
     * Constructor principal. Si el repo es null/vacío, se extrae de la URL o se usa DEFAULT_REPO.
     */
    public Updater(CSPlugin instance, String vInstalada, String vMinecraft, String updateUrl, String repo) {
        this.plugin = instance;
        this.versionInstalada = vInstalada;
        this.versionMinecraft = vMinecraft;
        this.updateUrl = updateUrl;
        this.repo = (repo != null && !repo.isEmpty()) ? repo : extractRepoFromUrl(updateUrl, DEFAULT_REPO);
    }

    /**
     * Constructor de retrocompatibilidad: si solo se pasa updateUrl, el repo se extrae
     * de la URL (asumiendo formato raw.githubusercontent.com/{repo}/{branch}/...) o cae
     * al DEFAULT_REPO si no se puede extraer.
     */
    public Updater(CSPlugin instance, String vInstalada, String vMinecraft, String updateUrl) {
        this(instance, vInstalada, vMinecraft, updateUrl, extractRepoFromUrl(updateUrl, DEFAULT_REPO));
    }

    private static String buildUpdateUrl(String repo, String branch) {
        return "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + DEFAULT_UPDATE_PATH;
    }

    private static String extractRepoFromUrl(String url, String fallback) {
        if (url == null) return fallback;
        int idx = url.indexOf("raw.githubusercontent.com/");
        if (idx < 0) return fallback;
        // Después viene: {owner}/{repo}/{branch}/{path}
        String rest = url.substring(idx + "raw.githubusercontent.com/".length());
        int firstSlash = rest.indexOf('/');
        if (firstSlash < 0) return fallback;
        int secondSlash = rest.indexOf('/', firstSlash + 1);
        if (secondSlash < 0) return fallback;
        return rest.substring(0, secondSlash);
    }

    /**
     * Comprobar si hay nueva versión
     * @param sender Jugador al que se avisará
     */
    public void checkearVersion(CSCommandSender sender) {
        checkearVersion(sender, false);
    }

    /**
     * Comprobar si hay nueva versión
     * @param sender Jugador al que se avisará
     * @param confirmation Si es true, se avisará si no hay nueva versión
     */
    public void checkearVersion(CSCommandSender sender, boolean confirmation) {
        if (sender == null) {
            sender = new CSConsoleSender(plugin);
        }
        plugin.debugLog("Buscando nueva versión para Minecraft " + versionMinecraft + "...");

        final CSCommandSender finalSender = sender;
        fetchUpdate().thenAccept((UpdaterInfo updaterInfo) -> {
            if (updaterInfo == null) {
                if (confirmation) {
                    finalSender.sendMessageWithTag(MessageKey.UPDATE_NO_INFO.resolve(plugin.getCSConfiguration()));
                }
                return;
            }
            Optional<Map.Entry<String, String>> recommendedVersion = updaterInfo.getPluginForMinecraft(versionMinecraft);
            if (recommendedVersion.isPresent()) {
                String updaterVersion = recommendedVersion.get().getKey();
                String updateDescription = recommendedVersion.get().getValue();

                if (!updaterVersion.equals(versionInstalada)) {
                    String link = String.format("https://github.com/%s/releases/tag/v%s", repo, updaterVersion);
                    String format = String.format(NEW_VERSION, updaterVersion, updateDescription, link);
                    finalSender.sendMessageWithTag(format);
                } else {
                    finalSender.sendMessageWithTag(UPDATED);
                }
            } else if (confirmation) {
                finalSender.sendMessageWithTag(MessageKey.UPDATE_NO_NEW.resolve(plugin.getCSConfiguration()));
            }
        }).exceptionally(e -> {
            plugin.log(ERROR);
            plugin.debugLog("Causa: " + e.getMessage());
            return null;
        });
    }

    private CompletableFuture<UpdaterInfo> fetchUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(updateUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(CONNECT_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent",
                        UserAgent.build(plugin.getPluginVersion(), plugin.getServerPlatform(), plugin.getServerVersion()));

                int status = connection.getResponseCode();
                InputStream stream = (status >= 200 && status < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                if (stream == null) {
                    throw new IOException("Updater fetch failed: HTTP " + status);
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("Updater fetch failed: HTTP " + status);
                }
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    UpdaterInfo info = new Gson().fromJson(reader, UpdaterInfo.class);
                    if (info == null) {
                        throw new IOException("Updater returned empty body");
                    }
                    return info;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute Updater fetch", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, SHARED_EXECUTOR);
    }

    /**
     * Liberar el executor compartido. Llamar al deshabilitar el plugin.
     */
    public static void shutdown() {
        SHARED_EXECUTOR.shutdown();
        try {
            if (!SHARED_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                SHARED_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SHARED_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
