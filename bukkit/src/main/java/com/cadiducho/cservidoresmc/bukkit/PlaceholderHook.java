package com.cadiducho.cservidoresmc.bukkit;

import com.cadiducho.cservidoresmc.model.ServerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PlaceholderHook extends PlaceholderExpansion {

    private final BukkitPlugin bukkitPlugin;

    public PlaceholderHook(BukkitPlugin bukkitPlugin) {
        this.bukkitPlugin = bukkitPlugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getAuthor() {
        return "Cadiducho";
    }

    @Override
    public String getIdentifier() {
        return "40servidoresmc";
    }

    @Override
    public String getVersion() {
        return "1.1.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        // %40servidoresmc_position%
        if (identifier.equals("position")) {
            return getStat(ServerStats::getPosition);
        }

        // %40servidoresmc_day_votes%
        if (identifier.equals("day_votes")) {
            return getStat(ServerStats::getDayVotes);
        }

        // %40servidoresmc_day_votes_rewarded%
        if (identifier.equals("day_votes_rewarded")) {
            return getStat(ServerStats::getRewardedDayVotes);
        }

        // %40servidoresmc_week_votes%
        if (identifier.equals("week_votes")) {
            return getStat(ServerStats::getWeekVotes);
        }

        // %40servidoresmc_week_votes_rewarded%
        if (identifier.equals("week_votes_rewarded")) {
            return getStat(ServerStats::getRewardedWeekVotes);
        }

        // %40servidoresmc_server_name%
        if (identifier.equals("server_name")) {
            return getStat(ServerStats::getServerName);
        }

        return null;
    }

    private String getStat(java.util.function.ToIntFunction<ServerStats> getter) {
        try {
            ServerStats stats = bukkitPlugin.getStatsCache().get().get(5, TimeUnit.SECONDS);
            if (stats == null) return "";
            return String.valueOf(getter.applyAsInt(stats));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }

    private String getStat(java.util.function.Function<ServerStats, String> getter) {
        try {
            ServerStats stats = bukkitPlugin.getStatsCache().get().get(5, TimeUnit.SECONDS);
            if (stats == null) return "";
            String value = getter.apply(stats);
            return value != null ? value : "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }
}
