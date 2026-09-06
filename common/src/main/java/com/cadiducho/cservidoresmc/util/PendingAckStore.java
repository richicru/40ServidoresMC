package com.cadiducho.cservidoresmc.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache en memoria de acks pendientes.
 *
 * <p>Si la entrega del premio tuvo éxito pero el ack posterior a
 * {@code POST /api/vote/v3/ack} falló (timeout, red caída, etc.), guardamos
 * los ids aquí para reintentarlos en el próximo {@code /voto40} del mismo
 * jugador. Así convertimos un duplicado aceptado en prácticamente cero sin
 * tareas de fondo ni bucles.</p>
 *
 * <p>Memoria volátil: si el server se reinicia entre la entrega y el ack
 * exitoso, los ids se pierden. El server web asume que esos votos quedan
 * como "pendientes" y los mostrará de nuevo al jugador — comportamiento
 * aceptable y consistente con el flujo principal.</p>
 */
public class PendingAckStore {

    private final ConcurrentMap<String, List<Long>> pending = new ConcurrentHashMap<>();

    /** Asocia ids a un nick para futura re-confirmación. */
    public void add(String nick, List<Long> voteIds) {
        if (nick == null || voteIds == null || voteIds.isEmpty()) return;
        pending.computeIfAbsent(nick, k -> new ArrayList<>()).addAll(voteIds);
    }

    /** Devuelve los ids pendientes para un nick y los borra del store. */
    public List<Long> take(String nick) {
        if (nick == null) return List.of();
        List<Long> taken = pending.remove(nick);
        return taken == null ? List.of() : taken;
    }

    /** Sólo consulta sin consumir. */
    public List<Long> peek(String nick) {
        if (nick == null) return List.of();
        List<Long> cur = pending.get(nick);
        return cur == null ? List.of() : List.copyOf(cur);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }
}
