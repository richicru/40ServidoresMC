package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * Respuesta de {@code GET /api/vote/v3/pending}.
 *
 * <p>Si {@code votosPendientes} está vacío, el plugin decide qué mensaje mostrar
 * al jugador usando {@code puedeVotarYa} (true = "vota en la web", false = "ya
 * canjeado, vuelve el {siguienteVoto}"). Si trae votos, hay que entregarlos
 * y ackear.</p>
 *
 * <p>Mapeo snake_case → camelCase explícito con {@code @SerializedName} para no
 * afectar el resto del plugin (que ya usa modelos con nombres en español).</p>
 */
@Data
public class PendingVotesResponse {
    @SerializedName("api_version")
    private int apiVersion;
    private String jugador;
    private ServerInfo servidor;
    @SerializedName("votos_pendientes")
    private List<PendingVote> votosPendientes;
    @SerializedName("reserva_segundos")
    private int reservaSegundos;
    @SerializedName("puede_votar_ya")
    private boolean puedeVotarYa;
    @SerializedName("siguiente_voto")
    private String siguienteVoto;

    @Data
    public static class ServerInfo {
        private int id;
        private String nombre;
        private String slug;
        private int puesto;
    }
}
