package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestUpdaterInfo {

    private final Gson gson = new Gson();

    @Test
    void parseMultipleMinecraftVersions() {
        String json = "{\n" +
                "  \"pluginVersions\": {\n" +
                "    \"3.0\": \"Reescritura\",\n" +
                "    \"2.5\": \"Bug fixes\",\n" +
                "    \"2.4\": \"Initial release\"\n" +
                "  },\n" +
                "  \"minecraftVersions\": {\n" +
                "    \"1.16.5\": \"3.0\",\n" +
                "    \"1.18.2\": \"3.0\",\n" +
                "    \"1.20.4\": \"3.0\"\n" +
                "  }\n" +
                "}";

        UpdaterInfo info = gson.fromJson(json, UpdaterInfo.class);
        assertNotNull(info);
        assertEquals(3, info.getMinecraftVersions().size());
        assertEquals(3, info.getPluginVersions().size());
    }

    @Test
    void getPluginForMinecraftReturnsCorrectEntry() {
        String json = "{\n" +
                "  \"pluginVersions\": {\"3.0\": \"Reescritura\"},\n" +
                "  \"minecraftVersions\": {\"1.20.4\": \"3.0\"}\n" +
                "}";
        UpdaterInfo info = gson.fromJson(json, UpdaterInfo.class);

        Optional<Map.Entry<String, String>> entry = info.getPluginForMinecraft("1.20.4");
        assertTrue(entry.isPresent());
        assertEquals("3.0", entry.get().getKey());
        assertEquals("Reescritura", entry.get().getValue());
    }

    @Test
    void getPluginForMinecraftUnknownReturnsEmpty() {
        String json = "{\n" +
                "  \"pluginVersions\": {\"3.0\": \"Reescritura\"},\n" +
                "  \"minecraftVersions\": {\"1.20.4\": \"3.0\"}\n" +
                "}";
        UpdaterInfo info = gson.fromJson(json, UpdaterInfo.class);

        Optional<Map.Entry<String, String>> entry = info.getPluginForMinecraft("99.99.99");
        assertFalse(entry.isPresent());
    }

    @Test
    void getPluginForMinecraftWhenMinecraftVersionNotMappedReturnsEmpty() {
        String json = "{\n" +
                "  \"pluginVersions\": {\"3.0\": \"Reescritura\"},\n" +
                "  \"minecraftVersions\": {}\n" +
                "}";
        UpdaterInfo info = gson.fromJson(json, UpdaterInfo.class);

        Optional<Map.Entry<String, String>> entry = info.getPluginForMinecraft("1.20.4");
        assertFalse(entry.isPresent());
    }

    @Test
    void emptyUpdaterInfoHandlesGracefully() {
        String json = "{\n" +
                "  \"pluginVersions\": {},\n" +
                "  \"minecraftVersions\": {}\n" +
                "}";
        UpdaterInfo info = gson.fromJson(json, UpdaterInfo.class);

        assertNotNull(info);
        assertNotNull(info.getMinecraftVersions());
        assertNotNull(info.getPluginVersions());
        assertTrue(info.getMinecraftVersions().isEmpty());
        assertTrue(info.getPluginVersions().isEmpty());
    }

    @Test
    void differentMinecraftVersionsMapToDifferentPluginVersions() {
        String json = "{\n" +
                "  \"pluginVersions\": {\n" +
                "    \"2.4\": \"old\",\n" +
                "    \"3.0\": \"new\"\n" +
                "  },\n" +
                "  \"minecraftVersions\": {\n" +
                "    \"1.16.5\": \"2.4\",\n" +
                "    \"1.20.4\": \"3.0\"\n" +
                "  }\n" +
                "}";
        UpdaterInfo info = gson.fromJson(json, UpdaterInfo.class);

        Optional<Map.Entry<String, String>> oldEntry = info.getPluginForMinecraft("1.16.5");
        Optional<Map.Entry<String, String>> newEntry = info.getPluginForMinecraft("1.20.4");

        assertTrue(oldEntry.isPresent());
        assertTrue(newEntry.isPresent());
        assertEquals("2.4", oldEntry.get().getKey());
        assertEquals("3.0", newEntry.get().getKey());
    }
}
