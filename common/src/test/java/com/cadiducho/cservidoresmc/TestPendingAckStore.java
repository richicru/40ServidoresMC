package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.util.PendingAckStore;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
