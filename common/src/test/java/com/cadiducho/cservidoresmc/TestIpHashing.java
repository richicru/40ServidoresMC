package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.util.IpHashing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestIpHashing {

    @Test
    void hashProduces64HexChars() {
        String h = IpHashing.hash("203.0.113.42");
        assertNotNull(h);
        assertEquals(64, h.length(), "SHA-256 en hex son 64 chars");
        assertTrue(h.matches("[0-9a-f]+"), "Sólo debe contener hex minúsculas");
    }

    @Test
    void hashingIsDeterministic() {
        assertEquals(IpHashing.hash("1.2.3.4"), IpHashing.hash("1.2.3.4"));
        assertNotEquals(IpHashing.hash("1.2.3.4"), IpHashing.hash("1.2.3.5"));
    }

    @Test
    void emptyAndNullReturnEmptyString() {
        assertEquals("", IpHashing.hash(""));
        assertEquals("", IpHashing.hash(null));
    }

    @Test
    void ipv6WithScopeIsDiscardedSilently() {
        assertEquals("", IpHashing.hash("fe80::1%eth0"),
                "IPv6 con scope suffix puede filtrar info del interfaz, descartar");
        assertEquals("", IpHashing.hash("::1%lo0"));
    }

    @Test
    void invalidFormatsAreDiscarded() {
        assertEquals("", IpHashing.hash("not-an-ip"));
        assertEquals("", IpHashing.hash("999.999.999.999"));
        assertEquals("", IpHashing.hash("1.2.3.4:5678"),
                "Si por error nos llega una IP con puerto, descartar sin error");
    }

    @Test
    void ipv6NormalIsHashed() {
        String h = IpHashing.hash("2001:db8::1");
        assertNotEquals("", h);
        assertEquals(64, h.length());
    }

    @Test
    void differentIpsDifferentHashes() {
        // IPv4 vs IPv6 → hashes distintos
        String ipv4 = IpHashing.hash("8.8.8.8");
        String ipv6 = IpHashing.hash("2001:4860:4860::8888");
        assertNotEquals(ipv4, ipv6);
        assertNotEquals("", ipv4);
        assertNotEquals("", ipv6);
    }
}
