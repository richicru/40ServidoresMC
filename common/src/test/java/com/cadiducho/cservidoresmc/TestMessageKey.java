package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.TestSupport.MockConfiguration;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestMessageKey {

    private final MockConfiguration config = new MockConfiguration(mock(CSPlugin.class));

    @Test
    void returnsDefaultWhenKeyMissing() {
        String result = MessageKey.VOTE_FETCHING.resolve(config);
        assertEquals("&7Obteniendo voto...", result);
    }

    @Test
    void returnsConfigValueWhenKeyPresent() {
        config.set("messages.vote-fetching", "&7Loading vote...");
        assertEquals("&7Loading vote...", MessageKey.VOTE_FETCHING.resolve(config));
    }

    @Test
    void supportsMultiplePlaceholders() {
        config.set("messages.stats-header", "&9{server} &7is at TOP &a#{position}");
        String result = MessageKey.STATS_HEADER.resolve(config, "server", "MyServer");
        // {position} no fue sustituido (no estaba en el map)
        assertEquals("&9MyServer &7is at TOP &a#{position}", result);
    }

    @Test
    void replacesAllPlaceholdersFromMap() {
        config.set("messages.stats-header", "&9{server} &7at &a#{position}");
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("server", "MyServer");
        vars.put("position", "5");
        String result = MessageKey.STATS_HEADER.resolve(config, vars);
        assertEquals("&9MyServer &7at &a#5", result);
    }

    @Test
    void replacesPlaceholderWithNullAsEmpty() {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("key", null);
        String result = MessageKey.STATS_HEADER.resolve(config, vars);
        assertEquals(MessageKey.STATS_HEADER.defaultValue(), result);
    }

    @Test
    void defaultValuesAreInSpanish() {
        assertTrue(MessageKey.VOTE_FETCHING.defaultValue().contains("Obteniendo"));
        assertTrue(MessageKey.VOTE_ALREADY_REWARDED.defaultValue().contains("ya has obtenido"));
        assertTrue(MessageKey.STATS_EXCEPTION.defaultValue().contains("excepción"));
    }

    @Test
    void keyPathUsesDottedFormat() {
        assertEquals("messages." + MessageKey.VOTE_FETCHING.key(),
                "messages." + "vote-fetching");
        assertTrue(MessageKey.STATS_HEADER.key().startsWith("stats-"));
    }

    @Test
    void allMessageKeysAreDistinct() {
        MessageKey[] values = MessageKey.values();
        assertTrue(values.length >= 15, "Debe haber al menos 15 mensajes catalogados");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (MessageKey k : values) {
            assertTrue(seen.add(k.key()), "Clave duplicada: " + k.key());
        }
    }
}
