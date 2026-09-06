package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * Respuesta de {@code POST /api/vote/v3/ack}.
 *
 * <p>Los campos principales:</p>
 * <ul>
 *   <li>{@code confirmados}: ids que el server aceptó como entregados / liberados.</li>
 *   <li>{@code yaConfirmados}: ids que ya estaban confirmados (petición duplicada).</li>
 *   <li>{@code liberados}: ids que el server marcó como no-entregados.</li>
 *   <li>{@code desconocidos}: ids que el server no reconoce (expirados, etc.).</li>
 * </ul>
 */
@Data
public class AckResponse {
    @SerializedName("api_version")
    private int apiVersion;
    private List<Long> confirmados;
    @SerializedName("ya_confirmados")
    private List<Long> yaConfirmados;
    private List<Long> liberados;
    private List<Long> desconocidos;
    private boolean entregado;
}
