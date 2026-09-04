package com.cadiducho.cservidoresmc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para {@link UserAgent}. El formato es:
 * {@code 40ServidoresMC/<pluginVersion>/<platform>-<serverVersion>/Java<javaMajor>-<vendor>}.
 */
class TestUserAgent {

    @Test
    void containsAllExpectedFields() {
        String ua = UserAgent.build("3.0", "Bukkit", "1.20.4");

        assertTrue(ua.startsWith("40ServidoresMC/3.0/Bukkit-1.20.4/"),
                "Debe empezar con '40ServidoresMC/3.0/Bukkit-1.20.4/...', fue: " + ua);
        assertTrue(ua.contains("Java"), "Debe contener la versión de Java, fue: " + ua);
    }

    @Test
    void replacesNullPlatformAndVersionWithDefaults() {
        String ua = UserAgent.build("3.0", null, null);
        assertTrue(ua.contains("Unknown-unknown"),
                "Platform/version nulos deben caer a 'Unknown-unknown', fue: " + ua);
    }

    @Test
    void sanitizesJavaVendorWhitespace() {
        // No podemos cambiar System.getProperty("java.vendor") sin security manager,
        // pero podemos verificar que la lógica actual no lo deja con espacios sueltos.
        String ua = UserAgent.build("3.0", "Bukkit", "1.20.4");
        // Si java.vendor contuviera espacios, el helper debería convertirlos a _
        // Verificamos estructura: la parte final es /Java<num>-<vendor>
        int javaIdx = ua.indexOf("/Java");
        assertTrue(javaIdx > 0);
        String tail = ua.substring(javaIdx + 1);
        assertFalse(tail.contains(" "), "La parte Java/info no debe contener espacios: " + tail);
    }

    @Test
    void keepsOnlyMajorJavaVersion() {
        // Forzamos java.version con un valor verificable. Si no se puede cambiar,
        // al menos verificamos que la salida no contiene números de patch tipo ".0.5"
        // después de 'Java'.
        String ua = UserAgent.build("3.0", "Bukkit", "1.20.4");
        int idx = ua.indexOf("/Java");
        assertTrue(idx > 0, "Debe contener segmento Java: " + ua);
        String javaSeg = ua.substring(idx + "/Java".length());
        // 'javaSeg' es "<num>-<vendor>" hasta el final del string
        int dash = javaSeg.indexOf('-');
        assertTrue(dash > 0, "Debe tener formato 'Java<n>-<vendor>', fue: " + javaSeg);
        String numPart = javaSeg.substring(0, dash);
        assertFalse(numPart.contains("."),
                "Solo debe incluir la versión major, sin puntos/patch: " + numPart);
        for (int i = 0; i < numPart.length(); i++) {
            char c = numPart.charAt(i);
            assertTrue(Character.isDigit(c),
                    "El componente numérico de Java debe ser solo dígitos, fue: " + numPart);
        }
    }

    @Test
    void unknownPluginVersionDoesNotCrash() {
        // No debe lanzar NullPointerException ni concatenar "null"
        String ua = UserAgent.build(null, "Bukkit", "1.20.4");
        assertFalse(ua.contains("null"), "No debe contener literal 'null': " + ua);
    }

    @Test
    void differentPlatformsProduceDifferentOutput() {
        String a = UserAgent.build("3.0", "Bukkit", "1.20.4");
        String b = UserAgent.build("3.0", "Sponge", "1.20.4");
        assertNotEquals(a, b);
        assertTrue(a.contains("Bukkit-"));
        assertTrue(b.contains("Sponge-"));
    }
}
