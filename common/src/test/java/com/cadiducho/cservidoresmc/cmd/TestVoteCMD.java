package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.ApiClient;
import com.cadiducho.cservidoresmc.StatsCache;
import com.cadiducho.cservidoresmc.TestSupport;
import com.cadiducho.cservidoresmc.Updater;
import com.cadiducho.cservidoresmc.TestSupport.MockCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.cmd.CSCommand.CommandResult;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TestVoteCMD {

    private VoteCMD cmd;
    private CSPlugin plugin;
    private TestSupport.MockConfiguration configuration;
    private ApiClient apiClient;

    @BeforeEach
    void setup() {
        cmd = new VoteCMD();

        configuration = new TestSupport.MockConfiguration(mock(CSPlugin.class));
        configuration
                .set("clave", "validKey")
                .set("mensaje", "&aPremio entregado")
                .set("broadcast.activado", false)
                .set("comandosCustom", Collections.singletonList("give {0} diamond 1"));

        apiClient = mock(ApiClient.class);

        plugin = mock(CSPlugin.class);
        when(plugin.getCSConfiguration()).thenReturn(configuration);
        when(plugin.getApiClient()).thenReturn(apiClient);
        when(plugin.getPluginVersion()).thenReturn("3.0");
        when(plugin.getUpdater()).thenReturn(mock(Updater.class));
        when(plugin.getStatsCache()).thenReturn(mock(StatsCache.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(plugin).log(anyString());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(plugin).logError(anyString());
    }

    @Test
    void consoleCannotVote() {
        MockCommandSender console = MockCommandSender.console();
        CommandResult result = cmd.execute(plugin, console, "voto40", Collections.emptyList());
        assertEquals(CommandResult.ONLY_PLAYER, result);
        verify(apiClient, never()).validateVote(anyString());
    }

    @Test
    void successfulVoteDispatchesRewards() throws Exception {
        VoteResponse response = new VoteResponse("https://40servidoresmc.es", VoteStatus.SUCCESS);
        when(apiClient.validateVote("alice")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender alice = MockCommandSender.player("alice");
        CommandResult result = cmd.execute(plugin, alice, "voto40", Collections.emptyList());
        assertEquals(CommandResult.SUCCESS, result);

        Thread.sleep(100);

        verify(plugin).dispatchCommand("give alice diamond 1");
    }

    @Test
    void notVotedSendsLink() throws Exception {
        VoteResponse response = new VoteResponse("https://40servidoresmc.es/votar", VoteStatus.NOT_VOTED);
        when(apiClient.validateVote("bob")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender bob = MockCommandSender.player("bob");
        cmd.execute(plugin, bob, "voto40", Collections.emptyList());

        Thread.sleep(100);

        assertTrue(bob.sentMessages.stream().anyMatch(m -> m.contains("https://40servidoresmc.es/votar")),
                "El mensaje debe contener el enlace de votación");
        verify(plugin, never()).dispatchCommand(anyString());
    }

    @Test
    void alreadyVotedDoesNotReward() throws Exception {
        VoteResponse response = new VoteResponse("", VoteStatus.ALREADY_VOTED);
        when(apiClient.validateVote("carol")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender carol = MockCommandSender.player("carol");
        cmd.execute(plugin, carol, "voto40", Collections.emptyList());

        Thread.sleep(100);

        verify(plugin, never()).dispatchCommand(anyString());
        assertTrue(carol.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("ya has obtenido")));
    }

    @Test
    void invalidKeyWarnsUser() throws Exception {
        VoteResponse response = new VoteResponse("", VoteStatus.INVALID_KEY);
        when(apiClient.validateVote("dave")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender dave = MockCommandSender.player("dave");
        cmd.execute(plugin, dave, "voto40", Collections.emptyList());

        Thread.sleep(100);

        assertTrue(dave.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("clave incorrecta")));
        verify(plugin, never()).dispatchCommand(anyString());
    }

    @Test
    void cooldownBlocksRepeatedCalls() {
        when(apiClient.validateVote("eve")).thenReturn(new CompletableFuture<>());

        MockCommandSender eve = MockCommandSender.player("eve");
        CommandResult first = cmd.execute(plugin, eve, "voto40", Collections.emptyList());
        CommandResult second = cmd.execute(plugin, eve, "voto40", Collections.emptyList());

        assertEquals(CommandResult.SUCCESS, first);
        assertEquals(CommandResult.COOLDOWN, second);
        verify(apiClient, times(1)).validateVote("eve");
    }

    @Test
    void broadcastSendsWhenEnabled() throws Exception {
        configuration.set("broadcast.activado", true)
                .set("broadcast.mensajeBroadcast", "&e{0} ha votado");

        VoteResponse response = new VoteResponse("", VoteStatus.SUCCESS);
        when(apiClient.validateVote("frank")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender frank = MockCommandSender.player("frank");
        cmd.execute(plugin, frank, "voto40", Collections.emptyList());

        Thread.sleep(100);

        verify(plugin).broadcastMessage(contains("frank"));
    }

    @Test
    void exceptionInApiCallsSendsGenericError() throws Exception {
        CompletableFuture<VoteResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("boom"));
        when(apiClient.validateVote("grace")).thenReturn(failed);

        MockCommandSender grace = MockCommandSender.player("grace");
        cmd.execute(plugin, grace, "voto40", Collections.emptyList());

        Thread.sleep(100);

        assertTrue(grace.sentMessages.stream().anyMatch(m -> m.toLowerCase().contains("excepci")));
        verify(plugin).logError(contains("boom"));
    }

    @Test
    void cooldownUsesConfiguredValue() throws Exception {
        configuration.set("cooldown", 0);
        VoteResponse response = new VoteResponse("", VoteStatus.SUCCESS);
        when(apiClient.validateVote("henry")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender henry = MockCommandSender.player("henry");
        cmd.execute(plugin, henry, "voto40", Collections.emptyList());
        Thread.sleep(100);

        VoteResponse response2 = new VoteResponse("", VoteStatus.SUCCESS);
        when(apiClient.validateVote("henry")).thenReturn(CompletableFuture.completedFuture(response2));
        CommandResult second = cmd.execute(plugin, henry, "voto40", Collections.emptyList());
        Thread.sleep(100);

        assertEquals(CommandResult.SUCCESS, second,
                "Con cooldown=0, debe poder votar repetidamente");
    }

    @Test
    void ipLoggingWhenEnabled() throws Exception {
        configuration.set("log-ip", true);
        when(plugin.getPlayerIp("ivy")).thenReturn("192.168.1.42");

        VoteResponse response = new VoteResponse("", VoteStatus.SUCCESS);
        when(apiClient.validateVote("ivy")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender ivy = MockCommandSender.player("ivy");
        cmd.execute(plugin, ivy, "voto40", Collections.emptyList());
        Thread.sleep(100);

        verify(plugin).log(contains("player=ivy"));
        verify(plugin).log(contains("ip=192.168.1.42"));
    }

    @Test
    void ipLoggingDisabledByDefault() throws Exception {
        VoteResponse response = new VoteResponse("", VoteStatus.SUCCESS);
        when(apiClient.validateVote("jack")).thenReturn(CompletableFuture.completedFuture(response));

        MockCommandSender jack = MockCommandSender.player("jack");
        cmd.execute(plugin, jack, "voto40", Collections.emptyList());
        Thread.sleep(100);

        verify(plugin, never()).log(contains("VoteReward"));
        verify(plugin, never()).getPlayerIp(anyString());
    }
}
