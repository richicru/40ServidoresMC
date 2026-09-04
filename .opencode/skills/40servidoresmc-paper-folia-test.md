---
name: 40servidoresmc-paper-folia-test
description: This skill should be used when the user works on or tests the 40ServidoresMC Minecraft plugin in this repository. It documents the dual Paper / Folia testing harness using docker-compose, the runtime Folia detection pattern, and the area-aware scheduler abstraction in CSPlugin. Triggers when the user says "test Paper", "test Folia", "docker compose", "Folia compat", "run the plugin in Docker", or asks about plugin's `bukkit/` module scheduler logic.
---

# 40ServidoresMC — Testing Paper & Folia

This repository publishes a Minecraft plugin under `bukkit/` (and `sponge/api7/`).
The `bukkit/` module supports **Paper** (the classic Bukkit/Spigot/Paper stack) **and
Folia** (Paper's multithreaded regionized fork) using a single JAR.

## Where things live

- Production code:
  - `common/src/main/java/.../api/CSPlugin.java` — defines the 3 cross-platform
    scheduler methods (`runSyncForPlayer`, `runSyncGlobal`, `runForEachOnlinePlayer`)
    consumed by the common module's commands.
  - `bukkit/src/main/java/.../BukkitPlugin.java` — implements them with Folia vs
    classic branching.
  - `bukkit/src/main/java/.../BukkitCommandSender.java` — wraps a Bukkit
    `CommandSender` for the common module.
- Testing infrastructure:
  - `docker-compose.test.yml` — defines `cs-test-paper` (PORT 25565) and
    `cs-test-folia` (PORT 25566).
  - `scripts/install-plugin.sh` — builds the JAR with shadow and copies it into
    both containers' `plugins/` directories.
  - `scripts/test-servers-up.sh` / `-down.sh` / `test-validate.sh` / `test-run.sh`
    are the entry points users expect.
  - `docs/testing/Local-Test-Setup.md` — full guide.

## Folia detection pattern

In `BukkitPlugin.onEnable`:

```java
this.folia = tryClass("io.papermc.paper.threadedregions.RegionizedServer");
```

Where `tryClass(String) -> boolean` is a helper that wraps `Class.forName`. This is
the runtime check used in this codebase; do not replace with `instanceof` or
`Bukkit.getServer().getClass().getName().contains("Folia")` (both have false positives
in synthetic test contexts).

After the check, **all** scheduler branching uses the boolean flag `this.folia`:

```java
public void runSyncForPlayer(String playerName, Runnable task) {
    Player p = getServer().getPlayerExact(playerName);
    if (p == null) { task.run(); return; }     // offline: caller decides
    if (folia) {
        p.getScheduler().execute(this, null, task, null);   // Folia entity scheduler
    } else {
        getServer().getScheduler().runTask(this, task);     // classic main thread
    }
}
```

## Async → Sync wrapping

Any callback that touches Bukkit API **must** go through one of the three schedulers:

| Method                                 | Use case                                          | Folia target                             |
| -------------------------------------- | ------------------------------------------------- | ---------------------------------------- |
| `runSyncForPlayer(name, Runnable)`     | Action that targets one player (vote confirmation) | `player.getScheduler().execute(...)`     |
| `runSyncGlobal(Runnable)`              | Console commands, broadcasts to all, updater msgs  | `Bukkit.getGlobalRegionScheduler().run()`|
| `runForEachOnlinePlayer(Consumer<...>)`| Broadcast                                       | iterate, send each to its `EntityScheduler` |

**Do not call** `sender.sendMessage(...)` / `player.sendMessage(...)` / `Bukkit.dispatchCommand(...)`
directly from `thenAccept(...)` in `common/cmd/`. Wrap them in `runSyncForPlayer` /
`runSyncGlobal` first.

## Build dependencies

`bukkit/build.gradle` declares `compileOnly 'io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT'`.
This supersedes `spigot-api`. To add new Paper/Folia API calls, import directly:

```java
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
```

If `Bukkit.getGlobalRegionScheduler()` ever returns null (older Paper, no Folia),
fall back to the classic path.

## Testing workflow

After any code change that touches scheduling, the user expects:

```bash
./scripts/test-run.sh --folia   # quick sanity check on Folia alone
./scripts/test-run.sh           # full Paper + Folia validation
```

The `test-validate.sh` exit code is meaningful: 0 means done, 1 means something
failed (look at `/tmp/cs-test-{paper,folia}.test.log`). The script asserts:

1. Server reached `Done (X.X s)`.
2. Plugin logged `40ServidoresMC vX.X.X cargado completamente`.
3. No `java.lang.*Exception` or `Caused by` in logs.
4. No `NoSuchMethodError` (means API method exists at compile time but not at runtime).

## Common pitfalls

- **Modifying `common/cmd/*` to call `sender.sendMessage` from an async callback**:
  will silently fail on Folia (`IllegalStateException: not on region thread`).
- **Replacing `Bukkit.getScheduler()` with reflection**: unnecessary; the runtime
  branch on `this.folia` is the agreed pattern.
- **Touching `BukkitCommandSender.sendMessage`**: not needed. The wrapping happens
  one level up at `runSyncForPlayer`. Document with a comment if you must.
