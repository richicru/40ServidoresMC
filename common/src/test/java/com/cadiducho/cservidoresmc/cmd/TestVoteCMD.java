package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.StatsCache;
import com.cadiducho.cservidoresmc.TestSupport;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.TestSupport.MockCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommand.CommandResult;
import com.cadiducho.cservidoresmc.model.AckResponse;
import com.cadiducho.cservidoresmc.model.PendingVote;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Tests del flujo v3 (pending + ack) de /voto40. El v2 (/api2.php) ya no es
 * el flujo principal del plugin; los tests verifican los caminos del v3.
 */
class TestVoteCMD {

    private VoteCMD cmd;
    private CSPlugin plugin;
    private TestSupport.MockConfiguration configuration;
    private ApiClient apiClient;

    @BeforeEach
    void setup() {
        cmd = new VoteCMD();

        configuration = new TestSupport.MockConfiguration(mock(CSPlugin.class));
        configuration
                .set("clave", "validKey")
                .set("mensaje", "&aPremio entregado")
                .set("broadcast.activado", false)
                .set("comandosCustom", Collections.singletonList("give {0} diamond 1"));

        apiClient = mock(ApiClient.class);

        plugin = mock(CSPlugin.class);
        when(plugin.getCSConfiguration()).thenReturn(configuration);
        when(plugin.getApiClient()).thenReturn(apiClient);
        when(plugin.getPluginVersion()).thenReturn("3.1.0");
        when(plugin.getUpdater()).thenReturn(mock(Updater.class));
        when(plugin.getStatsCache()).thenReturn(mock(StatsCache.class));
        doAnswer(inv -> { return null; })
                .when(plugin).log(anyString());
        doAnswer(inv -> { return null; })
                .when(plugin).logError(anyString());
        // runSyncForPlayer → ejecuta en línea (Bukkit/Folia unified mock)
        doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(plugin).runSyncForPlayer(anyString(), any(Runnable.class));
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(plugin).runSyncGlobal(any(Runnable.class));
        // runSyncForPlayerWithResult → ejecuta el supplier en línea y devuelve el resultado.
        doAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get())
                .when(plugin).runSyncForPlayerWithResult(anyString(), any(java.util.function.Supplier.class));
        // isPlayerOnline → true por defecto en el mock
        when(plugin.isPlayerOnline(anyString())).thenReturn(true);
        // dispatchCommand → true por defecto. Tests específicos (delivery failure)
        // sobreescriben con doReturn(false).
        when(plugin.dispatchCommand(anyString())).thenReturn(true);
    }

    @Test
    void consoleCannotVote() {
        MockCommandSender console = MockCommandSender.console();
        CommandResult result = cmd.execute(plugin, console, "voto40", Collections.emptyList());
        assertEquals(CommandResult.ONLY_PLAYER, result);
        verify(apiClient, never()).fetchPendingVotes(anyString());
    }

    @Test
    void pendingWithVotes_deliversAndAcksAsDelivered() throws Exception {
        // pending con 1 voto
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setJugador("alice");
        pending.setVotosPendientes(Collections.singletonList(makePending(123L)));
        pending.setReservaSegundos(300);
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("alice")).thenReturn(CompletableFuture.completedFuture(pending));

        AckResponse ack = new AckResponse();
        ack.setApiVersion(3);
        ack.setConfirmados(Collections.singletonList(123L));
        ack.setEntregado(true);
        when(apiClient.sendAck(eq(Collections.singletonList(123L)), eq("alice"), eq(true), anyString()))
                .thenReturn(CompletableFuture.completedFuture(ack));

        MockCommandSender alice = MockCommandSender.player("alice");
        CommandResult result = cmd.execute(plugin, alice, "voto40", Collections.emptyList());
        assertEquals(CommandResult.SUCCESS, result);

        Thread.sleep(200);

        verify(plugin).dispatchCommand("give alice diamond 1");
        verify(apiClient).sendAck(eq(Collections.singletonList(123L)), eq("alice"), eq(true), anyString());
        assertTrue(alice.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("gracias")),
                "Tras ack=true el jugador debería ver el mensaje de gracias");
    }

    @Test
    void emptyPending_puedeVotarYa_sendsPendingVoteMessage() throws Exception {
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setJugador("bob");
        pending.setVotosPendientes(Collections.emptyList());
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("bob")).thenReturn(CompletableFuture.completedFuture(pending));

        MockCommandSender bob = MockCommandSender.player("bob");
        cmd.execute(plugin, bob, "voto40", Collections.emptyList());

        Thread.sleep(200);

        verify(plugin, never()).dispatchCommand(anyString());
        verify(apiClient, never()).sendAck(any(), any(), anyBoolean(), anyString());
        assertTrue(bob.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("vota")),
                "Si puede_votar_ya=true debe salir el mensaje 'vota en la web'");
    }

    @Test
    void emptyPending_cannotVoteYet_sendsAlreadyRewardedMessage() throws Exception {
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setJugador("carol");
        pending.setVotosPendientes(Collections.emptyList());
        pending.setPuedeVotarYa(false);
        pending.setSiguienteVoto("2026-09-07T03:31:07+02:00");
        when(apiClient.fetchPendingVotes("carol")).thenReturn(CompletableFuture.completedFuture(pending));

        MockCommandSender carol = MockCommandSender.player("carol");
        cmd.execute(plugin, carol, "voto40", Collections.emptyList());

        Thread.sleep(200);

        verify(plugin, never()).dispatchCommand(anyString());
        verify(apiClient, never()).sendAck(any(), any(), anyBoolean(), anyString());
        assertTrue(carol.sentMessages.stream().anyMatch(m -> m.contains("2026-09-07")),
                "Debe incluir la fecha de siguiente_voto en el mensaje");
    }

    @Test
    void invalidKey403_sendsInvalidKeyMessage() throws Exception {
        // pending que falla con 403 (clave incorrecta en v3)
        when(apiClient.fetchPendingVotes("dave")).thenReturn(
                CompletableFuture.failedFuture(new java.io.IOException("API call failed: HTTP 403 — ...")));

        MockCommandSender dave = MockCommandSender.player("dave");
        cmd.execute(plugin, dave, "voto40", Collections.emptyList());

        Thread.sleep(200);

        verify(apiClient, never()).sendAck(any(), any(), anyBoolean(), anyString());
        assertTrue(dave.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("clave")),
                "Debe mostrar mensaje de clave incorrecta");
    }

    @Test
    void deliveryFailure_acksAsNotDelivered() throws Exception {
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(456L)));
        pending.setReservaSegundos(300);
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("eve")).thenReturn(CompletableFuture.completedFuture(pending));

        AckResponse ack = new AckResponse();
        ack.setEntregado(false);
        ack.setLiberados(Collections.singletonList(456L));
        when(apiClient.sendAck(any(), any(), eq(false), anyString()))
                .thenReturn(CompletableFuture.completedFuture(ack));

        // Comando custom "revienta" — dispatchCommand devuelve false
        when(plugin.dispatchCommand(anyString())).thenReturn(false);

        MockCommandSender eve = MockCommandSender.player("eve");
        cmd.execute(plugin, eve, "voto40", Collections.emptyList());

        Thread.sleep(200);

        verify(apiClient).sendAck(any(), eq("eve"), eq(false), anyString());
        assertTrue(eve.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("vuelve a")),
                "Debe informar al jugador que vuelva a intentarlo en unos minutos");
    }

    @Test
    void playerOfflineDuringDelivery_acksAsNotDelivered() throws Exception {
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(789L)));
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("frank")).thenReturn(CompletableFuture.completedFuture(pending));

        AckResponse ack = new AckResponse();
        ack.setEntregado(false);
        when(apiClient.sendAck(any(), any(), eq(false), anyString()))
                .thenReturn(CompletableFuture.completedFuture(ack));

        // El jugador se desconecta durante la entrega
        when(plugin.isPlayerOnline("frank")).thenReturn(false);

        MockCommandSender frank = MockCommandSender.player("frank");
        cmd.execute(plugin, frank, "voto40", Collections.emptyList());

        Thread.sleep(200);

        verify(apiClient).sendAck(any(), eq("frank"), eq(false), anyString());
    }

    @Test
    void userIp_sentInPlaintext_notHashed() throws Exception {
        // El user_ip va en claro al server. NO se hashea con SHA-256: el server
        // lo cruza con la IP que el usuario dejó al votar en la web, y un hash
        // sin sal nunca cuadraría.
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(111L)));
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("alice")).thenReturn(CompletableFuture.completedFuture(pending));

        final String[] capturedBody = {null};
        AckResponse ack = new AckResponse();
        ack.setEntregado(true);
        when(apiClient.sendAck(any(), eq("alice"), eq(true), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    // Capturamos lo que el plugin está mandando para verificar.
                    // Para capturarlo necesitamos hacer el send real, no mock.
                    // Por simplicidad, validamos vía peekPendingAcks + verificación del argumento.
                    String userIp = inv.getArgument(3);
                    assertEquals("203.0.113.42", userIp,
                            "user_ip debe ir en claro, NO como hash. Recibido: " + userIp);
                    return CompletableFuture.completedFuture(ack);
                });

        // getPlayerIp devuelve una IP pública
        when(plugin.getPlayerIp("alice")).thenReturn("203.0.113.42");

        MockCommandSender alice = MockCommandSender.player("alice");
        cmd.execute(plugin, alice, "voto40", Collections.emptyList());

        Thread.sleep(200);

        // Verifica que sendAck fue llamado con la IP en claro (no con un hash de 64 chars)
        org.mockito.Mockito.verify(apiClient).sendAck(
                org.mockito.ArgumentMatchers.anyList(),
                eq("alice"),
                eq(true),
                eq("203.0.113.42"));
    }

    @Test
    void userIp_emptyWhenLikelyBehindProxy() throws Exception {
        // Si el server está detrás de BungeeCord sin ip-forward, getPlayerIp() devuelve
        // una IP privada (127.x, 10.x, 192.168.x, etc.). En ese caso NO mandamos
        // user_ip: una IP constante para todos = "no coincide" permanente en cada voto
        // y hace parecer fraudulento al servidor.
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(222L)));
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("proxy_user")).thenReturn(CompletableFuture.completedFuture(pending));

        AckResponse ack = new AckResponse();
        ack.setEntregado(true);
        when(apiClient.sendAck(any(), eq("proxy_user"), eq(true), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    String userIp = inv.getArgument(3);
                    assertEquals("", userIp,
                            "Si IP parece de proxy, mandamos cadena vacía (no la IP del proxy). Recibido: " + userIp);
                    return CompletableFuture.completedFuture(ack);
                });

        // Simula IP de BungeeCord: 192.168.x
        when(plugin.getPlayerIp("proxy_user")).thenReturn("192.168.1.50");

        MockCommandSender sender = MockCommandSender.player("proxy_user");
        cmd.execute(plugin, sender, "voto40", Collections.emptyList());

        Thread.sleep(200);

        org.mockito.Mockito.verify(apiClient).sendAck(
                org.mockito.ArgumentMatchers.anyList(),
                eq("proxy_user"),
                eq(true),
                eq(""));
    }

    @Test
    void retryPendingAcks_isCalledAtStartOfExecution() throws Exception {
        when(apiClient.fetchPendingVotes("alice")).thenReturn(new CompletableFuture<>());

        // Verifica que retryPendingAcks se llama al principio (con los ids pendientes
        // que pueda haber del intento anterior).
        MockCommandSender alice = MockCommandSender.player("alice");
        cmd.execute(plugin, alice, "voto40", Collections.emptyList());

        org.mockito.Mockito.verify(apiClient).retryPendingAcks("alice");
    }

    @Test
    void ackFailure_storesIdsForLaterRetry() throws Exception {
        // Si el ack falla DESPUÉS de entregar el premio, los ids deben quedar en el
        // PendingAckStore para que el próximo /voto40 los re-confirme sin volver a
        // entregar el premio.
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(555L)));
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("eve")).thenReturn(CompletableFuture.completedFuture(pending));

        // El ack falla con IOException (timeout, red caída, etc.)
        when(apiClient.sendAck(any(), any(), anyBoolean(), anyString())).thenReturn(
                CompletableFuture.failedFuture(new java.io.IOException("timeout")));

        MockCommandSender eve = MockCommandSender.player("eve");
        cmd.execute(plugin, eve, "voto40", Collections.emptyList());

        Thread.sleep(200);

        // El plugin debe haber añadido los ids al store para retry posterior.
        org.mockito.Mockito.verify(apiClient).addPendingAck(eq("eve"),
                org.mockito.ArgumentMatchers.eq(Collections.singletonList(555L)));
    }

    @Test
    void ackFailure_noStoreIds_whenDeliveryAlsoFailed() throws Exception {
        // Si el ack falla Y la entrega también (algún dispatchCommand devolvió false
        // o el jugador se desconectó), entonces el premio no se dio y NO añadimos
        // los ids al store — si lo hiciéramos, el retry le cobraría al jugador sin
        // haber recibido el premio.
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(777L)));
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("frank")).thenReturn(CompletableFuture.completedFuture(pending));

        when(apiClient.sendAck(any(), any(), eq(false), anyString())).thenReturn(
                CompletableFuture.failedFuture(new java.io.IOException("timeout")));

        // dispatchCommand devuelve false → entrega falla
        when(plugin.dispatchCommand(anyString())).thenReturn(false);

        MockCommandSender frank = MockCommandSender.player("frank");
        cmd.execute(plugin, frank, "voto40", Collections.emptyList());

        Thread.sleep(200);

        // Como la entrega falló, NO se añaden los ids al store.
        org.mockito.Mockito.verify(apiClient, org.mockito.Mockito.never()).addPendingAck(any(), any());
    }

    @Test
    void ackFailure_deliveredStillTrue_sendsAckFailedMessage() throws Exception {
        PendingVotesResponse pending = new PendingVotesResponse();
        pending.setApiVersion(3);
        pending.setVotosPendientes(Collections.singletonList(makePending(321L)));
        pending.setReservaSegundos(300);
        pending.setPuedeVotarYa(true);
        when(apiClient.fetchPendingVotes("gina")).thenReturn(CompletableFuture.completedFuture(pending));

        // El ack falla (timeout, red caída, etc.)
        when(apiClient.sendAck(any(), any(), anyBoolean(), anyString())).thenReturn(
                CompletableFuture.failedFuture(new java.io.IOException("API call failed: ack timeout")));

        MockCommandSender gina = MockCommandSender.player("gina");
        cmd.execute(plugin, gina, "voto40", Collections.emptyList());

        Thread.sleep(200);

        // No se reintenta; sólo se loguea. El jugador ve "premio entregado pero no pudimos confirmar".
        assertTrue(gina.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("premio")
                        || m.toLowerCase().contains("confirm")
                        || m.toLowerCase().contains("reserva")),
                "Premio entregado + ack fallido → mensaje claro al jugador. Recibido: "
                        + gina.sentMessages);
        verify(apiClient, times(1)).sendAck(any(), any(), anyBoolean(), anyString());
    }

    @Test
    void cooldownBlocksRepeatedCalls() {
        when(apiClient.fetchPendingVotes("henry")).thenReturn(new CompletableFuture<>());

        MockCommandSender henry = MockCommandSender.player("henry");
        CommandResult first = cmd.execute(plugin, henry, "voto40", Collections.emptyList());
        CommandResult second = cmd.execute(plugin, henry, "voto40", Collections.emptyList());

        assertEquals(CommandResult.SUCCESS, first);
        assertEquals(CommandResult.COOLDOWN, second);
        verify(apiClient, times(1)).fetchPendingVotes("henry");
    }

    private PendingVote makePending(long id) {
        PendingVote v = new PendingVote();
        v.setId(id);
        v.setFecha("2026-09-06T17:10:41+02:00");
        v.setDia("2026-09-06");
        v.setOrigen("web");
        return v;
    }
}
