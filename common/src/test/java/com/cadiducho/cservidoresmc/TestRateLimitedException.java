package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestRateLimitedException {

    @Test
    void retryAfterMsIsExposed() {
        RateLimitedException ex = new RateLimitedException(15_000L);
        assertEquals(15_000L, ex.getRetryAfterMs());
        assertEquals(15L, ex.getRetryAfterSeconds(),
                "retryAfterSeconds debe ser la conversión entera de ms/1000 para mostrar al jugador");
    }

    @Test
    void retryAfterSecondsRoundsToIntSeconds() {
        // 2500 ms → 2 segundos. Truncamos para mostrar tiempos enteros al jugador.
        assertEquals(2L, new RateLimitedException(2_500L).getRetryAfterSeconds());
        // 999 ms → 0 segundos (caso límite inferior).
        assertEquals(0L, new RateLimitedException(999L).getRetryAfterSeconds());
    }

    @Test
    void isRuntimeException_andMessageMentionsRateLimit() {
        // Importante: debe ser RuntimeException para que se propague desde el
        // wrapper de ApiClient (que sólo envuelve IOException).
        assertTrue(RuntimeException.class.isAssignableFrom(RateLimitedException.class));
        assertTrue(new RateLimitedException(5_000L).getMessage().toLowerCase().contains("rate"));
    }
}
