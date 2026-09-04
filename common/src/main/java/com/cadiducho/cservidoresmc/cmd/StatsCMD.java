package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.MessageKey;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.ServerStats;
import com.cadiducho.cservidoresmc.model.ServerVote;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comando para obtener las estadísticas de tu servidor en 40ServidoresMC
 * @author Cadiducho
 */
public class StatsCMD extends CSCommand {

    protected StatsCMD() {
        super("stats40", "40servidores.stats", Collections.emptyList(),
                "Comprueba las estadísticas de voto",
                "Usa /stats40 para obtener las estadísticas de voto");
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        plugin.getApiClient().fetchServerStats().thenAccept((ServerStats serverStats) -> {
            if (serverStats.getServerName() == null) { //clave mal configurada
                sender.sendMessageWithTag(MessageKey.STATS_INVALID_KEY.resolve(plugin.getCSConfiguration()));
                return;
            }

            Map<String, String> vars = new HashMap<>();
            vars.put("server", serverStats.getServerName());
            vars.put("position", String.valueOf(serverStats.getPosition()));
            sender.sendMessageWithTag(MessageKey.STATS_HEADER.resolve(plugin.getCSConfiguration(), vars));

            sender.sendMessageWithTag(MessageKey.STATS_DAY_VOTES.resolve(plugin.getCSConfiguration(), "count",
                    String.valueOf(serverStats.getDayVotes())));
            sender.sendMessageWithTag(MessageKey.STATS_DAY_VOTES_REWARDED.resolve(plugin.getCSConfiguration(), "count",
                    String.valueOf(serverStats.getRewardedDayVotes())));
            sender.sendMessageWithTag(MessageKey.STATS_WEEK_VOTES.resolve(plugin.getCSConfiguration(), "count",
                    String.valueOf(serverStats.getWeekVotes())));
            sender.sendMessageWithTag(MessageKey.STATS_WEEK_VOTES_REWARDED.resolve(plugin.getCSConfiguration(), "count",
                    String.valueOf(serverStats.getRewardedWeekVotes())));

            if (serverStats.getLastVotes() != null && !serverStats.getLastVotes().isEmpty()) {
                StringBuilder usuarios = new StringBuilder();
                for (ServerVote vote : serverStats.getLastVotes()) {
                    String color = vote.isRewarded() ? "&a" : "&c";
                    usuarios.append(color).append(vote.getName()).append("&6, ");
                }
                String usuariosStr = usuarios.substring(0, usuarios.length() - 2) + ".";
                sender.sendMessageWithTag(MessageKey.STATS_LAST_VOTES.resolve(plugin.getCSConfiguration(), "votes", usuariosStr));
            }
        }).exceptionally(ex -> {
            sender.sendMessageWithTag(MessageKey.STATS_EXCEPTION.resolve(plugin.getCSConfiguration()));
            plugin.logError("Excepción obteniendo estadisticas: " + ex.getMessage());
            return null;
        });
        return CommandResult.SUCCESS;
    }
}
