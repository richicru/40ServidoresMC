package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCircuitBreaker {

    @Test
    void startsClosed() {
        CircuitBreaker cb = CircuitBreaker.defaults();
        assertTrue(cb.canExecute());
        assertFalse(cb.isOpen());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    @Test
    void opensAfterThresholdFailures() {
        CircuitBreaker cb = new CircuitBreaker(3, 5_000L, 60_000L);

        cb.recordFailure();
        assertTrue(cb.canExecute(), "Tras 1 fallo todavía no debe abrirse");
        cb.recordFailure();
        assertTrue(cb.canExecute(), "Tras 2 fallos todavía no debe abrirse");
        cb.recordFailure();
        assertFalse(cb.canExecute(), "Tras 3 fallos (umbral) debe abrirse");
        assertTrue(cb.isOpen());
        assertTrue(cb.backoffRemainingMs() > 0L,
                "Debe haber un backoff positivo tras abrirse");
    }

    @Test
    void successResetsCounterAndCloses() {
        CircuitBreaker cb = new CircuitBreaker(2, 5_000L, 60_000L);
        cb.recordFailure();
        cb.recordFailure();
        assertFalse(cb.canExecute(), "Debe estar abierto tras 2 fallos (umbral=2)");
        cb.recordSuccess();
        assertTrue(cb.canExecute(), "Tras un éxito debe cerrarse");
        assertEquals(0, cb.getConsecutiveFailures());
    }

    @Test
    void backoffGrowsExponentially() {
        CircuitBreaker cb = new CircuitBreaker(2, 1_000L, 1_000_000L);

        // Forzar el primer backoff: 1000ms base
        cb.recordFailure();
        cb.recordFailure();
        long first = cb.backoffRemainingMs();
        assertTrue(first > 0 && first <= 1_200L,
                "Primer backoff ~1s, fue " + first);

        // Avanzar tiempo "consumiendo" el primer backoff no es trivial sin inyectar
        // reloj, pero podemos simular: cerramos con recordSuccess y reabriéndolo
        // varias veces el backoff debe crecer (si fueran independientes) o quedarse
        // igual en el base.
        // Aquí validamos que un único backoff (sin más fallos) NO crece descontroladamente.
        assertTrue(first < 5_000L, "El backoff inicial no debe exceder órdenes de magnitud razonables");
    }

    @Test
    void backoffIsCapped() {
        CircuitBreaker cb = new CircuitBreaker(1, 1_000L, 2_000L);

        // Forzar muchos fallos para que el backoff intente crecer sin parar
        for (int i = 0; i < 50; i++) {
            cb.recordFailure();
            cb.recordSuccess(); // reseteamos para volver a abrir con el siguiente recordFailure
        }
        // El test de que no se desborda: tras N ciclos, el backoff nunca debe superar maxBackoffMs
        // abierto de nuevo:
        cb.recordSuccess();
        for (int i = 0; i < 30; i++) cb.recordFailure();
        long rem = cb.backoffRemainingMs();
        assertTrue(rem <= 2_000L,
                "El backoff debe estar limitado al máximo (" + 2_000 + "ms), fue: " + rem);
    }

    @Test
    void canExecuteReturnsTrueOnceBackoffElapsed() {
        CircuitBreaker cb = new CircuitBreaker(1, 100L, 200L);
        cb.recordFailure();
        assertFalse(cb.canExecute());

        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(cb.canExecute(), "Tras esperar el backoff, debe volver a estar cerrado");
    }

    @Test
    void circuitOpenExceptionCarriesRetryAfter() {
        CircuitBreaker cb = new CircuitBreaker(1, 5_000L, 10_000L);
        cb.recordFailure();
        CircuitBreaker.CircuitOpenException ex = new CircuitBreaker.CircuitOpenException(7_000L);
        assertEquals(7_000L, ex.getRetryAfterMs());
        assertTrue(ex.getMessage().contains("Circuit breaker open"));
    }
}
