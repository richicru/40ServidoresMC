package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.StatsCache;
import com.cadiducho.cservidoresmc.TestSupport;
import com.cadiducho.cservidoresmc.TestSupport.MockCommandSender;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommand.CommandResult;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.ServerVote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TestStatsCMD {

    private StatsCMD cmd;
    private CSPlugin plugin;
    private ApiClient apiClient;

    @BeforeEach
    void setup() {
        cmd = new StatsCMD();
        TestSupport.MockConfiguration cfg = new TestSupport.MockConfiguration(mock(CSPlugin.class));
        apiClient = mock(ApiClient.class);
        plugin = mock(CSPlugin.class);
        when(plugin.getCSConfiguration()).thenReturn(cfg);
        when(plugin.getApiClient()).thenReturn(apiClient);
        when(plugin.getPluginVersion()).thenReturn("3.0");
        when(plugin.getUpdater()).thenReturn(mock(Updater.class));
        when(plugin.getStatsCache()).thenReturn(mock(StatsCache.class));
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(plugin).log(anyString());
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(plugin).logError(anyString());
    }

    @Test
    void emptyLastVotesDoesNotCrash() throws Exception {
        ServerStats stats = new ServerStats();
        stats.setServerName("TestServer");
        stats.setPosition(10);
        stats.setLastVotes(Collections.emptyList());
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        MockCommandSender sender = MockCommandSender.console();
        CommandResult result = cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        assertEquals(CommandResult.SUCCESS, result);

        Thread.sleep(100);

        assertFalse(sender.sentMessages.stream().anyMatch(m -> m.contains("Últimos 20 votos")),
                "No debe aparecer línea de últimos votos si la lista está vacía");
    }

    @Test
    void nullLastVotesDoesNotCrash() throws Exception {
        ServerStats stats = new ServerStats();
        stats.setServerName("TestServer");
        stats.setPosition(10);
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        MockCommandSender sender = MockCommandSender.console();
        CommandResult result = cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        assertEquals(CommandResult.SUCCESS, result);
        Thread.sleep(100);
        assertFalse(sender.sentMessages.stream().anyMatch(m -> m.contains("Últimos 20 votos")));
    }

    @Test
    void populatedLastVotesShowsAll() throws Exception {
        ServerStats stats = new ServerStats();
        stats.setServerName("MyServer");
        stats.setPosition(1);
        stats.setDayVotes(10);
        stats.setRewardedDayVotes(8);
        stats.setWeekVotes(50);
        stats.setRewardedWeekVotes(40);
        stats.setLastVotes(java.util.Arrays.asList(
                makeVote("alice", true),
                makeVote("bob", false),
                makeVote("carol", true)
        ));
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        MockCommandSender sender = MockCommandSender.console();
        cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        Thread.sleep(100);

        long matches = sender.sentMessages.stream().filter(m -> m.contains("alice") && m.contains("bob") && m.contains("carol")).count();
        assertEquals(1, matches, "Los 3 votos deben aparecer en una sola línea");
    }

    @Test
    void invalidKeyShowsWarning() throws Exception {
        ServerStats stats = new ServerStats();
        stats.setServerName(null);
        stats.setPosition(0);
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        MockCommandSender sender = MockCommandSender.console();
        cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        Thread.sleep(100);

        assertTrue(sender.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("clave incorrecta")));
    }

    @Test
    void exceptionInApiIsHandled() throws Exception {
        CompletableFuture<ServerStats> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("network down"));
        when(apiClient.fetchServerStats()).thenReturn(failed);

        MockCommandSender sender = MockCommandSender.console();
        cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        Thread.sleep(100);

        assertTrue(sender.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("excepci")));
        verify(plugin).logError(contains("network down"));
    }

    @Test
    void statsCmdCachePathIsUsedWhenAvailable() throws Exception {
        StatsCache cache = mock(StatsCache.class);
        when(plugin.getStatsCmdCache()).thenReturn(cache);

        ServerStats stats = new ServerStats();
        stats.setServerName("FromCache");
        stats.setPosition(7);
        stats.setLastVotes(Collections.emptyList());
        when(cache.get()).thenReturn(CompletableFuture.completedFuture(stats));

        MockCommandSender sender = MockCommandSender.console();
        cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        Thread.sleep(100);

        verify(cache, times(1)).get();
        verify(apiClient, never()).fetchServerStats();
        assertTrue(sender.sentMessages.stream().anyMatch(m -> m.contains("FromCache")),
                "El nombre del server debe provenir del cache");
    }

    @Test
    void nullStatsCmdCacheFallsBackToApi() throws Exception {
        when(plugin.getStatsCmdCache()).thenReturn(null);

        ServerStats stats = new ServerStats();
        stats.setServerName("FromApi");
        stats.setLastVotes(Collections.emptyList());
        when(apiClient.fetchServerStats()).thenReturn(CompletableFuture.completedFuture(stats));

        MockCommandSender sender = MockCommandSender.console();
        cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        Thread.sleep(100);

        verify(apiClient, times(1)).fetchServerStats();
        assertTrue(sender.sentMessages.stream().anyMatch(m -> m.contains("FromApi")),
                "El nombre del server debe provenir de la llamada directa a la API");
    }

    @Test
    void statsCmdCacheExceptionIsHandledLikeDirectApiError() throws Exception {
        StatsCache cache = mock(StatsCache.class);
        when(plugin.getStatsCmdCache()).thenReturn(cache);

        CompletableFuture<ServerStats> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("cache error"));
        when(cache.get()).thenReturn(failed);

        MockCommandSender sender = MockCommandSender.console();
        cmd.execute(plugin, sender, "stats40", Collections.emptyList());
        Thread.sleep(100);

        assertTrue(sender.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("excepci")));
    }

    private ServerVote makeVote(String name, boolean rewarded) {
        ServerVote vote = new ServerVote();
        try {
            java.lang.reflect.Field f = ServerVote.class.getDeclaredField("recompensado");
            f.setAccessible(true);
            f.set(vote, rewarded ? 1 : 0);
        } catch (Exception e) {
            fail(e);
        }
        try {
            java.lang.reflect.Field f = ServerVote.class.getDeclaredField("name");
            f.setAccessible(true);
            f.set(vote, name);
        } catch (Exception e) {
            fail(e);
        }
        return vote;
    }
}
