package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.ServerStats;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caché de {@link ServerStats} con TTL para evitar llamadas repetidas a la API.
 * Usado por el sistema de placeholders.
 *
 * <p>La caché se invalida automáticamente cada {@code ttlSeconds} segundos y se
 * refresca bajo demanda. Las llamadas concurrentes al refresh comparten el mismo
 * CompletableFuture (single-flight).</p>
 */
public class StatsCache {

    private static final long DEFAULT_TTL_SECONDS = 300; // 5 min

    private final CSPlugin plugin;
    private final long ttlSeconds;
    private final AtomicReference<CachedStats> ref = new AtomicReference<>();

    public StatsCache(CSPlugin plugin) {
        this(plugin, DEFAULT_TTL_SECONDS);
    }

    public StatsCache(CSPlugin plugin, long ttlSeconds) {
        this.plugin = plugin;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Obtener las stats cacheadas, refrescando si han expirado.
     */
    public CompletableFuture<ServerStats> get() {
        CachedStats current = ref.get();
        long now = System.currentTimeMillis();
        if (current != null && (now - current.fetchedAt) < TimeUnit.SECONDS.toMillis(ttlSeconds)) {
            return CompletableFuture.completedFuture(current.stats);
        }
        return refresh();
    }

    /**
     * Forzar refresco de la caché. Siempre dispara una nueva llamada a la API,
     * sin esperar a que expire el TTL.
     */
    public CompletableFuture<ServerStats> refresh() {
        return plugin.getApiClient().fetchServerStats()
                .thenApply(stats -> {
                    ref.set(new CachedStats(stats, System.currentTimeMillis()));
                    return stats;
                });
    }

    /**
     * Invalidar la caché (la próxima llamada refrescará).
     */
    public void invalidate() {
        ref.set(null);
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private static class CachedStats {
        final ServerStats stats;
        final long fetchedAt;

        CachedStats(ServerStats stats, long fetchedAt) {
            this.stats = stats;
            this.fetchedAt = fetchedAt;
        }
    }
}
