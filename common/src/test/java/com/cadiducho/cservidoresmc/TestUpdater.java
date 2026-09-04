package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.model.updater.UpdaterInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestUpdater {

    @Test
    void parseUpdateRequest() {
        String file = "{\n" +
                "    \"pluginVersions\": {\n" +
                "        \"3.0\": \"Reescritura del sistema para hacerlo compatible con Spigot, Sponge y BungeeCord\"\n" +
                "    },\n" +
                "    \"minecraftVersions\": {\n" +
                "        \"1.8.8\": \"3.0\",\n" +
                "        \"1.12.2\": \"3.0\",\n" +
                "        \"1.13.2\": \"3.0\",\n" +
                "        \"1.14.4\": \"3.0\",\n" +
                "        \"1.15.2\": \"3.0\",\n" +
                "        \"1.16.2\": \"3.0\",\n" +
                "        \"1.16.4\": \"3.0\",\n" +
                "        \"1.16.5\": \"3.0\"\n" +
                "    }\n" +
                "}";
        Gson gson = new Gson();
        UpdaterInfo updaterInfo = gson.fromJson(file, UpdaterInfo.class);
        assertNotNull(updaterInfo);
        assertEquals("3.0", updaterInfo.getMinecraftVersions().get("1.16.5"));
        Optional<Map.Entry<String, String>> versionEntry = updaterInfo.getPluginForMinecraft("1.16.5");
        assertTrue(versionEntry.isPresent());

        String updaterVersion = versionEntry.get().getKey();
        String updateDescription = versionEntry.get().getValue();
        assertEquals("3.0", updaterVersion);
        assertEquals("Reescritura del sistema para hacerlo compatible con Spigot, Sponge y BungeeCord", updateDescription);
    }

    @Test
    void defaultRepoIsRichicru() {
        assertEquals("richicru/40ServidoresMC", Updater.DEFAULT_REPO,
                "El repo por defecto debe ser el fork de richicru, no el upstream");
    }

    @Test
    void defaultBranchIsDev() {
        assertEquals("dev", Updater.DEFAULT_BRANCH);
    }

    @Test
    void forGitHubBuildsCorrectUrl() throws Exception {
        Updater updater = Updater.forGitHub(null, "3.0", "1.20.4",
                "richicru/40ServidoresMC", "dev");

        Field urlField = Updater.class.getDeclaredField("updateUrl");
        urlField.setAccessible(true);
        String url = (String) urlField.get(updater);

        assertEquals("https://raw.githubusercontent.com/richicru/40ServidoresMC/dev/etc/v3.json", url);
    }

    @Test
    void forGitHubStoresRepo() throws Exception {
        Updater updater = Updater.forGitHub(null, "3.0", "1.20.4",
                "my-org/my-fork", "main");

        Field repoField = Updater.class.getDeclaredField("repo");
        repoField.setAccessible(true);
        String repo = (String) repoField.get(updater);

        assertEquals("my-org/my-fork", repo);
    }

    @Test
    void urlConstructorExtractsRepo() throws Exception {
        Updater updater = new Updater(null, "3.0", "1.20.4",
                "https://raw.githubusercontent.com/owner/project/main/etc/v3.json");

        Field repoField = Updater.class.getDeclaredField("repo");
        repoField.setAccessible(true);
        String repo = (String) repoField.get(updater);

        assertEquals("owner/project", repo);
    }

    @Test
    void urlConstructorFallsBackToDefaultWhenUrlInvalid() throws Exception {
        Updater updater = new Updater(null, "3.0", "1.20.4", "not-a-github-url");

        Field repoField = Updater.class.getDeclaredField("repo");
        repoField.setAccessible(true);
        String repo = (String) repoField.get(updater);

        assertEquals(Updater.DEFAULT_REPO, repo);
    }

    @Test
    void urlConstructorFallsBackToDefaultWhenUrlNull() throws Exception {
        Updater updater = new Updater(null, "3.0", "1.20.4", null);

        Field repoField = Updater.class.getDeclaredField("repo");
        repoField.setAccessible(true);
        String repo = (String) repoField.get(updater);

        assertEquals(Updater.DEFAULT_REPO, repo);
    }
}
