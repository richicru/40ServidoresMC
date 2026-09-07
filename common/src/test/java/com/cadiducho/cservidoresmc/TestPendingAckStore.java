package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.util.PendingAckStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class TestPendingAckStore {

    @Test
    void emptyByDefault() {
        PendingAckStore store = new PendingAckStore();
        assertTrue(store.isEmpty());
        assertEquals(List.of(), store.peek("alice"));
        assertEquals(List.of(), store.take("alice"));
    }

    @Test
    void addThenPeek() {
        PendingAckStore store = new PendingAckStore();
        store.add("alice", List.of(1L, 2L));
        assertEquals(List.of(1L, 2L), store.peek("alice"));
        assertEquals(List.of(1L, 2L), store.peek("alice")); // peek no consume
    }

    @Test
    void takeConsumes() {
        PendingAckStore store = new PendingAckStore();
        store.add("alice", List.of(1L));
        assertEquals(List.of(1L), store.take("alice"));
        assertTrue(store.isEmpty());
        assertEquals(List.of(), store.peek("alice"));
    }

    @Test
    void differentNicksIsolated() {
        PendingAckStore store = new PendingAckStore();
        store.add("alice", List.of(1L));
        store.add("bob", List.of(2L));
        assertEquals(List.of(1L), store.peek("alice"));
        assertEquals(List.of(2L), store.peek("bob"));
    }

    @Test
    void addAccumulates() {
        PendingAckStore store = new PendingAckStore();
        store.add("alice", List.of(1L));
        store.add("alice", List.of(2L, 3L));
        assertEquals(List.of(1L, 2L, 3L), store.peek("alice"));
    }

    @Test
    void addEmptyOrNullIsNoOp() {
        PendingAckStore store = new PendingAckStore();
        store.add(null, List.of(1L));
        store.add("alice", null);
        store.add("alice", List.of());
        assertTrue(store.isEmpty());
    }

    /**
     * Regresión 2026-09-06: `computeIfAbsent(...).addAll(...)` sobre un
     * ArrayList plano no es atómico. Dos entregas del MISMO jugador que
     * fallan su ack casi a la vez (poco frecuente pero posible: dos
     * respuestas de red lentas solapadas) podían perder ids o lanzar
     * ConcurrentModificationException. Lanzamos muchos `add()` concurrentes
     * sobre el mismo nick y comprobamos que no se pierde ni un id.
     */
    @Test
    void concurrentAddsForSameNickDoNotLoseIds() throws InterruptedException {
        PendingAckStore store = new PendingAckStore();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final long id = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    store.add("alice", List.of(id));
                } catch (Throwable t) {
                    errors.incrementAndGet();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, errors.get(), "ninguna llamada a add() debe lanzar");
        List<Long> result = store.peek("alice");
        List<Long> expected = LongStream.range(0, threads).boxed().collect(Collectors.toList());
        assertEquals(threads, result.size(), "no debe perderse ningún id bajo concurrencia");
        assertTrue(new ArrayList<>(result).containsAll(expected), "deben estar todos los ids, sin importar el orden");
    }

    /**
     * Regresión 2026-09-06: sin límite, un jugador cuyo ack falla y nunca
     * vuelve a ejecutar /voto40 se queda en el mapa para siempre. Con
     * maxAge=0 (constructor de test), la siguiente operación debe purgarlo.
     */
    @Test
    void entriesOlderThanMaxAgeAreEvicted() throws InterruptedException {
        PendingAckStore store = new PendingAckStore(Duration.ofMillis(1));
        store.add("alice", List.of(1L));
        assertEquals(List.of(1L), store.peek("alice"));

        Thread.sleep(20); // superar el maxAge de 1ms

        // add()/take() disparan la purga internamente; probamos con take() de
        // OTRO nick para no reinsertar "alice" durante la comprobación.
        store.take("bob");
        assertTrue(store.isEmpty(), "la entrada de alice debe haberse purgado por antigüedad");
    }
}
