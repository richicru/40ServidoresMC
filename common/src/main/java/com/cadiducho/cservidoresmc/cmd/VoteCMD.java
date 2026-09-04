package com.cadiducho.cservidoresmc.cmd;

import com.cadiducho.cservidoresmc.Cooldown;
import com.cadiducho.cservidoresmc.MessageKey;
import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.model.VoteResponse;
import com.cadiducho.cservidoresmc.model.VoteStatus;

import java.util.Arrays;
import java.util.List;

/**
 * Comando para validar el voto en 40ServidoresMC
 */
public class VoteCMD extends CSCommand {

    protected VoteCMD() {
        super("voto40", "40servidores.voto", Arrays.asList("votar40", "vote40", "mivoto40"),
                "Valida tu voto en el servidor",
                "Usa /voto40 para validar tu voto en el servidor");
    }

    private Cooldown cooldown;

    private Cooldown cooldown(CSPlugin plugin) {
        if (cooldown == null) {
            int seconds = plugin.getCSConfiguration().getInt("cooldown", 60);
            cooldown = new Cooldown(seconds);
        }
        return cooldown;
    }

    @Override
    public CommandResult execute(CSPlugin plugin, CSCommandSender sender, String label, List<String> args) {
        if (sender.isConsole()) {
            return CommandResult.ONLY_PLAYER;
        }

        Cooldown cd = cooldown(plugin);
        if (cd.isCoolingDown(sender.getName())) {
            return CommandResult.COOLDOWN;
        }

        cd.setOnCooldown(sender.getName());

        sender.sendMessageWithTag(MessageKey.VOTE_FETCHING.resolve(plugin.getCSConfiguration()));
        plugin.getApiClient().validateVote(sender.getName()).thenAccept((VoteResponse voteResponse) -> {
            String web = voteResponse.getWeb();
            VoteStatus status = voteResponse.getStatus();

            switch (status) {
                case NOT_VOTED:
                    sender.sendNotVotedTodayLink(
                            MessageKey.VOTE_NOT_VOTED_PREFIX.resolve(plugin.getCSConfiguration()), web);
                    break;
                case SUCCESS:
                    sender.sendMessageWithTag(plugin.getCSConfiguration().getString("mensaje"));

                    plugin.getCSConfiguration().customCommandsList().stream()
                            .map(cmds -> cmds.replace("{0}", sender.getName()))
                            .forEach(plugin::dispatchCommand);

                    if (plugin.getCSConfiguration().getBoolean("broadcast.activado")) {
                        plugin.broadcastMessage(plugin.getCSConfiguration().getString("broadcast.mensajeBroadcast").replace("{0}", sender.getName()));
                    }

                    if (plugin.getCSConfiguration().getBoolean("log-ip", false)) {
                        String ip = plugin.getPlayerIp(sender.getName());
                        plugin.log(String.format("[VoteReward] player=%s ip=%s",
                                sender.getName(), ip != null ? ip : "unknown"));
                    }
                    break;
                case ALREADY_VOTED:
                    sender.sendMessageWithTag(MessageKey.VOTE_ALREADY_REWARDED.resolve(plugin.getCSConfiguration()));
                    break;
                case INVALID_KEY:
                    sender.sendMessageWithTag(MessageKey.VOTE_INVALID_KEY.resolve(plugin.getCSConfiguration()));
                    break;
                default:
                    sender.sendMessageWithTag(MessageKey.VOTE_ERROR.resolve(plugin.getCSConfiguration()));
                    break;
            }
        }).exceptionally(e -> {
            sender.sendMessageWithTag(MessageKey.VOTE_EXCEPTION.resolve(plugin.getCSConfiguration()));
            plugin.logError("Excepción intentando votar: " + e.getMessage());
            return null;
        });

        return CommandResult.SUCCESS;
    }
}
