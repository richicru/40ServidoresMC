package com.cadiducho.cservidoresmc;

/**
 * Helper para construir el header HTTP {@code User-Agent} que envía el plugin.
 *
 * <p>Formato actual:
 * <pre>40ServidoresMC/&lt;pluginVersion&gt;/&lt;platform&gt;-&lt;serverVersion&gt;/Java&lt;javaVersion&gt;-&lt;javaVendor&gt;</pre>
 *
 * <p>Esta información se manda tanto en las llamadas a la API de 40servidoresmc.es
 * como en las peticiones a GitHub para comprobación de actualizaciones. Permite al
 * autor del plugin identificar de un vistazo qué plataforma y versión de servidor
 * está usando un cliente que reporta problemas.</p>
 */
public final class UserAgent {

    private UserAgent() {}

    /**
     * @param pluginVersion versión del plugin (ej. "3.0")
     * @param platform      tipo de plataforma (ej. "Bukkit", "Sponge")
     * @param serverVersion versión del servidor (ej. "1.20.4", o el Bukkit API version)
     */
    public static String build(String pluginVersion, String platform, String serverVersion) {
        String p = platform == null ? "Unknown" : platform;
        String v = serverVersion == null ? "unknown" : serverVersion;
        return "40ServidoresMC/" + safe(pluginVersion) + "/" + p + "-" + v + "/" + javaInfo();
    }

    private static String javaInfo() {
        String ver = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor");
        String v = sanitizeJavaVersion(ver);
        String vd = vendor == null ? "Unknown" : vendor.replaceAll("\\s+", "_");
        return "Java" + v + "-" + vd;
    }

    /**
     * "17.0.5" -> "17". Mantener solo el major version: reduce ruido en logs del servidor
     * y evita exponer patchlevels de Java.
     */
    private static String sanitizeJavaVersion(String ver) {
        if (ver == null || ver.isEmpty()) return "Unknown";
        int dot = ver.indexOf('.');
        if (dot < 0) return sanitizeTokens(ver);
        return sanitizeTokens(ver.substring(0, dot));
    }

    private static String sanitizeTokens(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
            else break;
        }
        return sb.length() == 0 ? "Unknown" : sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "0" : s;
    }
}
