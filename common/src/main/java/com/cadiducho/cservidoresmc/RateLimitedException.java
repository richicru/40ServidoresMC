package com.cadiducho.cservidoresmc;

import java.util.concurrent.TimeUnit;

/**
 * Lanzada por {@link ApiClient} cuando la API devuelve HTTP 429 (Too Many Requests).
 *
 * <p>Es un <b>RuntimeException</b> a propósito: queremos que se propague sin
 * envolver en un {@code IllegalStateException("Cannot execute API call", e)} como
 * el resto de errores I/O, para que el caller (VoteCMD, StatsCMD) pueda distinguir
 * este caso del resto y dar un mensaje específico al usuario con el tiempo de
 * reintento.</p>
 *
 * <p>Un 429 NO alimenta el {@link CircuitBreaker}: es una indicación de "ve más
 * despacio", no de "la API está caída". El plugin respeta el valor de
 * {@code Retry-After} que la API envía y muestra al usuario cuánto debe esperar.</p>
 */
public class RateLimitedException extends RuntimeException {

    private final long retryAfterMs;

    public RateLimitedException(long retryAfterMs) {
        super("Rate limited; retry after " + retryAfterMs + " ms");
        this.retryAfterMs = retryAfterMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }

    /**
     * Versión "amigable" para mostrar al jugador, en segundos.
     */
    public long getRetryAfterSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(retryAfterMs);
    }
}
