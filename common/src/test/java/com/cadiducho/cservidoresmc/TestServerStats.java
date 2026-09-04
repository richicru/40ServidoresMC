package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.ServerVote;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestServerStats {

    private final Gson gson = new Gson();

    @Test
    void parseFullServerStats() {
        String json = "{\n" +
                "  \"nombre\": \"MiServidor\",\n" +
                "  \"puesto\": 42,\n" +
                "  \"votoshoy\": 10,\n" +
                "  \"votoshoypremiados\": 8,\n" +
                "  \"votossemanales\": 75,\n" +
                "  \"votossemanalespremiados\": 60,\n" +
                "  \"ultimos20votos\": [\n" +
                "    {\"usuario\": \"alice\", \"recompensado\": 1},\n" +
                "    {\"usuario\": \"bob\", \"recompensado\": 0}\n" +
                "  ]\n" +
                "}";

        ServerStats stats = gson.fromJson(json, ServerStats.class);

        assertNotNull(stats);
        assertEquals("MiServidor", stats.getServerName());
        assertEquals(42, stats.getPosition());
        assertEquals(10, stats.getDayVotes());
        assertEquals(8, stats.getRewardedDayVotes());
        assertEquals(75, stats.getWeekVotes());
        assertEquals(60, stats.getRewardedWeekVotes());
        assertNotNull(stats.getLastVotes());
        assertEquals(2, stats.getLastVotes().size());
    }

    @Test
    void parseServerStatsWithoutVotesList() {
        String json = "{\n" +
                "  \"nombre\": \"ServidorSinVotos\",\n" +
                "  \"puesto\": 100,\n" +
                "  \"votoshoy\": 0,\n" +
                "  \"votoshoypremiados\": 0,\n" +
                "  \"votossemanales\": 0,\n" +
                "  \"votossemanalespremiados\": 0\n" +
                "}";

        ServerStats stats = gson.fromJson(json, ServerStats.class);
        assertNotNull(stats);
        assertNull(stats.getLastVotes());
    }

    @Test
    void parseServerStatsWithEmptyVotesList() {
        String json = "{\n" +
                "  \"nombre\": \"ServidorVacio\",\n" +
                "  \"puesto\": 200,\n" +
                "  \"votoshoy\": 0,\n" +
                "  \"votoshoypremiados\": 0,\n" +
                "  \"votossemanales\": 0,\n" +
                "  \"votossemanalespremiados\": 0,\n" +
                "  \"ultimos20votos\": []\n" +
                "}";

        ServerStats stats = gson.fromJson(json, ServerStats.class);
        assertNotNull(stats);
        List<ServerVote> votes = stats.getLastVotes();
        assertNotNull(votes);
        assertTrue(votes.isEmpty());
    }

    @Test
    void parseInvalidKeyReturnsNullServerName() {
        String json = "{\n" +
                "  \"nombre\": null,\n" +
                "  \"puesto\": 0,\n" +
                "  \"votoshoy\": 0,\n" +
                "  \"votoshoypremiados\": 0,\n" +
                "  \"votossemanales\": 0,\n" +
                "  \"votossemanalespremiados\": 0\n" +
                "}";

        ServerStats stats = gson.fromJson(json, ServerStats.class);
        assertNotNull(stats);
        assertNull(stats.getServerName());
    }

    @Test
    void serverVoteIsRewardedWhenFlagNonZero() {
        ServerVote rewarded = new ServerVote();
        java.lang.reflect.Field f;
        try {
            f = ServerVote.class.getDeclaredField("recompensado");
            f.setAccessible(true);
            f.set(rewarded, 1);
        } catch (Exception e) {
            fail(e);
        }
        assertTrue(rewarded.isRewarded());
    }

    @Test
    void serverVoteNotRewardedWhenFlagZero() {
        ServerVote notRewarded = new ServerVote();
        java.lang.reflect.Field f;
        try {
            f = ServerVote.class.getDeclaredField("recompensado");
            f.setAccessible(true);
            f.set(notRewarded, 0);
        } catch (Exception e) {
            fail(e);
        }
        assertFalse(notRewarded.isRewarded());
    }

    @Test
    void emptyLastVotesCanBeProcessedWithoutException() {
        List<ServerVote> emptyList = Collections.emptyList();
        assertDoesNotThrow(() -> {
            StringBuilder sb = new StringBuilder();
            for (ServerVote v : emptyList) {
                sb.append(v.getName()).append(", ");
            }
            String result = sb.length() > 0
                    ? sb.substring(0, sb.length() - 2)
                    : "";
            assertEquals("", result);
        });
    }
}
