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

    public static final String DEFAULT_REPO = "Cadiducho/40ServidoresMC";
    public static final String DEFAULT_BRANCH = "development";
    public static final String DEFAULT_UPDATE_PATH = "etc/v3.json";

    public Updater(CSPlugin instance, String vInstalada, String vMinecraft) {
        this(instance, vInstalada, vMinecraft, DEFAULT_REPO, DEFAULT_BRANCH);
    }

    public Updater(CSPlugin instance, String vInstalada, String vMinecraft, String repo, String branch) {
        this(instance, vInstalada, vMinecraft,
                "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + DEFAULT_UPDATE_PATH);
    }

    public Updater(CSPlugin instance, String vInstalada, String vMinecraft, String updateUrl) {
        this.plugin = instance;
        this.versionInstalada = vInstalada;
        this.versionMinecraft = vMinecraft;
        this.updateUrl = updateUrl;
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
                    String link = String.format("https://github.com/Cadiducho/40ServidoresMC/releases/tag/v%s", updaterVersion);
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
                connection.setRequestProperty("User-Agent", "40ServidoresMC-Plugin/3.0");

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
