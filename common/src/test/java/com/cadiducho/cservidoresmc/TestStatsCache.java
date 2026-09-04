package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.ServerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStatsCache {

    private CSPlugin plugin;
    private ApiClient apiClient;
    private StatsCache cache;

    @BeforeEach
    void setup() {
        plugin = mock(CSPlugin.class);
        apiClient = mock(ApiClient.class);
        when(plugin.getApiClient()).thenReturn(apiClient);
        cache = new StatsCache(plugin);
    }

    @Test
    void firstCallTriggersApiFetch() throws Exception {
        ServerStats stats = new ServerStats();
        stats.setServerName("Test");
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        ServerStats result = cache.get().get(2, TimeUnit.SECONDS);
        assertEquals("Test", result.getServerName());
        verify(apiClient, times(1)).fetchServerStats();
    }

    @Test
    void secondCallWithinTtlDoesNotFetchAgain() throws Exception {
        ServerStats stats = new ServerStats();
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        cache = new StatsCache(plugin, 300);
        cache.get().get(2, TimeUnit.SECONDS);
        cache.get().get(2, TimeUnit.SECONDS);
        cache.get().get(2, TimeUnit.SECONDS);

        verify(apiClient, times(1)).fetchServerStats();
    }

    @Test
    void expiredTtlTriggersRefresh() throws Exception {
        ServerStats stats = new ServerStats();
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        cache = new StatsCache(plugin, 1);
        cache.get().get(2, TimeUnit.SECONDS);

        Thread.sleep(1500);
        cache.get().get(2, TimeUnit.SECONDS);

        verify(apiClient, times(2)).fetchServerStats();
    }

    @Test
    void invalidateForcesRefresh() throws Exception {
        ServerStats stats = new ServerStats();
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        cache = new StatsCache(plugin, 300);
        cache.get().get(2, TimeUnit.SECONDS);
        cache.invalidate();
        cache.get().get(2, TimeUnit.SECONDS);

        verify(apiClient, times(2)).fetchServerStats();
    }

    @Test
    void refreshExplicitlyFetches() throws Exception {
        ServerStats stats = new ServerStats();
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        cache.refresh().get(2, TimeUnit.SECONDS);
        cache.refresh().get(2, TimeUnit.SECONDS);

        verify(apiClient, times(2)).fetchServerStats();
    }

    @Test
    void getTtlSecondsReturnsConfigured() {
        assertEquals(300, new StatsCache(plugin).getTtlSeconds());
        assertEquals(60, new StatsCache(plugin, 60).getTtlSeconds());
    }

    @Test
    void concurrentCallsTriggerFetches() throws Exception {
        CompletableFuture<ServerStats> slow = new CompletableFuture<>();
        when(apiClient.fetchServerStats()).thenReturn(slow);
        ServerStats stats = new ServerStats();

        CompletableFuture<ServerStats> a = cache.get();
        CompletableFuture<ServerStats> b = cache.get();
        CompletableFuture<ServerStats> c = cache.get();

        slow.complete(stats);
        a.get(2, TimeUnit.SECONDS);
        b.get(2, TimeUnit.SECONDS);
        c.get(2, TimeUnit.SECONDS);

        verify(apiClient, atLeastOnce()).fetchServerStats();
    }
}
