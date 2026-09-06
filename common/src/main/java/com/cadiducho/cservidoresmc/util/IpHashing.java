package com.cadiducho.cservidoresmc.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash de IPs para enviar a la API de v3 de 40ServidoresMC.
 *
 * <p>El protocolo v3 acepta opcionalmente la IP del jugador hasheada (SHA-256)
 * para validar que quien ejecuta /voto40 es quien emitió el voto en la web.
 * La IP nunca se guarda en claro — sólo el hash.</p>
 *
 * <p>Casos de descarte silencioso (devuelve ""):</p>
 * <ul>
 *   <li>Input null o vacío.</li>
 *   <li>Contiene un sufijo de scope IPv6 (ej. "fe80::1%eth0") — filtrar antes de
 *       hashear para no filtrar el identificador de interfaz.</li>
 *   <li>{@link InetAddress#getByName} falla (formato inválido, puerto pegado, etc.).</li>
 * </ul>
 *
 * <p>Si falla, el ack al server se envía sin user_ip igualmente — la entrega del
 * premio no depende de la IP. Es información secundaria para auditoría.</p>
 */
public final class IpHashing {

    private IpHashing() {}

    /**
     * Hash SHA-256 hexadecimal en minúsculas (64 chars). Devuelve "" si la IP
     * es null, vacía, tiene scope IPv6 o no parsea como dirección.
     */
    public static String hash(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        if (ip.contains("%")) return ""; // IPv6 scope suffix → descarta silenciosamente
        try {
            InetAddress addr = InetAddress.getByName(ip);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(addr.getAddress());
            return HexFormat.of().formatHex(digest);
        } catch (UnknownHostException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}
