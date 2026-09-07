package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.AckRequest;
import com.cadiducho.cservidoresmc.model.AckResponse;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.util.PendingAckStore;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
     *
     * <p>En v3.1.0 (protocolo v3) este default sigue valiendo: lo interpretamos como base URL
     * y construimos los paths absolutos a partir de él. Si la URL termina en
     * {@code /api2.php?clave=} (formato legacy v2), la truncamos automáticamente a la base.</p>
     */
    static final String DEFAULT_API_URL = "https://www.40servidoresmc.es/api2.php?clave=";

    private final CSPlugin plugin;
    private final Gson gson;
    private final ExecutorService ioExecutor;
    private final CircuitBreaker circuitBreaker;
    private final PendingAckStore pendingAcks = new PendingAckStore();

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
     * URL completa tal cual está en config. Se mantiene por compatibilidad con el
     * flujo v2 (donde {@code getBaseUrl() + apiKey() + params} ya incluía el
     * {@code /api2.php?clave=}). Para v3 se usa {@link #getApiBase()}.
     */
    protected String getBaseUrl() {
        String url = plugin.getCSConfiguration().getString("api-url", DEFAULT_API_URL);
        if (url == null || url.isEmpty()) {
            return DEFAULT_API_URL;
        }
        return url;
    }

    /**
     * URL base (scheme + host + port) extraída de {@code api-url}. Si la URL
     * configurada incluye path (e.g. {@code https://host/api2.php?clave=}), lo
     * descartamos. Esto permite al usuario dejar el default legacy y que el
     * plugin lo trunque automáticamente para construir los endpoints v3.
     */
    protected String getApiBase() {
        String url = getBaseUrl();
        if (url == null || url.isEmpty()) url = DEFAULT_API_URL;
        int protocolEnd = url.indexOf("://");
        if (protocolEnd < 0) return url;
        int pathStart = url.indexOf('/', protocolEnd + 3);
        return (pathStart > 0) ? url.substring(0, pathStart) : url;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Executor de I/O de este cliente (2 hilos dedicados, ver
     * {@link #defaultIoExecutor()}). Expuesto para que VoteCMD pueda encadenar
     * ahí el trabajo de "entregar premio + ackear" en vez de dejar que
     * {@code CompletableFuture.runAsync(...)} caiga en
     * {@code ForkJoinPool.commonPool()} -- el pool compartido de TODA la JVM,
     * usado también por otros plugins. Bloquear un hilo de ese pool compartido
     * esperando un tick del scheduler de Bukkit (como hace
     * {@code runSyncForPlayerWithResult}) puede dejar sin hilos disponibles a
     * cualquier otro código que dependa de él durante un pico de votos.
     */
    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    // ============================================================
    //  v2 — flujo legacy con /api2.php
    // ============================================================

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
                // stats usa el endpoint legacy /api2.php (no hay equivalente v3).
                String v2Url = v2StatsUrl();
                return executeRequest(v2Url, "GET", null, null, ServerStats.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute API call", e);
            }
        }, ioExecutor);
    }

    /**
     * Construye la URL del endpoint legacy /api2.php?clave= para stats.
     * Si {@code api-url} ya termina en {@code /api2.php?clave=} (formato legacy),
     * añadimos stats al final. Si es la bare base, añadimos el path y la clave.
     */
    private String v2StatsUrl() {
        String base = getBaseUrl();
        if (base.endsWith("?clave=") || base.endsWith("?clave")) {
            // legacy v2 url con placeholder ?clave=; añadimos los params de stats
            return base + apiKey() + "&estadisticas=1";
        }
        // bare base — añadimos el path legacy y la clave
        return getApiBase() + "/api2.php?clave=" + apiKey() + "&estadisticas=1";
    }

    /**
     * v2 — ejecuta la llamada legacy a /api2.php?clave=X con clave en query string.
     * Usado por validateVote (legacy) si algún admin sigue con la versión vieja
     * y por fetchServerStats (no hay endpoint v3 de stats).
     */
    private <T> T fetchData(String params, String method, Class<T> type) throws IOException {
        String base = getBaseUrl();
        String v2Url;
        if (base.endsWith("?clave=") || base.endsWith("?clave")) {
            // legacy v2 url con placeholder ?clave=; añadimos apiKey() + params
            v2Url = base + apiKey() + params;
        } else {
            // bare base — añadimos el path legacy y la clave
            v2Url = getApiBase() + "/api2.php?clave=" + apiKey() + params;
        }
        return executeRequest(v2Url, method, null, null, type);
    }

    // ============================================================
    //  v3 — protocolo nuevo, Bearer auth
    // ============================================================

    /**
     * {@code GET /api/vote/v3/pending?nick=...} con {@code Authorization: Bearer <clave>}.
     * Devuelve los votos pendientes que el jugador puede cobrar.
     */
    public CompletableFuture<PendingVotesResponse> fetchPendingVotes(String nick) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = "/api/vote/v3/pending?nick=" + URLEncoder.encode(nick, "UTF-8");
                return executeRequest(getApiBase() + path, "GET", "Bearer " + apiKey(), null,
                        PendingVotesResponse.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute V3 pending API call", e);
            }
        }, ioExecutor);
    }

    /**
     * {@code POST /api/vote/v3/ack} con Bearer auth y body JSON.
     *
     * @param delivered true si la entrega del premio fue exitosa, false si algo
     *                  falló y queremos liberar la reserva al instante.
     * @param userIp    IP en claro (sin hashear) — el server la cruza con la IP
     *                  que el usuario dejó al votar en la web. Si el server está
     *                  detrás de un proxy sin ip-forward, el caller debe pasar ""
     *                  (no tiene sentido mandarle la IP del proxy para todos).
     */
    public CompletableFuture<AckResponse> sendAck(List<Long> voteIds, String nick,
                                                 boolean delivered, String userIp) {
        AckRequest payload = new AckRequest(voteIds, delivered, nick,
                (userIp == null) ? "" : userIp);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = gson.toJson(payload);
                return executeRequest(getApiBase() + "/api/vote/v3/ack", "POST",
                        "Bearer " + apiKey(), body, AckResponse.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot execute V3 ack API call", e);
            }
        }, ioExecutor);
    }

    // ============================================================
    //  Retry de acks fallidos
    // ============================================================

    /**
     * Registra ids cuya entrega tuvo éxito pero cuyo ack al server falló.
     * Se reintentará en el próximo {@code /voto40} del mismo jugador.
     */
    public void addPendingAck(String nick, List<Long> voteIds) {
        pendingAcks.add(nick, voteIds);
    }

    /**
     * @return ids pendientes para este nick sin consumirlos.
     */
    public List<Long> peekPendingAcks(String nick) {
        return pendingAcks.peek(nick);
    }

    /**
     * Saca los ids pendientes y los manda como ack con {@code delivered:true}.
     * Si la llamada tiene éxito, los ids se eliminan del store. Si falla
     * (timeout, red caída, 4xx/5xx), vuelven al store para el próximo intento.
     *
     * <p>Fire-and-forget: devuelve un {@code CompletableFuture} para que el caller
     * pueda encadenar, pero no debe esperar a que termine antes de procesar el
     * pending normal. Si hay ids pendientes, los manda primero; si no, retorna
     * un futuro ya completado.</p>
     */
    public CompletableFuture<AckResponse> retryPendingAcks(String nick) {
        List<Long> ids = pendingAcks.take(nick);
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return sendAck(ids, nick, true, "").handle((ack, err) -> {
            if (err != null || ack == null) {
                // Falló: los ids vuelven al store para el próximo /voto40.
                pendingAcks.add(nick, ids);
            }
            // Si tuvo éxito, sendAck ya hizo su trabajo y no hace falta re-almacenar.
            return ack;
        });
    }

    // ============================================================
    //  Helper compartido: hace la HTTP request, parsea el body y aplica
    //  circuit breaker / rate limiting / 4xx-5xx.
    // ============================================================

    /**
     * Ejecuta una llamada HTTP y parsea la respuesta como el tipo pedido.
     *
     * @param fullUrl URL completa, ya con path y query params concatenados.
     * @param method  "GET" o "POST".
     * @param bearer  Si no es null, se envía como {@code Authorization: Bearer <bearer>}.
     *                 Si es null, no se manda header (modo legacy v2 usa ?clave= en la URL).
     * @param body    Cuerpo JSON para POST. Null para GET.
     * @param type    Clase para deserializar la respuesta.
     */
    private <T> T executeRequest(String fullUrl, String method, String bearer, String body, Class<T> type) throws IOException {
        if (!circuitBreaker.canExecute()) {
            long retryMs = circuitBreaker.backoffRemainingMs();
            throw new CircuitBreaker.CircuitOpenException(retryMs);
        }

        URL url = new URL(fullUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(timeOut());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent",
                UserAgent.build(plugin.getPluginVersion(), plugin.getServerPlatform(), plugin.getServerVersion()));
        if (bearer != null && !bearer.isEmpty()) {
            connection.setRequestProperty("Authorization", bearer);
        }

        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);
            connection.getOutputStream().close();
        }

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
                String bodyText;
                try (Reader r = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[256];
                    int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                    bodyText = sb.toString();
                }
                circuitBreaker.recordFailure();
                throw new IOException("API call failed: HTTP " + status + " — " + bodyText);
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
