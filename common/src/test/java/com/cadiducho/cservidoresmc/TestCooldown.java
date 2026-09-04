package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCooldown {

    @Test
    void cooldownStartsInactive() {
        Cooldown cooldown = new Cooldown(60);
        assertFalse(cooldown.isCoolingDown("player1"));
        assertEquals(0, cooldown.getTimeLeft("player1"));
    }

    @Test
    void cooldownActivatesAfterSet() {
        Cooldown cooldown = new Cooldown(60);
        cooldown.setOnCooldown("player1");
        assertTrue(cooldown.isCoolingDown("player1"));
        assertTrue(cooldown.getTimeLeft("player1") > 0);
        assertTrue(cooldown.getTimeLeft("player1") <= 61);
    }

    @Test
    void cooldownIsPerPlayer() {
        Cooldown cooldown = new Cooldown(60);
        cooldown.setOnCooldown("player1");
        assertTrue(cooldown.isCoolingDown("player1"));
        assertFalse(cooldown.isCoolingDown("player2"));
    }

    @Test
    void cooldownExpiresAfterDuration() throws InterruptedException {
        Cooldown cooldown = new Cooldown(1);
        cooldown.setOnCooldown("player1");
        assertTrue(cooldown.isCoolingDown("player1"));
        Thread.sleep(1500);
        assertFalse(cooldown.isCoolingDown("player1"));
        assertEquals(0, cooldown.getTimeLeft("player1"));
    }

    @Test
    void getTimeReturnsConfiguredTime() {
        Cooldown cooldown = new Cooldown(120);
        assertEquals(120, cooldown.getTime());
    }

    @Test
    void resetRemovesPlayerFromCooldown() {
        Cooldown cooldown = new Cooldown(60);
        cooldown.setOnCooldown("player1");
        assertTrue(cooldown.isCoolingDown("player1"));
        cooldown.setOnCooldown("player1");
        assertTrue(cooldown.isCoolingDown("player1"));
    }
}
