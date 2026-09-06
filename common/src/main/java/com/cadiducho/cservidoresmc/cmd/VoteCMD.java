package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.CircuitBreaker;
import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.MessageKey;
import com.cadiducho.cservidoresmc.RateLimitedException;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.AckResponse;
import com.cadiducho.cservidoresmc.model.PendingVote;
import com.cadiducho.cservidoresmc.model.PendingVotesResponse;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import com.cadiducho.cservidoresmc.util.IpHashing;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comando para validar el voto en 40ServidoresMC.
 *
 * <p>v3.1.0 implementa el protocolo v3 (pending + ack) que arregla el bug
 * clásico de /api2.php: marcar el voto como cobrado antes de que el cliente
 * reciba la respuesta. Si se pierde la respuesta con v2, el jugador se queda
 * sin premio. Con v3, el ack explícito es lo que marca como cobrado.</p>
 */
public class VoteCMD extends CSCommand {

    protected VoteCMD() {
        super("voto40", "40servidores.voto", Arrays.asList("votar40", "vote40", "mivoto40"),
                "Valida tu voto en el servidor",
                "Usa /voto40 para validar tu voto en el servidor");
    }

    private Cooldown cooldown;

    private Cooldown cooldown(CSPlugin plugin) {
        if (cooldown == null) {
            int seconds = plugin.getCSConfiguration().getInt("cooldown", 60);
            cooldown = new Cooldown(seconds);
        }
        return cooldown;
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        if (sender.isConsole()) {
            return CommandResult.ONLY_PLAYER;
        }

        Cooldown cd = cooldown(plugin);
        if (cd.isCoolingDown(sender.getName())) {
            return CommandResult.COOLDOWN;
        }

        cd.setOnCooldown(sender.getName());

        sender.sendMessageWithTag(MessageKey.VOTE_FETCHING.resolve(plugin.getCSConfiguration()));

        // Protocolo v3: pending → entregar → ack. Es asíncrono pero lineal;
        // cada paso depende del anterior. No se reintenta pending en bucle.
        String nick = sender.getName();
        plugin.getApiClient().fetchPendingVotes(nick).thenCompose(pending -> {
            // pending NUNCA es null aquí; ApiClient valida que el body no esté vacío.
            if (pending.getVotosPendientes() == null || pending.getVotosPendientes().isEmpty()) {
                return handleEmptyPending(plugin, sender, pending);
            }
            return handlePendingWithVotes(plugin, sender, pending);
        }).exceptionally(e -> {
            plugin.runSyncForPlayer(sender.getName(), () -> handleVoteError(plugin, sender, e));
            return null;
        });

        return CommandResult.SUCCESS;
    }

    /**
     * pending con lista vacía: NO cobramos nada, sólo informamos. Si el server
     * dice que puede_votar_ya=true, mandamos al jugador a la web. Si no, ya
     * canjeó y le decimos cuándo puede volver a votar.
     */
    private java.util.concurrent.CompletableFuture<Void> handleEmptyPending(CSPlugin plugin,
                                                                           CSCommandSender sender,
                                                                           PendingVotesResponse pending) {
        final String nick = sender.getName();
        return java.util.concurrent.CompletableFuture.runAsync(() ->
                plugin.runSyncForPlayer(nick, () -> {
                    if (pending.isPuedeVotarYa()) {
                        // No ha votado hoy: lo mandamos a la web.
                        sender.sendNotVotedTodayLink(
                                MessageKey.VOTE_V3_PENDING_VOTE.resolve(plugin.getCSConfiguration()),
                                // Para v3 no tenemos el `web` del API, pero el cliente Bukkit
                                // puede construir el link estándar a la web de 40servidoresmc.es
                                "https://www.40servidoresmc.es/");
                    } else {
                        // Ya canjeó. Mostramos cuándo puede volver a votar.
                        String sig = pending.getSiguienteVoto() == null ? "—" : pending.getSiguienteVoto();
                        sender.sendMessageWithTag(MessageKey.VOTE_V3_ALREADY_REWARDED.resolve(
                                plugin.getCSConfiguration(), "siguiente_voto", sig));
                    }
                }));
    }

    /**
     * pending con votos: entregar premio y luego ackear. Si la entrega falla,
     * ackeamos con entregado:false para liberar la reserva al instante.
     */
    private java.util.concurrent.CompletableFuture<Void> handlePendingWithVotes(CSPlugin plugin,
                                                                               CSCommandSender sender,
                                                                               PendingVotesResponse pending) {
        final String nick = sender.getName();
        final List<Long> voteIds = pending.getVotosPendientes().stream()
                .map(PendingVote::getId)
                .collect(Collectors.toList());

        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            // Entrega del premio: corre en el scheduler del jugador (region-aware en Folia).
            DeliveryResult result = plugin.runSyncForPlayerWithResult(nick, () -> {
                boolean allOk = dispatchRewards(plugin, sender);
                boolean playerStillOnline = plugin.isPlayerOnline(nick);
                return new DeliveryResult(allOk && playerStillOnline);
            });

            String ipHash = computeIpHash(plugin, nick);
            boolean delivered = result.deliveryOk;

            // Ack — no retry, no bucles. Si falla el ack se loguea pero no se reintenta.
            // La reserva vence a los `reservaSegundos` (5 min por defecto).
            plugin.getApiClient().sendAck(voteIds, nick, delivered, ipHash).thenAccept(ack -> {
                plugin.runSyncForPlayer(nick, () -> onAckReceived(plugin, sender, ack, delivered));
            }).exceptionally(e -> {
                // Si el ack falla, no es crítico: la reserva expira sola. Logueamos
                // para que el admin sepa que algo va mal con el endpoint ack.
                plugin.logError("v3 ack falló (la reserva expirará sola en " +
                        pending.getReservaSegundos() + "s): " + unwrapRootCause(e).getMessage());
                plugin.runSyncForPlayer(nick, () -> {
                    // Mensaje al jugador: premio entregado (o no), no pudimos confirmar.
                    if (delivered) {
                        sender.sendMessageWithTag(MessageKey.VOTE_V3_ACK_FAILED.resolve(plugin.getCSConfiguration()));
                    }
                });
                return null;
            });
        });
    }

    /**
     * Despacha los comandos custom y el broadcast al scheduler correcto.
     * Devuelve {@code true} si todos los comandos se dispatcharon y el jugador
     * seguía online al terminar.
     */
    private boolean dispatchRewards(CSPlugin plugin, CSCommandSender sender) {
        boolean allOk = true;
        for (String cmd : plugin.getCSConfiguration().customCommandsList()) {
            String command = cmd.replace("{0}", sender.getName());
            if (!plugin.dispatchCommand(command)) {
                allOk = false;
            }
        }
        if (plugin.getCSConfiguration().getBoolean("broadcast.activado")) {
            plugin.broadcastMessage(plugin.getCSConfiguration().getString(
                    "broadcast.mensajeBroadcast").replace("{0}", sender.getName()));
        }
        if (plugin.getCSConfiguration().getBoolean("log-ip", false)) {
            String ip = plugin.getPlayerIp(sender.getName());
            plugin.log(String.format("[VoteReward] player=%s ip=%s",
                    sender.getName(), ip != null ? ip : "unknown"));
        }
        return allOk;
    }

    private String computeIpHash(CSPlugin plugin, String nick) {
        String ip = plugin.getPlayerIp(nick);
        return IpHashing.hash(ip);
    }

    /**
     * Mensajes al jugador según el resultado del ack.
     */
    private void onAckReceived(CSPlugin plugin, CSCommandSender sender, AckResponse ack, boolean delivered) {
        if (delivered) {
            // Premio entregado + ack OK
            sender.sendMessageWithTag(MessageKey.VOTE_V3_THANKS.resolve(plugin.getCSConfiguration()));
            // Si el ack llegó con datos inesperados (id expirado, ya confirmado) los logueamos.
            if (ack.getDesconocidos() != null && !ack.getDesconocidos().isEmpty()) {
                plugin.log("[VoteAck] ids desconocidos al server: " + ack.getDesconocidos());
            }
            if (ack.getYaConfirmados() != null && !ack.getYaConfirmados().isEmpty()) {
                plugin.log("[VoteAck] ids ya confirmados (petición duplicada): " + ack.getYaConfirmados());
            }
        } else {
            // Entrega falló y mandamos entregado:false
            sender.sendMessageWithTag(MessageKey.VOTE_V3_DELIVERY_FAILED.resolve(plugin.getCSConfiguration()));
            if (ack.getLiberados() != null && !ack.getLiberados().isEmpty()) {
                plugin.log("[VoteAck] reserva liberada por el server: " + ack.getLiberados());
            }
        }
    }

    /**
     * Recorre la cadena de causas de una excepción y devuelve la raíz, sin
     * envoltorios tipo {@code CompletionException} / {@code IllegalStateException}.
     * Si la cadena tiene bucles (cada causa apunta a la siguiente), corta en el
     * primer nivel.
     */
    public static Throwable unwrapRootCause(Throwable e) {
        Throwable cur = e;
        java.util.IdentityHashMap<Throwable, Boolean> seen = new java.util.IdentityHashMap<>();
        while (cur != null && cur.getCause() != null && cur.getCause() != cur && !seen.containsKey(cur.getCause())) {
            seen.put(cur, Boolean.TRUE);
            cur = cur.getCause();
        }
        return cur == null ? e : cur;
    }

    /**
     * Manejo diferenciado de excepciones en /voto40. Distinguimos tres casos:
     * <ul>
     *   <li>{@link CircuitBreaker.CircuitOpenException}: el plugin no llegó a llamar
     *       a la API porque el circuito estaba abierto. Mensaje al jugador con el
     *       tiempo de reintento; el voto no se ha perdido.</li>
     *   <li>{@link RateLimitedException}: la API devolvió 429. Mensaje con el Retry-After
     *       de la cabecera HTTP.</li>
     *   <li>Cualquier otra excepción: walk hasta el root cause y registramos en
     *       consola con el nombre de la clase (no solo el mensaje) para que un
     *       reporte del dueño sea útil.</li>
     * </ul>
     */
    private void handleVoteError(CSPlugin plugin, CSCommandSender sender, Throwable e) {
        Throwable root = unwrapRootCause(e);
        if (root instanceof CircuitBreaker.CircuitOpenException) {
            long seconds = ((CircuitBreaker.CircuitOpenException) root).getRetryAfterMs() / 1000L;
            sender.sendMessageWithTag(MessageKey.VOTE_CIRCUIT_OPEN.resolve(
                    plugin.getCSConfiguration(), "seconds", String.valueOf(seconds)));
            plugin.logError("Voto saltado: circuit breaker abierto, reintento en " + seconds + "s.");
            return;
        }
        if (root instanceof RateLimitedException) {
            long seconds = ((RateLimitedException) root).getRetryAfterSeconds();
            sender.sendMessageWithTag(MessageKey.VOTE_RATE_LIMITED.resolve(
                    plugin.getCSConfiguration(), "seconds", String.valueOf(seconds)));
            plugin.logError("Voto rechazado: API devolvió 429 (rate limited), reintento en " + seconds + "s.");
            return;
        }
        if (root instanceof java.io.IOException
                && root.getMessage() != null
                && root.getMessage().startsWith("API call failed: HTTP 403")) {
            // 403 = clave incorrecta en v3
            sender.sendMessageWithTag(MessageKey.VOTE_V3_INVALID_KEY.resolve(plugin.getCSConfiguration()));
            plugin.logError("Voto rechazado: API devolvió 403 (clave incorrecta)");
            return;
        }
        // Camino genérico: el mensaje de root.toString() lleva "ClaseEx: mensaje",
        // a diferencia de e.getMessage() que en IllegalStateException es solo
        // "Cannot execute API call".
        sender.sendMessageWithTag(MessageKey.VOTE_EXCEPTION.resolve(plugin.getCSConfiguration()));
        plugin.logError("Excepción intentando votar: " + root.getClass().getName()
                + ": " + root.getMessage());
    }

    /**
     * Holder para el resultado de la entrega del premio dentro del scheduler
     * del jugador. Lombok para evitar boilerplate.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static final class DeliveryResult {
        boolean deliveryOk;
    }
}
