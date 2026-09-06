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

    /**
     * URL por defecto de la API. Antes era {@code https://40servidoresmc.es/api2.php?clave=}
     * (sin www) pero el apex respondía 301 hacia {@code www.40servidoresmc.es} y eso añadía
     * un segundo handshake TLS por cada validación. Hoy por defecto vamos directos al host
     * canónico ({@code www.}). Sigue siendo override-able vía {@code api-url} en config.
     */
    static final String DEFAULT_API_URL = "https://www.40servidoresmc.es/api2.php?clave=";

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

    /**
     * URL base de la API, configurable vía {@code api-url} en config.
     * Si la clave falta o el valor queda vacío, se cae al default canónico
     * ({@code https://www.40servidoresmc.es/api2.php?clave=}).
     *
     * <p>Se lee en cada fetch — no se cachea — para que un {@code /reload40}
     * surta efecto inmediato sobre la URL sin reiniciar el servidor.</p>
     */
    protected String getBaseUrl() {
        String url = plugin.getCSConfiguration().getString("api-url", DEFAULT_API_URL);
        if (url == null || url.isEmpty()) {
            return DEFAULT_API_URL;
        }
        return url;
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
            // Lanzamos la excepción propia para que el caller (VoteCMD/StatsCMD)
            // pueda distinguir "el circuito está abierto, no he llamado" de "la
            // llamada falló de verdad" y mostrar un mensaje adecuado al jugador.
            long retryMs = circuitBreaker.backoffRemainingMs();
            throw new CircuitBreaker.CircuitOpenException(retryMs);
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

            // 429 Too Many Requests: la API nos está pidiendo que bajemos el ritmo.
            // No alimentamos el circuit breaker (no es "la API está caída") y
            // respetamos Retry-After para informar al jugador del tiempo de espera.
            if (status == 429) {
                long retryMs = parseRetryAfter(connection, 5000L);
                connection.disconnect();
                throw new RateLimitedException(retryMs);
            }

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
     * Lee la cabecera Retry-After de la respuesta HTTP y devuelve el valor en
     * milisegundos. Si la cabecera falta o es inválida, devuelve el fallback.
     *
     * <p>Retry-After admite dos formatos según RFC 7231:
     * <ul>
     *   <li>Entero: número de segundos (p. ej. "30").</li>
     *   <li>HTTP-date: fecha exacta de reintento (raro en APIs JSON).</li>
     * </ul>
     * Sólo soportamos el formato entero.</p>
     */
    private static long parseRetryAfter(HttpURLConnection connection, long fallbackMs) {
        String header = connection.getHeaderField("Retry-After");
        if (header == null || header.isEmpty()) {
            return fallbackMs;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            if (seconds < 0) return fallbackMs;
            return seconds * 1000L;
        } catch (NumberFormatException nfe) {
            return fallbackMs;
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
