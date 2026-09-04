package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_IO_THREADS = 2;

    private final String API_URL = "https://40servidoresmc.es/api2.php?clave=";
    private final CSPlugin plugin;
    private final Gson gson;
    private final ExecutorService ioExecutor;
    private final CircuitBreaker circuitBreaker;

    public ApiClient(CSPlugin plugin, Gson gson) {
        this(plugin, gson, defaultIoExecutor(), CircuitBreaker.defaults());
    }

    public ApiClient(CSPlugin plugin, Gson gson, ExecutorService ioExecutor) {
        this(plugin, gson, ioExecutor, CircuitBreaker.defaults());
    }

    public ApiClient(CSPlugin plugin, Gson gson, ExecutorService ioExecutor, CircuitBreaker circuitBreaker) {
        this.plugin = plugin;
        this.gson = gson;
        this.ioExecutor = ioExecutor;
        this.circuitBreaker = circuitBreaker;
    }

    private static ExecutorService defaultIoExecutor() {
        return Executors.newFixedThreadPool(DEFAULT_IO_THREADS, r -> {
            Thread t = new Thread(r, "cservidoresmc-io");
            t.setDaemon(true);
            return t;
        });
    }

    public String apiKey() {
        return plugin.getCSConfiguration().getString("clave");
    }

    public int timeOut() {
        return plugin.getCSConfiguration().getInt("readTimeOut");
    }

    protected String getBaseUrl() {
        return API_URL;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public CompletableFuture<VoteResponse> validateVote(String player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchData("&nombre=" + player, "GET", VoteResponse.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call", e);
            }
        }, ioExecutor);
    }

    public CompletableFuture<ServerStats> fetchServerStats() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchData("&estadisticas=1", "GET", ServerStats.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call", e);
            }
        }, ioExecutor);
    }

    /**
     * Obtener datos de la API, según unos parámetros dados, y parsearlo a un objeto
     * @param params Parámetros HTTP de la petición
     * @param method Método HTTP
     * @param type Clase a la que convertir los datos recibidos
     * @param <T> Tipo que retornará
     * @return El objeto con los datos solicitados a la API
     * @throws IOException Si falla al parsear o al conectarse a la API, o si el servidor responde con código != 2xx
     */
    private <T> T fetchData(String params, String method, Class<T> type) throws IOException {
        if (!circuitBreaker.canExecute()) {
            long retryMs = circuitBreaker.backoffRemainingMs();
            throw new IOException("Circuit breaker open (backoff " + retryMs + " ms): aborting API call");
        }

        URL url = new URL(getBaseUrl() + apiKey() + params);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(timeOut());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent",
                UserAgent.build(plugin.getPluginVersion(), plugin.getServerPlatform(), plugin.getServerVersion()));

        int status;
        try {
            status = connection.getResponseCode();
            InputStream stream = (status >= 200 && status < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                circuitBreaker.recordFailure();
                throw new IOException("API call failed: HTTP " + status + " (no response body)");
            }
            if (status < 200 || status >= 300) {
                String body;
                try (Reader r = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[256];
                    int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                    body = sb.toString();
                }
                circuitBreaker.recordFailure();
                throw new IOException("API call failed: HTTP " + status + " — " + body);
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                T result = gson.fromJson(reader, type);
                if (result == null) {
                    circuitBreaker.recordFailure();
                    throw new IOException("API returned empty/null body for " + type.getSimpleName());
                }
                circuitBreaker.recordSuccess();
                return result;
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Liberar el executor de I/O. Llamar al deshabilitar el plugin.
     */
    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
