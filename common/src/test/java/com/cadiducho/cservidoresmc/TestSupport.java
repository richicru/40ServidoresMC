package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;
import com.cadiducho.cservidoresmc.config.CSConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helpers for tests: in-memory mock implementations of the platform abstractions.
 */
public final class TestSupport {

    private TestSupport() {}

    public static class MockCommandSender implements CSCommandSender {
        private final String name;
        private final boolean console;
        private final List<String> permissions;
        public final List<String> sentMessages = new CopyOnWriteArrayList<>();

        public MockCommandSender(String name, boolean console, String... permissions) {
            this.name = name;
            this.console = console;
            this.permissions = new ArrayList<>();
            for (String p : permissions) this.permissions.add(p);
        }

        public static MockCommandSender player(String name, String... permissions) {
            return new MockCommandSender(name, false, permissions);
        }

        public static MockCommandSender console() {
            return new MockCommandSender("CONSOLE", true);
        }

        @Override public String TAG() { return "&8[&bTest&8]"; }
        @Override public void sendMessage(String message) { sentMessages.add(message); }
        @Override public String getName() { return name; }
        @Override public boolean isConsole() { return console; }
        @Override public boolean hasPermission(String permission) {
            return permissions.contains(permission) || permissions.contains("*");
        }
    }

    public static class MockConfiguration implements CSConfiguration {
        private final Map<String, Object> data = new HashMap<>();
        private final CSPlugin plugin;

        public MockConfiguration(CSPlugin plugin) {
            this.plugin = plugin;
        }

        public MockConfiguration set(String key, Object value) {
            data.put(key, value);
            return this;
        }

        @Override public void reload() { /* no-op */ }
        @Override public String getString(String key, String defValue) {
            Object v = data.get(key);
            return v == null ? defValue : v.toString();
        }
        @Override public int getInt(String key, int defValue) {
            Object v = data.get(key);
            if (v == null) return defValue;
            if (v instanceof Number) return ((Number) v).intValue();
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return defValue; }
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = data.get(key);
            if (v == null) return defValue;
            if (v instanceof Boolean) return (Boolean) v;
            return Boolean.parseBoolean(v.toString());
        }
        @Override public List<String> getStringList(String path, List<String> def) {
            Object v = data.get(path);
            if (v == null) return def;
            if (v instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) v;
                return list;
            }
            return def;
        }
        @Override public Map<String, String> getStringMap(String path, Map<String, String> def) {
            Object v = data.get(path);
            if (v == null) return def;
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) v;
                return map;
            }
            return def;
        }
        @Override public CSPlugin getPlugin() { return plugin; }
    }

    public static class MockPlugin implements CSPlugin {
        public final List<String> logs = new CopyOnWriteArrayList<>();
        public final List<String> errors = new CopyOnWriteArrayList<>();
        public final List<String> dispatchedCommands = new CopyOnWriteArrayList<>();
        public final List<String> broadcasts = new CopyOnWriteArrayList<>();
        public final MockConfiguration configuration;
        public final ApiClient apiClient;
        public final Updater updater;
        public final StatsCache statsCache;

        public MockPlugin() {
            this.configuration = new MockConfiguration(this);
            this.apiClient = new ApiClient(this, new com.google.gson.Gson());
            this.updater = new Updater(this, "1.20.4", "3.0");
            this.statsCache = new StatsCache(this);
        }

        @Override public void log(String text) { logs.add(text); }
        @Override public void logError(String text) { errors.add(text); }
        @Override public void registerCommands() { /* no-op */ }
        @Override public CSConfiguration getCSConfiguration() { return configuration; }
        @Override public ApiClient getApiClient() { return apiClient; }
        @Override public Updater getUpdater() { return updater; }
        @Override public StatsCache getStatsCache() { return statsCache; }
        @Override public StatsCache getStatsCmdCache() { return null; }
        @Override public String getPluginVersion() { return "3.0"; }
        @Override public void dispatchCommand(String command) { dispatchedCommands.add(command); }
        @Override public void broadcastMessage(String message) { broadcasts.add(message); }
        @Override public String getServerPlatform() { return "Test"; }
        @Override public String getServerVersion() { return "test-1.0"; }
    }
}
