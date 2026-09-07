package com.cadiducho.cservidoresmc.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
 *
 * <p><b>2026-09-06</b>: dos correcciones sobre la versión original.</p>
 * <ul>
 *   <li>{@code add()} usaba {@code computeIfAbsent(...).addAll(...)} sobre un
 *       {@code ArrayList} plano: la inserción en el mapa es atómica, pero
 *       {@code addAll} sobre la lista devuelta NO lo es. Dos llamadas a
 *       {@code add()} para el MISMO nick casi al mismo tiempo (dos entregas
 *       que fallan su ack en rápida sucesión) podían perder ids o lanzar
 *       {@code ConcurrentModificationException}. Ahora la lista interior es
 *       {@link CopyOnWriteArrayList}.</li>
 *   <li>Sin límite: un jugador que recibe el premio, falla el ack y nunca
 *       vuelve a ejecutar {@code /voto40} dejaba su entrada aquí para
 *       siempre (hasta reiniciar el proceso). En un servidor con jugadores
 *       esporádicos, eso crece sin parar. Cada entrada ahora lleva su
 *       instante de creación y {@link #evictOlderThan} se llama en cada
 *       {@code add()}/{@code take()} para purgar lo más viejo que
 *       {@code maxAge} — llamada barata (recorre un mapa que en la práctica
 *       tiene un puñado de entradas) y no requiere hilo de fondo.</li>
 * </ul>
 */
public class PendingAckStore {

    /** Cuánto tiempo puede quedar una entrada sin reclamarse antes de purgarse. */
    private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(6);

    private final ConcurrentMap<String, Entry> pending = new ConcurrentHashMap<>();
    private final Duration maxAge;

    public PendingAckStore() {
        this(DEFAULT_MAX_AGE);
    }

    /** Visible para tests: permite forzar un maxAge corto y probar la purga sin dormir horas. */
    public PendingAckStore(Duration maxAge) {
        this.maxAge = maxAge;
    }

    /** Asocia ids a un nick para futura re-confirmación. */
    public void add(String nick, List<Long> voteIds) {
        if (nick == null || voteIds == null || voteIds.isEmpty()) return;
        evictOlderThan(maxAge);
        pending.compute(nick, (k, existing) -> {
            Entry entry = existing != null ? existing : new Entry();
            entry.ids.addAll(voteIds);
            return entry;
        });
    }

    /** Devuelve los ids pendientes para un nick y los borra del store. */
    public List<Long> take(String nick) {
        if (nick == null) return List.of();
        evictOlderThan(maxAge);
        Entry taken = pending.remove(nick);
        return taken == null ? List.of() : List.copyOf(taken.ids);
    }

    /** Sólo consulta sin consumir. */
    public List<Long> peek(String nick) {
        if (nick == null) return List.of();
        Entry cur = pending.get(nick);
        return cur == null ? List.of() : List.copyOf(cur.ids);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /** Purga entradas más viejas que {@code age}. Barato: sin hilo de fondo, se llama desde add()/take(). */
    private void evictOlderThan(Duration age) {
        Instant cutoff = Instant.now().minus(age);
        pending.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(cutoff));
    }

    private static final class Entry {
        final List<Long> ids = new CopyOnWriteArrayList<>();
        final Instant createdAt = Instant.now();
    }
}
