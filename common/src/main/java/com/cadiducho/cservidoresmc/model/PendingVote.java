package com.cadiducho.cservidoresmc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Un voto pendiente devuelto por {@code GET /api/vote/v3/pending}.
 *
 * <p>El {@code id} es lo que tenemos que mandar de vuelta al server en el ack
 * (campo {@code votos} del {@link AckRequest}).</p>
 */
@Data
public class PendingVote {
    private long id;
    private String fecha;
    private String dia;
    @SerializedName("origen")
    private String origen;
}
