package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.TestSupport.MockPlugin;
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
 * La URL base de la API está hardcodeada en ApiClient, así que estos tests sirven
 * para validar la lógica de fetchData/parseo y la propagación de errores, no para
 * cambiar la URL. La cobertura principal es el parser de respuestas y manejo de errores.
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

        @Override
        protected String getBaseUrl() {
            return testUrl != null ? testUrl : super.getBaseUrl();
        }
    }
}
