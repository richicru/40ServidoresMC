package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.CircuitBreaker;
import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.MessageKey;
import com.cadiducho.cservidoresmc.RateLimitedException;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;

import java.util.Arrays;
import java.util.List;

/**
 * Comando para validar el voto en 40ServidoresMC
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
        plugin.getApiClient().validateVote(sender.getName()).thenAccept((VoteResponse voteResponse) -> {
            plugin.runSyncForPlayer(sender.getName(), () -> handleVoteResponse(plugin, sender, voteResponse));
        }).exceptionally(e -> {
            plugin.runSyncForPlayer(sender.getName(), () -> handleVoteError(plugin, sender, e));
            return null;
        });

        return CommandResult.SUCCESS;
    }

    /**
     * Lógica de respuesta al voto, extraída para poder ejecutarse en el thread correcto
     * (entidad/jugador en Folia, main thread en Paper clásico).
     */
    private void handleVoteResponse(CSPlugin plugin, CSCommandSender sender, VoteResponse voteResponse) {
        String web = voteResponse.getWeb();
        VoteStatus status = voteResponse.getStatus();

        switch (status) {
            case NOT_VOTED:
                sender.sendNotVotedTodayLink(
                        MessageKey.VOTE_NOT_VOTED_PREFIX.resolve(plugin.getCSConfiguration()), web);
                break;
            case SUCCESS:
                sender.sendMessageWithTag(plugin.getCSConfiguration().getString("mensaje"));

                plugin.getCSConfiguration().customCommandsList().stream()
                        .map(cmds -> cmds.replace("{0}", sender.getName()))
                        .forEach(plugin::dispatchCommand);

                if (plugin.getCSConfiguration().getBoolean("broadcast.activado")) {
                    plugin.broadcastMessage(plugin.getCSConfiguration().getString("broadcast.mensajeBroadcast").replace("{0}", sender.getName()));
                }

                if (plugin.getCSConfiguration().getBoolean("log-ip", false)) {
                    String ip = plugin.getPlayerIp(sender.getName());
                    plugin.log(String.format("[VoteReward] player=%s ip=%s",
                            sender.getName(), ip != null ? ip : "unknown"));
                }
                break;
            case ALREADY_VOTED:
                sender.sendMessageWithTag(MessageKey.VOTE_ALREADY_REWARDED.resolve(plugin.getCSConfiguration()));
                break;
            case INVALID_KEY:
                sender.sendMessageWithTag(MessageKey.VOTE_INVALID_KEY.resolve(plugin.getCSConfiguration()));
                break;
            default:
                sender.sendMessageWithTag(MessageKey.VOTE_ERROR.resolve(plugin.getCSConfiguration()));
                break;
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
        // Camino genérico: el mensaje de root.toString() lleva "ClaseEx: mensaje",
        // a diferencia de e.getMessage() que en IllegalStateException es solo
        // "Cannot execute API call".
        sender.sendMessageWithTag(MessageKey.VOTE_EXCEPTION.resolve(plugin.getCSConfiguration()));
        plugin.logError("Excepción intentando votar: " + root.getClass().getName()
                + ": " + root.getMessage());
    }
}
