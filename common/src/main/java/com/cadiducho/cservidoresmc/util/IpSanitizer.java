package com.cadiducho.cservidoresmc.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Limpieza de la IP antes de enviarla a la API de v3.
 *
 * <p>El server (40servidoresmc.es) espera la IP en claro en el campo
 * {@code user_ip} del ack. Lo cruza con la IP que el usuario dejó al votar en la
 * web (ambos pasan por {@code filter_var($ip, FILTER_VALIDATE_IP)} en su lado). Si
 * nosotros hasheáramos, ese cruce fallaría siempre — y un hash sin sal es
 * "privacidad" de cara a la galería, no de cara al cruce.</p>
 *
 * <p>Sólo saneamos: IPv6 con scope (ej. {@code fe80::1%eth0}) puede filtrar
 * identificadores de interfaz, así que descartamos silenciosamente. IPs sin
 * formato válido también.</p>
 */
public final class IpSanitizer {

    private IpSanitizer() {}

    /**
     * Devuelve la IP en claro si parsea como dirección válida y no trae scope.
     * En cualquier otro caso (null, vacío, con scope, formato inválido) devuelve "".
     */
    public static String sanitize(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        if (ip.contains("%")) return ""; // IPv6 scope suffix: descartar silenciosamente
        try {
            // getByName canónico + getHostAddress para devolver formato normalizado
            // (ej. "0:0:0:0:0:0:0:1" → "::1").
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            return "";
        }
    }

    /**
     * ¿La IP tiene aspecto de venir de un proxy sin ip-forward activado?
     *
     * <p>Heurística conservadora del propio operador de 40servidoresmc.es:
     * si la IP es loopback o pertenece a un rango privado (RFC 1918 / RFC 6890),
     * es el síntoma clásico de BungeeCord / Velocity sin forwarding. La IP es
     * la del proxy para todos los jugadores, así que mandarla como {@code user_ip}
     * produce cruces negativos en cada voto y hace parecer fraudulento al servidor.</p>
     *
     * <p>En ese caso el caller debería NO mandar {@code user_ip} (string vacío).</p>
     */
    public static boolean isLikelyBehindProxy(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isLoopbackAddress()) return true;       // 127.0.0.0/8, ::1
            if (addr.isSiteLocalAddress()) return true;        // 10/8, 172.16/12, 192.168/16
            if (addr.isLinkLocalAddress()) return true;       // 169.254/16, fe80::/10
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
