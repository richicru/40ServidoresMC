package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestVoteStatus {

    private final Gson gson = new Gson();

    @Test
    void parseZeroAsNotVoted() {
        VoteStatus status = gson.fromJson("\"0\"", VoteStatus.class);
        assertEquals(VoteStatus.NOT_VOTED, status);
    }

    @Test
    void parseOneAsSuccess() {
        VoteStatus status = gson.fromJson("\"1\"", VoteStatus.class);
        assertEquals(VoteStatus.SUCCESS, status);
    }

    @Test
    void parseTwoAsAlreadyVoted() {
        VoteStatus status = gson.fromJson("\"2\"", VoteStatus.class);
        assertEquals(VoteStatus.ALREADY_VOTED, status);
    }

    @Test
    void parseThreeAsInvalidKey() {
        VoteStatus status = gson.fromJson("\"3\"", VoteStatus.class);
        assertEquals(VoteStatus.INVALID_KEY, status);
    }

    @Test
    void allStatusesAreDistinct() {
        VoteStatus[] values = VoteStatus.values();
        assertEquals(4, values.length);
    }

    @Test
    void parseFullVoteResponseSuccess() {
        String json = "{\"web\":\"https://40servidoresmc.es\",\"status\":\"1\"}";
        VoteResponse response = gson.fromJson(json, VoteResponse.class);
        assertNotNull(response);
        assertEquals("https://40servidoresmc.es", response.getWeb());
        assertEquals(VoteStatus.SUCCESS, response.getStatus());
    }

    @Test
    void parseFullVoteResponseNotVoted() {
        String json = "{\"web\":\"https://40servidoresmc.es/votar\",\"status\":\"0\"}";
        VoteResponse response = gson.fromJson(json, VoteResponse.class);
        assertEquals(VoteStatus.NOT_VOTED, response.getStatus());
    }

    @Test
    void parseFullVoteResponseInvalidKey() {
        String json = "{\"web\":\"\",\"status\":\"3\"}";
        VoteResponse response = gson.fromJson(json, VoteResponse.class);
        assertEquals(VoteStatus.INVALID_KEY, response.getStatus());
    }
}
