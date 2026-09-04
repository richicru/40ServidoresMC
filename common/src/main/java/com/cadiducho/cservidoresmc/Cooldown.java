package com.cadiducho.cservidoresmc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Cooldown {

    private final int time;
    private final ConcurrentMap<String, Long> cooldowns;

    public Cooldown(int time) {
        this.time = time;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public int getTime() {
        return time;
    }

    private ConcurrentMap<String, Long> getCooldowns() {
        return cooldowns;
    }

    public int getTimeLeft(String player) {
        if (!isCoolingDown(player)) {
            return 0;
        }
        return (int) (((getCooldowns().get(player) - (System.currentTimeMillis() - (getTime() * 1000))) / 1000) + 1);
    }

    public void setOnCooldown(String player) {
        getCooldowns().put(player, System.currentTimeMillis());
    }

    public boolean isCoolingDown(String player) {
        Long cooldownTime = getCooldowns().get(player);
        if (cooldownTime == null) {
            return false;
        }
        if (cooldownTime >= System.currentTimeMillis() - (getTime() * 1000L)) {
            return true;
        }
        getCooldowns().remove(player, cooldownTime);
        return false;
    }
}