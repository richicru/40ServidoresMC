package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.TestSupport.MockPlugin;
import com.cadiducho.cservidoresmc.cmd.VoteCMD;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de ApiClient con un servidor HTTP local (com.sun.net.httpserver).
 * La URL base de la API es configurable por {@code api-url} en config
 * (default = www.40servidoresmc.es/api2.php?clave=). Los tests usan un override
 * de {@link TestableApiClient#getBaseUrl()} para apuntar a un servidor HTTP local.
 * Cobertura principal: parser de respuestas, propagación de errores,
 * y la nueva lógica de selección de URL base.
 */
class TestApiClient {

    private HttpServer server;
    private MockPlugin plugin;
    private TestableApiClient client;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        plugin = new MockPlugin();
        plugin.configuration
                .set("clave", "TESTKEY")
                .set("readTimeOut", 5000);
        // api-url: el client de test la ignora porque TestableApiClient override
        // getBaseUrl(). Tests específicos de api-url usan un cliente real (no-Testable).

        client = new TestableApiClient(plugin, new Gson());
    }

    @AfterEach
    void teardown() {
        if (server != null) server.stop(0);
    }

    @Test
    void validateVoteParsesSuccessResponse() throws Exception {
        server.createContext("/test/success", exchange -> {
            String body = "{\"web\":\"https://40servidoresmc.es\",\"status\":\"1\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/success?clave=";

        VoteResponse response = client.validateVote("alice").get(2, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(VoteStatus.SUCCESS, response.getStatus());
        assertEquals("https://40servidoresmc.es", response.getWeb());
    }

    @Test
    void validateVoteParsesNotVotedResponse() throws Exception {
        server.createContext("/test/notvoted", exchange -> {
            String body = "{\"web\":\"https://40servidoresmc.es/votar\",\"status\":\"0\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/notvoted?clave=";

        VoteResponse response = client.validateVote("bob").get(2, TimeUnit.SECONDS);
        assertEquals(VoteStatus.NOT_VOTED, response.getStatus());
    }

    @Test
    void fetchServerStatsParsesCorrectly() throws Exception {
        server.createContext("/test/stats", exchange -> {
            String body = "{\"nombre\":\"Server\",\"puesto\":5,\"votoshoy\":10,\"votoshoypremiados\":8," +
                    "\"votossemanales\":75,\"votossemanalespremiados\":60,\"ultimos20votos\":[]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/stats?clave=";

        ServerStats stats = client.fetchServerStats().get(2, TimeUnit.SECONDS);
        assertNotNull(stats);
        assertEquals("Server", stats.getServerName());
        assertEquals(5, stats.getPosition());
        assertEquals(10, stats.getDayVotes());
        assertTrue(stats.getLastVotes().isEmpty());
    }

    @Test
    void serverErrorPropagatesAsException() {
        server.createContext("/test/error", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/error?clave=";

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> client.validateVote("x").get(2, TimeUnit.SECONDS));
        String fullMessage = collectMessageChain(ex);
        assertTrue(fullMessage.contains("HTTP 500"),
                "El mensaje debe indicar código HTTP, fue: " + fullMessage);
    }

    @Test
    void malformedJsonPropagatesAsException() {
        server.createContext("/test/bad", exchange -> {
            String body = "not json {{{";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/bad?clave=";

        assertThrows(Exception.class, () -> client.validateVote("x").get(2, TimeUnit.SECONDS));
    }

    @Test
    void emptyBodyReturnsIOExceptionWithDescriptiveMessage() {
        server.createContext("/test/empty", exchange -> {
            byte[] empty = new byte[0];
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(empty);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/empty?clave=";

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> client.validateVote("x").get(2, TimeUnit.SECONDS));
        String fullMessage = collectMessageChain(ex);
        assertTrue(fullMessage.contains("empty") || fullMessage.contains("null"),
                "El mensaje debe indicar cuerpo vacío/null, fue: " + fullMessage);
    }

    @Test
    void clientError4xxPropagatesWithStatus() {
        server.createContext("/test/notfound", exchange -> {
            String body = "{\"error\":\"not found\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/notfound?clave=";

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> client.validateVote("x").get(2, TimeUnit.SECONDS));
        String fullMessage = collectMessageChain(ex);
        assertTrue(fullMessage.contains("HTTP 404"),
                "El mensaje debe indicar código HTTP, fue: " + fullMessage);
    }

    private static String collectMessageChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            if (cur.getMessage() != null) sb.append(cur.getMessage()).append(" | ");
            cur = cur.getCause();
        }
        return sb.toString();
    }

    @Test
    void apiKeyIsReadFromConfiguration() throws Exception {
        StringBuilder capturedQuery = new StringBuilder();
        server.createContext("/test/keycheck", exchange -> {
            capturedQuery.append(exchange.getRequestURI().getQuery());
            String body = "{\"web\":\"\",\"status\":\"3\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/keycheck?clave=";

        VoteResponse response = client.validateVote("x").get(2, TimeUnit.SECONDS);
        assertEquals(VoteStatus.INVALID_KEY, response.getStatus());
        assertNotNull(capturedQuery.toString());
        assertTrue(capturedQuery.toString().contains("clave=TESTKEY"),
                "La URL debe contener la clave de configuración, fue: " + capturedQuery);
    }

    @Test
    void userAgentHeaderIsSent() throws Exception {
        final String[] captured = {null};
        server.createContext("/test/ua", exchange -> {
            captured[0] = exchange.getRequestHeaders().getFirst("User-Agent");
            String body = "{\"web\":\"\",\"status\":\"1\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        client.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/test/ua?clave=";

        client.validateVote("x").get(2, TimeUnit.SECONDS);
        assertNotNull(captured[0], "Debe enviarse la cabecera User-Agent");
        assertTrue(captured[0].contains("40ServidoresMC"),
                "El User-Agent debe contener 40ServidoresMC, fue: " + captured[0]);
        // MockPlugin.getServerPlatform()="Test", getServerVersion()="test-1.0"
        assertTrue(captured[0].contains("Test-test-1.0"),
                "El User-Agent debe incluir plataforma y serverVersion (Test-test-1.0), fue: " + captured[0]);
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailures() {
        // Crear cliente con circuit breaker explícito: 2 fallos, backoff corto
        CircuitBreaker cb = new CircuitBreaker(2, 5_000L, 10_000L);
        TestableApiClient cbClient = new TestableApiClient(plugin, new Gson(), cb);

        server.createContext("/cb/fail", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        cbClient.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/cb/fail?clave=";

        // dos fallos deben abrir el circuito
        for (int i = 0; i < 2; i++) {
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> cbClient.validateVote("u").get(2, TimeUnit.SECONDS));
        }
        assertEquals(2, cb.getConsecutiveFailures());
        assertTrue(cb.isOpen(), "Tras 2 fallos seguidos, el circuit breaker debe estar abierto");

        // La siguiente llamada NO debe tocar el servidor: falla inmediato
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> cbClient.validateVote("u").get(2, TimeUnit.SECONDS));
    }

    @Test
    void circuitBreakerClosesAfterSuccess() {
        CircuitBreaker cb = new CircuitBreaker(2, 60_000L, 120_000L);
        TestableApiClient cbClient = new TestableApiClient(plugin, new Gson(), cb);

        server.createContext("/cb/ok", exchange -> {
            String body = "{\"web\":\"\",\"status\":\"1\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        cbClient.testUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/cb/ok?clave=";

        cb.recordFailure();
        cb.recordFailure();
        assertTrue(cb.isOpen());
        cb.recordSuccess();
        assertFalse(cb.isOpen(), "Tras un éxito el circuito debe cerrarse");

        // Una llamada real debe funcionar
        try {
            cbClient.validateVote("u").get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Tras cerrar el circuito la llamada debe funcionar: " + e.getMessage());
        }
        assertEquals(0, cb.getConsecutiveFailures());
    }

    // ============================================================
    //  CircuitOpenException: cuando el circuito está abierto, fetchData
    //  lanza CircuitOpenException (RuntimeException) con el retryAfterMs, NO
    //  un IOException genérico.
    // ============================================================
    @Test
    void fetchDataThrowsCircuitOpenException_whenCircuitIsOpen() throws Exception {
        // Cliente con circuit breaker explícito: 1 fallo → abre con 60s de backoff
        CircuitBreaker cb = new CircuitBreaker(1, 60_000L, 120_000L);
        cb.recordFailure();
        assertTrue(cb.isOpen());

        // Mock-server que no debería recibir NADA (porque el circuito está abierto)
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/cb/open", exchange -> {
            hits.incrementAndGet();
            String body = "{\"web\":\"\",\"status\":\"1\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        RealApiClient openClient = new RealApiClient(plugin, new Gson(),
                java.util.concurrent.Executors.newSingleThreadExecutor(), cb);
        openClient.setTestBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/cb/open?clave=");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> openClient.validateVote("u").get(2, TimeUnit.SECONDS));
        // La excepción de la CompletableFuture es una CompletionException envolviendo
        // IllegalStateException que envuelve la CircuitOpenException. El caller usa
        // unwrapRootCause para llegar al fondo.
        Throwable root = VoteCMD.unwrapRootCause(ex);
        assertTrue(root instanceof CircuitBreaker.CircuitOpenException,
                "El root cause debe ser CircuitOpenException, fue: " + root.getClass().getName());
        assertTrue(((CircuitBreaker.CircuitOpenException) root).getRetryAfterMs() > 0);
        assertEquals(0, hits.get(),
                "El mock no debe haber recibido NINGUNA petición (el circuito impidió la llamada)");
    }

    // ============================================================
    //  RateLimitedException: un HTTP 429 con Retry-After se traduce a
    //  RateLimitedException, y NO alimenta el circuit breaker.
    // ============================================================
    @Test
    void fetchDataThrowsRateLimitedException_on429_andDoesNotFeedCircuitBreaker() throws Exception {
        CircuitBreaker cb = new CircuitBreaker(3, 1_000L, 5_000L);
        RealApiClient limitedClient = new RealApiClient(plugin, new Gson(),
                java.util.concurrent.Executors.newSingleThreadExecutor(), cb);

        server.createContext("/test/429", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "37");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        limitedClient.setTestBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/test/429?clave=");

        // Hacer 5 llamadas que devuelven 429
        for (int i = 0; i < 5; i++) {
            try {
                limitedClient.validateVote("u").get(2, TimeUnit.SECONDS);
                fail("La llamada #" + (i+1) + " debería haber lanzado RateLimitedException");
            } catch (ExecutionException ignored) {
                // esperado
            }
        }
        // CRÍTICO: el circuit breaker NO debe estar abierto tras 5 calls fallidas
        // con 429, porque un 429 NO es "la API está caída".
        assertEquals(0, cb.getConsecutiveFailures(),
                "429 NO debe alimentar el circuit breaker. consecutiveFailures=" + cb.getConsecutiveFailures());
        assertFalse(cb.isOpen(),
                "El circuito debe seguir cerrado tras 5 x 429 (no es señal de API caída)");

        // Y la última excepción debe haber sido RateLimitedException con retryAfter=37s
        // (verificamos el último error haciendo otra llamada y mirando el tipo)
        try {
            limitedClient.validateVote("u2").get(2, TimeUnit.SECONDS);
            fail("Debió lanzar excepción");
        } catch (ExecutionException e) {
            Throwable root = VoteCMD.unwrapRootCause(e);
            assertTrue(root instanceof RateLimitedException,
                    "Root cause debe ser RateLimitedException, fue: " + root.getClass().getName());
            RateLimitedException rle = (RateLimitedException) root;
            assertEquals(37_000L, rle.getRetryAfterMs(),
                    "Retry-After: 37 segundos, fue: " + rle.getRetryAfterMs());
        }
    }

    @Test
    void parseRetryAfterFallsBackOnMissingHeader() throws Exception {
        server.createContext("/test/no429hdr", exchange -> {
            // 429 sin Retry-After
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        CircuitBreaker cb = new CircuitBreaker(3, 1_000L, 5_000L);
        RealApiClient limitedClient = new RealApiClient(plugin, new Gson(),
                java.util.concurrent.Executors.newSingleThreadExecutor(), cb);
        limitedClient.setTestBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/test/no429hdr?clave=");

        try {
            limitedClient.validateVote("u").get(2, TimeUnit.SECONDS);
            fail("Debió lanzar RateLimitedException");
        } catch (ExecutionException e) {
            Throwable root = VoteCMD.unwrapRootCause(e);
            assertTrue(root instanceof RateLimitedException);
            // Si no hay Retry-After, debe caer al fallback de 5000ms.
            assertEquals(5_000L, ((RateLimitedException) root).getRetryAfterMs());
        }
    }

    @Test
    void parseRetryAfterHandlesInvalidHeaderGracefully() throws Exception {
        server.createContext("/test/bad429hdr", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "not-a-number");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        CircuitBreaker cb = new CircuitBreaker(3, 1_000L, 5_000L);
        RealApiClient limitedClient = new RealApiClient(plugin, new Gson(),
                java.util.concurrent.Executors.newSingleThreadExecutor(), cb);
        limitedClient.setTestBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/test/bad429hdr?clave=");

        try {
            limitedClient.validateVote("u").get(2, TimeUnit.SECONDS);
            fail("Debió lanzar RateLimitedException");
        } catch (ExecutionException e) {
            Throwable root = VoteCMD.unwrapRootCause(e);
            assertTrue(root instanceof RateLimitedException);
            // Header inválido → fallback a 5000ms.
            assertEquals(5_000L, ((RateLimitedException) root).getRetryAfterMs());
        }
    }

    // ============================================================
    //  api-url config: default con www, override por config, fallback si vacío
    // ============================================================

    /**
     * Helper: cliente real (no-Testable) que respeta la selección de URL base
     * desde config. Necesario porque TestableApiClient overridea getBaseUrl().
     * Permite también fijar una URL de test con setTestBaseUrl().
     */
    private static class RealApiClient extends com.cadiducho.cservidoresmc.ApiClient {
        RealApiClient(com.cadiducho.cservidoresmc.api.CSPlugin plugin, Gson gson) {
            super(plugin, gson);
        }
        RealApiClient(com.cadiducho.cservidoresmc.api.CSPlugin plugin, Gson gson,
                       java.util.concurrent.ExecutorService exec, CircuitBreaker cb) {
            super(plugin, gson, exec, cb);
        }
        String capturedBaseUrl;
        private String testBaseUrl;

        void setTestBaseUrl(String url) {
            this.testBaseUrl = url;
        }

        @Override
        protected String getBaseUrl() {
            String u = (testBaseUrl != null) ? testBaseUrl : super.getBaseUrl();
            capturedBaseUrl = u;
            return u;
        }
    }

    @Test
    void apiUrlDefaultsToWwwHost() {
        plugin.configuration.set("api-url", null); // valor explícitamente null
        RealApiClient c = new RealApiClient(plugin, new Gson());
        String url = c.getBaseUrl();
        assertTrue(url.startsWith("https://www.40servidoresmc.es/"),
                "Default debe apuntar al host canónico (www.), fue: " + url);
        assertTrue(url.contains("/api2.php?clave="),
                "Default debe contener '/api2.php?clave=', fue: " + url);
    }

    @Test
    void apiUrlHonorsConfigOverride() {
        plugin.configuration.set("api-url", "https://custom.example.org/vote/api.php?clave=");
        RealApiClient c = new RealApiClient(plugin, new Gson());
        assertEquals("https://custom.example.org/vote/api.php?clave=", c.getBaseUrl());
    }

    @Test
    void apiUrlFallsBackOnEmptyString() {
        plugin.configuration.set("api-url", ""); // el usuario borró el valor por error
        RealApiClient c = new RealApiClient(plugin, new Gson());
        assertTrue(c.getBaseUrl().startsWith("https://www.40servidoresmc.es/"),
                "Cadena vacía debe caer al default (www.), fue: " + c.getBaseUrl());
    }

    @Test
    void apiUrlReadOnEveryFetch_soReloadTakesEffect() {
        // RealApiClient captura la URL cada vez que se llama getBaseUrl() (que es
        // exactamente lo que pasa cuando fetchData() se llama varias veces).
        plugin.configuration.set("api-url", "https://host-a.example/?clave=");
        RealApiClient c = new RealApiClient(plugin, new Gson());
        assertEquals("https://host-a.example/?clave=", c.getBaseUrl());
        assertEquals("https://host-a.example/?clave=", c.capturedBaseUrl,
                "RealApiClient captura la URL cada vez");

        // Simulamos /reload40 — el usuario cambia api-url.
        plugin.configuration.set("api-url", "https://host-b.example/?clave=");
        assertEquals("https://host-b.example/?clave=", c.getBaseUrl(),
                "El cambio en config debe verse inmediatamente sin reiniciar el plugin");
    }

    @Test
    void apiUrlIsAppendedWithKeyAndParams() {
        // Esto verifica el formato final de la URL que el server verá.
        // Aunque el path exacto puede cambiar (un endpoint distinto), la URL debe:
        //   - terminar con /<algo>?clave=
        //   - estar en HTTPS
        String url = ApiClient.DEFAULT_API_URL;
        assertTrue(url.startsWith("https://"), "Debe ser HTTPS: " + url);
        assertTrue(url.endsWith("/api2.php?clave="),
                "Debe terminar con '/api2.php?clave=' (clave se concatena después): " + url);
    }

    /**
     * TestableApiClient expone un setter para sobreescribir la URL base en tiempo
     * de tests. La clase real (ApiClient) tiene la URL hardcodeada — esta envoltura
     * mantiene la misma lógica de fetchData pero permite inyectar el host.
     */
    private static class TestableApiClient extends com.cadiducho.cservidoresmc.ApiClient {
        String testUrl;

        TestableApiClient(com.cadiducho.cservidoresmc.api.CSPlugin plugin, Gson gson) {
            super(plugin, gson);
        }

        TestableApiClient(com.cadiducho.cservidoresmc.api.CSPlugin plugin, Gson gson, CircuitBreaker cb) {
            super(plugin, gson, java.util.concurrent.Executors.newSingleThreadExecutor(), cb);
        }

        @Override
        protected String getBaseUrl() {
            return testUrl != null ? testUrl : super.getBaseUrl();
        }
    }
}
