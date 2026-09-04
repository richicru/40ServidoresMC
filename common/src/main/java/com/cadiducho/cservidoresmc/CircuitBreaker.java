package com.cadiducho.cservidoresmc;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit breaker simple, en memoria, para evitar martillear una API caída.
 *
 * <p>Tras {@link #recordFailure()} consecutivas, todas las llamadas reciben un
 * {@link CircuitOpenException} sin tocar la red, durante una ventana de backoff
 * que crece exponencialmente hasta un máximo. La primera llamada que tenga
 * éxito resetea el contador.</p>
 *
 * <p>El estado se mantiene en memoria: cada reinicio del plugin empieza con el
 * circuito cerrado. No hay persistencia porque la idea es proteger solo durante
 * la sesión actual (un reinicio es ya un "reset natural").</p>
 */
public class CircuitBreaker {

    private final int failureThreshold;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong nextRetryAtMs = new AtomicLong(0);

    /**
     * @param failureThreshold número de fallos consecutivos que abren el circuito (ej. 3)
     * @param baseBackoffMs    backoff inicial tras abrirse (ej. 5_000)
     * @param maxBackoffMs     tope de backoff, aunque los fallos sigan acumulándose (ej. 300_000 = 5 min)
     */
    public CircuitBreaker(int failureThreshold, long baseBackoffMs, long maxBackoffMs) {
        this.failureThreshold = failureThreshold;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    /**
     * Crea un circuit breaker con valores sensatos por defecto: 3 fallos, 5s inicial,
     * máximo 5 min.
     */
    public static CircuitBreaker defaults() {
        return new CircuitBreaker(3, 5_000L, 300_000L);
    }

    /**
     * ¿Podemos ejecutar ya, o seguimos en backoff?
     */
    public boolean canExecute() {
        long nra = nextRetryAtMs.get();
        return nra == 0 || System.currentTimeMillis() >= nra;
    }

    /**
     * @return ms restantes antes de poder ejecutar; 0 si el circuito está cerrado.
     */
    public long backoffRemainingMs() {
        long nra = nextRetryAtMs.get();
        if (nra == 0) return 0L;
        long remaining = nra - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public boolean isOpen() {
        return !canExecute();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Notificar un fallo. Si se alcanza el umbral, se calcula el backoff y se
     * programa la próxima ventana disponible.
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures < failureThreshold) return;
        int over = failures - failureThreshold;
        long backoff = baseBackoffMs << Math.min(over, 30);
        backoff = Math.min(backoff, maxBackoffMs);
        nextRetryAtMs.set(System.currentTimeMillis() + backoff);
    }

    /**
     * Notificar un éxito. Resetea el contador y cierra el circuito.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        nextRetryAtMs.set(0);
    }

    /**
     * Lanzada cuando se intenta ejecutar con el circuito abierto. NO es un fallo
     * de red, por lo que NO incrementa el contador.
     */
    public static class CircuitOpenException extends RuntimeException {
        private final long retryAfterMs;

        public CircuitOpenException(long retryAfterMs) {
            super("Circuit breaker open: retries paused for " + retryAfterMs + " ms");
            this.retryAfterMs = retryAfterMs;
        }

        public long getRetryAfterMs() {
            return retryAfterMs;
        }
    }
}
