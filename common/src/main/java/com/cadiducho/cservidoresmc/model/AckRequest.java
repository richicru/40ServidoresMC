package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cuerpo de {@code POST /api/vote/v3/ack}.
 *
 * <p>{@code entregado = true} confirma que el plugin entregó el premio;
 * {@code entregado = false} libera la reserva al instante para que el voto vuelva
 * a estar disponible. Si el ack no llega (timeout/red), no pasa nada: la reserva
 * expira a los 5 minutos por defecto ({@code reservaSegundos}).</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AckRequest {
    private List<Long> votos;
    private boolean entregado;
    private String nick;
    @SerializedName("user_ip")
    private String userIp;
}
