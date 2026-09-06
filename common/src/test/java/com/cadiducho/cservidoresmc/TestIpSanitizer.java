package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.util.IpSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestIpSanitizer {

    @Test
    void ipv4RoundTrips() {
        assertEquals("203.0.113.42", IpSanitizer.sanitize("203.0.113.42"));
    }

    @Test
    void ipv6RoundTrips() {
        // Java normaliza ::1 a su forma completa 0:0:0:0:0:0:0:1 al pasar por getByName
        // + getHostAddress. Lo importante es que la IP es válida, no cómo se canonicaliza.
        assertEquals("0:0:0:0:0:0:0:1", IpSanitizer.sanitize("::1"));
        assertEquals("2001:db8:0:0:0:0:0:1", IpSanitizer.sanitize("2001:db8::1"));
    }

    @Test
    void nullAndEmptyReturnEmptyString() {
        assertEquals("", IpSanitizer.sanitize(null));
        assertEquals("", IpSanitizer.sanitize(""));
    }

    @Test
    void ipv6WithScopeIsDiscardedSilently() {
        // Importante: nunca mandamos un identificador de interfaz.
        assertEquals("", IpSanitizer.sanitize("fe80::1%eth0"));
        assertEquals("", IpSanitizer.sanitize("::1%lo0"));
    }

    @Test
    void invalidFormatsReturnEmpty() {
        assertEquals("", IpSanitizer.sanitize("not-an-ip"));
        assertEquals("", IpSanitizer.sanitize("999.999.999.999"));
        assertEquals("", IpSanitizer.sanitize("1.2.3.4:5678"));
    }

    // ============================================================
    //  Detección de proxy (BungeeCord / Velocity sin ip-forward)
    // ============================================================

    @Test
    void publicIpIsNotProxy() {
        assertFalse(IpSanitizer.isLikelyBehindProxy("203.0.113.42"));
        assertFalse(IpSanitizer.isLikelyBehindProxy("8.8.8.8"));
        assertFalse(IpSanitizer.isLikelyBehindProxy("2001:4860:4860::8888"));
    }

    @Test
    void loopbackIsProxy() {
        assertTrue(IpSanitizer.isLikelyBehindProxy("127.0.0.1"));
        assertTrue(IpSanitizer.isLikelyBehindProxy("127.0.0.42"));
        assertTrue(IpSanitizer.isLikelyBehindProxy("::1"));
    }

    @Test
    void privateRangesAreProxy() {
        // 10/8
        assertTrue(IpSanitizer.isLikelyBehindProxy("10.0.0.5"));
        assertTrue(IpSanitizer.isLikelyBehindProxy("10.255.255.255"));
        // 172.16/12
        assertTrue(IpSanitizer.isLikelyBehindProxy("172.16.0.1"));
        assertTrue(IpSanitizer.isLikelyBehindProxy("172.31.255.254"));
        // 192.168/16
        assertTrue(IpSanitizer.isLikelyBehindProxy("192.168.1.1"));
        // link-local (169.254/16) — los servidores DHCP sin respuesta
        assertTrue(IpSanitizer.isLikelyBehindProxy("169.254.0.1"));
    }

    @Test
    void boundaryCasesAreCorrect() {
        // 172.16/12 cubre 172.16.0.0 .. 172.31.255.255. Fuera de ese rango → público.
        assertFalse(IpSanitizer.isLikelyBehindProxy("172.15.255.255")); // una /16 por debajo
        assertFalse(IpSanitizer.isLikelyBehindProxy("172.32.0.0"));     // una /16 por encima
        assertTrue(IpSanitizer.isLikelyBehindProxy("172.16.0.0"));      // primer /16 dentro
        assertTrue(IpSanitizer.isLikelyBehindProxy("172.31.255.255"));   // último /16 dentro
    }

    @Test
    void emptyAndInvalidReturnFalse() {
        assertFalse(IpSanitizer.isLikelyBehindProxy(null));
        assertFalse(IpSanitizer.isLikelyBehindProxy(""));
        assertFalse(IpSanitizer.isLikelyBehindProxy("not-an-ip"));
    }
}
