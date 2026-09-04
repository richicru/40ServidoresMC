# Compatibilidad con Folia

> **Estado actual**: ✅ **Implementado y validado en v3.0.2**
> sobre Paper 1.20.4 + Folia 1.20.4 en Docker.
> Entorno de test reproducible: ver [docs/testing/Local-Test-Setup.md](testing/Local-Test-Setup.md).

## Qué se hizo

Folia divide el mundo en regiones que tickean en paralelo. No existe un "main thread" global del servidor: cada región corre en su propio hilo, y sólo se puede acceder a la API de Bukkit/Paper desde el hilo propietario de la región (o un scheduler apropiado).

### 1. Abstracción de scheduler en `common`

En `common/src/main/java/.../api/CSPlugin.java` se añadieron tres métodos (con
implementaciones `default` razonables para Sponge):

```java
void runSyncForPlayer(String playerName, Runnable task);  // action -> EntityScheduler (Folia) o main thread
void runSyncGlobal(Runnable task);                       // action -> GlobalRegionScheduler (Folia) o main thread
void runForEachOnlinePlayer(Consumer<CSCommandSender>);  // iterate, route per EntityScheduler en Folia
```

El módulo `bukkit` los sobrescribe (en `BukkitPlugin.java`) con la lógica
correcta para Folia. El módulo `sponge/api7` hereda el `default`, que ejecuta
el `Runnable` directamente — válido para el modelo de Sponge (no regionizado).

### 2. Detección de Folia

`BukkitPlugin` detecta Folia una sola vez en `onEnable` usando
`FoliaDetector.isFoliaServer()` → `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`.

### 3. `dispatchCommand`, `broadcastMessage`, `BukkitCommandSender`

Reescritos para enrutarse por el scheduler apropiado. En Folia, broadcast
recorre `Bukkit.getOnlinePlayers()` y agenda cada `Player#sendMessage(...)`
en su `EntityScheduler`.

### 4. Callbacks async envueltos

`VoteCMD`, `StatsCMD` y `Updater.checkearVersion` ahora envuelven cada
`thenAccept(...)` que escribe en la API de Bukkit dentro de
`runSyncForPlayer(...)` / `runSyncGlobal(...)`. Antes los callbacks se
ejecutaban en `ForkJoinPool`/`cservidoresmc-io` y la API lanzaba
`IllegalStateException: not on region thread` al primer comando.

### 5. plugin.yml

Añadido `folia-supported: true` (sin él, Folia rechaza el plugin).

### 6. `bukkit/build.gradle`

Se queda con `org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT` como `compileOnly`.
Todas las APIs de Folia se acceden por reflection para no requerir Paper API
1.20+ (que necesita Java 17 mínimo en compilación).

---

## Cómo probarlo localmente

```bash
# Una vez: pull de la imagen, ya está cacheada tras la primera ejecución
./scripts/test-servers-up.sh          # levanta Paper y Folia

# Build del plugin y copiar a plugins/
./scripts/install-plugin.sh

# Volver a validar arranque
./scripts/test-validate.sh

# Limpieza
./scripts/test-servers-down.sh
```

Resultado esperado en logs:

- Paper  → `Plugin 40ServidoresMC v3.0.2 cargado completamente`
- Folia  → `Folia detectado: usando schedulers region-aware.`
            seguido de `Plugin 40ServidoresMC v3.0.2 cargado completamente`

Y no debe aparecer:

- `Could not load plugin '...' as it is not marked as supporting Folia!`
- `IllegalStateException: ... not on region thread`

---

## Compatibilidad con Sponge

No se ve afectada. `SpongePlugin` hereda el `default runSyncGlobal` (ejecuta
directamente), que es válido en el modelo no-regionizado de Sponge 7.

---

## Detalles por archivo

| Archivo | Cambio |
| --- | --- |
| `common/.../api/CSPlugin.java` | +3 métodos default (`runSyncForPlayer`, `runSyncGlobal`, `runForEachOnlinePlayer`). |
| `common/.../cmd/VoteCMD.java` | Envoltorio de `thenAccept` en `runSyncForPlayer` (extraído a `handleVoteResponse`). |
| `common/.../cmd/StatsCMD.java` | Envoltorio de `thenAccept` en `runSyncGlobal` (extraído a `handleStatsResponse`). |
| `common/.../Updater.java` | `checkearVersion` enruta el callback de la API al scheduler global. |
| `common/.../Cooldown.java` | Sin cambios (ya era `ConcurrentHashMap` desde v3.0). |
| `bukkit/.../BukkitPlugin.java` | Detección Folia, nuevos métodos, `dispatchCommand`/`broadcastMessage` Folia-aware. |
| `bukkit/.../FoliaDetector.java` | Nuevo: detector liviano con `Class.forName`. |
| `bukkit/.../bukkit/BukkitCommandSender.java` | Sin cambios (sólo se invoca desde el scheduler correcto). |
| `bukkit/build.gradle` | Sin cambios (Spigot API + reflection para Folia). |
| `bukkit/src/main/resources/plugin.yml` | +`folia-supported: true`. |
| `sponge/api7/.../SpongePlugin.java` | Sin cambios (hereda `default`). |

---

## Plan original (referencia histórica)

A continuación el plan completo original para entender el camino recorrido.
Fases y código de ejemplo del plan ya ejecutado, mantenido para referencia.

### Estado de partida (previo a v3.0.2)

Plugin esencialmente "main-thread friendly":

- No hay listeners Bukkit registrados.
- No hay tareas repetitivas (`runTaskTimer`).
- Toda la red de I/O HTTP se hace con `CompletableFuture.supplyAsync(...)` sobre `ForkJoinPool.commonPool()` (`ApiClient.java:31-49`, `Updater.java:82-97`).
- Los callbacks `thenAccept(...)` de esas futures se ejecutan en hilos del ForkJoinPool, **fuera del hilo principal**, y ya llaman directamente a la API de Bukkit.
- Sólo hay dos usos del scheduler de Bukkit, ambos en `BukkitPlugin.java`:
  - `dispatchCommand` → `callSyncMethod` (línea 127).
  - `broadcastMessage` → `runTask` + iteración de `getOnlinePlayers()` (líneas 131-135).

### Sitios incompatibles con Folia

| # | Archivo:línea | Problema |
|---|---|---|
| 1 | `VoteCMD.java:38-66` | Callback `thenAccept` corre en ForkJoinPool; llama `sender.sendMessage`, `plugin.dispatchCommand`, `plugin.broadcastMessage` desde hilo ajeno a la región del jugador. |
| 2 | `BukkitCommandSender.java:23-26` | `commandSender.spigot().sendMessage(...)` requiere el hilo del jugador en Folia. |
| 3 | `BukkitPlugin.java:127` | `BukkitScheduler.callSyncMethod` está prohibido en Folia. |
| 4 | `BukkitPlugin.java:131-135` | `getOnlinePlayers().forEach(p -> p.sendMessage(...))` viola el modelo de regiones. |
| 5 | `StatsCMD.java:25-50` | Callback async llama `sendMessageWithTag` directamente. |
| 6 | `Updater.java:58-79` | Callback async llama `sender.sendMessage` desde ForkJoinPool. |
| 7 | `Cooldown.java:8` | `HashMap` no thread-safe (riesgo si se accede desde callbacks async). |

---

## Estrategia de compatibilidad

Folia expone, vía Paper API, los siguientes schedulers:

- `Bukkit.getGlobalRegionScheduler()` — único. Dueño de datos globales (gamerules, tiempo, ejecución de comandos de consola).
- `Bukkit.getAsyncScheduler()` — para tareas async (no toca la API de Bukkit).
- `player.getScheduler()` (EntityScheduler) — corre en la región dueña del jugador.
- `location.getScheduler()` no existe; hay que usar `Bukkit.getRegionScheduler()` (no siempre presente en compilaciones antiguas).

Los métodos Folia son `default`/nuevos en Paper API. Para mantener compatibilidad con Spigot puro (que no los tiene), se hará **detección en runtime** y shim con `BukkitScheduler` clásico.

### Principios del refactor

1. **Toda interacción con Bukkit desde código async debe pasar por el `CSPlugin` (abstracción de plataforma)**, no por llamadas directas a `Bukkit`.
2. **El módulo `common` no debe depender nunca de Bukkit/Paper** — ya está bien abstraído.
3. **La detección Folia / Paper / Spigot se hace una sola vez en `onEnable`** y se cachea en `BukkitPlugin`.
4. **No usar reflection** — los métodos de Folia son `default` en `Server` (Paper) y se pueden llamar directamente cuando la clase está; si no, fallback.

---

## Plan de implementación por fases

### Fase 1 — Base: abstraer scheduler en `common`

**Objetivo:** introducir dos métodos en `CSPlugin` que el módulo `bukkit` implemente.

Añadir a `common/.../api/CSPlugin.java`:

```java
/**
 * Ejecutar una acción en el contexto de Bukkit/Paper de forma segura para Folia.
 * Si el emisor es un jugador, se agenda en su EntityScheduler.
 * Si es consola o no hay jugador, se agenda en GlobalRegionScheduler (Folia) o main thread (clásico).
 */
void runSyncForPlayer(String playerName, Runnable task);

/**
 * Ejecutar una acción de consola/comando (no asociado a un jugador).
 * En Folia: GlobalRegionScheduler. En clásico: main thread scheduler.
 */
void runSyncGlobal(Runnable task);

/**
 * Ejecutar una acción tocando a TODOS los jugadores conectados (broadcast, etc.).
 * Implementación por plataforma: en Folia se agenda en cada EntityScheduler.
 */
void runForEachOnlinePlayer(java.util.function.Consumer<CSCommandSender> action);
```

No romper `dispatchCommand` ni `broadcastMessage` existentes: simplemente reescribirlos por dentro en `BukkitPlugin` para delegar en estos nuevos métodos.

### Fase 2 — Implementación Bukkit con detección Folia

En `BukkitPlugin.java`:

1. Añadir campo `private final boolean folia;`.
2. En `onEnable`, detectar:
   ```java
   this.folia = tryClass("io.papermc.paper.threadedregions.RegionizedServer");
   ```
   (Más sencillo y robusto que mirar el nombre de la clase del server.)
3. Reescribir `dispatchCommand(String command)` para usar el scheduler apropiado:
   - Folia: `Bukkit.getGlobalRegionScheduler().run(plugin, t -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));`
   - Clásico: `Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));`
4. Reescribir `broadcastMessage(String message)`:
   - Folia: `Bukkit.getGlobalRegionScheduler().run(plugin, t -> Bukkit.getOnlinePlayers().forEach(p -> p.getScheduler().run(plugin, null, () -> p.sendMessage(colorize(msg)), null)));`
   - Clásico: `Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(colorize(msg))));`
5. Implementar `runSyncForPlayer`, `runSyncGlobal`, `runForEachOnlinePlayer` con la misma lógica condicional.

> Nota: la firma exacta de `EntityScheduler.run(Plugin, Consumer, Runnable, Consumer)` cambió entre versiones de Paper; revisar la API de la versión mínima soportada y elegir la firma adecuada (probablemente `run(Plugin, Consumer, Runnable)` con `null` para cancelados).

### Fase 3 — Envolver los callbacks async

En `common/.../cmd/VoteCMD.java` (línea 38), `StatsCMD.java:25` y `Updater.java:58`:

Sustituir las llamadas directas a `sender.sendMessage(...)` dentro de `thenAccept(...)` por invocaciones al nuevo método:

```java
apiClient.validateVote(sender.getName()).thenAccept(response -> {
    plugin.runSyncForPlayer(sender.getName(), () -> handleVoteResponse(response));
});
```

Donde `handleVoteResponse` contiene la lógica actual (`switch` sobre `VoteStatus`). El `switch` puede seguir haciendo `sender.sendMessageWithTag(...)` y `plugin.dispatchCommand(...)` / `plugin.broadcastMessage(...)` **porque ahora correrán en el hilo correcto**.

El `exceptionally` (línea 67) debe ir por `runSyncGlobal` o, si conoce al jugador, por `runSyncForPlayer`.

### Fase 4 — `Cooldown` thread-safe

Migrar `Cooldown.java:8` a `ConcurrentHashMap<String, Long>` (ya estaba propuesto en `docs/MEJORAS.md:32-34`). No es estrictamente obligatorio para Folia — los accesos actuales son todos main-thread — pero elimina un riesgo latente.

### Fase 5 — `BukkitCommandSender.sendMessage`

El wrapper (`BukkitCommandSender.java:23-26`) llama `commandSender.spigot().sendMessage(...)`. Como los nuevos métodos de `CSPlugin` ya garantizan que el envío ocurre en el scheduler correcto, **no hace falta tocar esta clase**. Pero conviene documentar con un comentario que sólo debe invocarse desde el scheduler apropiado.

### Fase 6 — Build & dependencias

`bukkit/build.gradle`:

- Mantener `spigot-api:1.16.5-R0.1-SNAPSHOT` como `compileOnly` (compat con Spigot 1.16.5).
- **Añadir** `compileOnly 'io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT'` (o la versión que decida el mantenedor) para que el código que usa `GlobalRegionScheduler` / `EntityScheduler` **compile**.
- En runtime, los métodos nuevos son `default`/`null` en Paper pero **inexistentes** en Spigot puro. Por eso la detección runtime (Fase 2, paso 2) es esencial.
- Alternativa más limpia: usar `paperweight-folia` / `paperweight-userdev` para ofuscar, pero ese setup complica el build actual basado en Spigot. **Se recomienda el enfoque de doble `compileOnly` + detección runtime.**

### Fase 7 — Verificación

Checklist antes de cerrar:

- [ ] Compila contra Spigot 1.16.5 y contra Paper 1.20+.
- [ ] El `.jar` actual sigue funcionando idéntico en Spigot / Paper clásico.
- [ ] El `.jar` funciona en Folia sin lanzar `IllegalStateException` ni `java.lang.IllegalArgumentException: ... not owned by current region`.
- [ ] `/voto40` con voto correcto: mensaje al jugador, comandos custom ejecutados, broadcast recibido por todos.
- [ ] `/voto40` con voto no realizado: link clickable.
- [ ] `/stats40` muestra datos sin error.
- [ ] `/test40` ejecuta comandos dry-run.
- [ ] `/update40` informa de versión sin error.
- [ ] El cooldown (60 s) no se rompe entre votaciones rápidas.
- [ ] No aparecen warnings de Folia sobre "blocking operation" en consola.

---

## Compatibilidad con Sponge

**No se ve afectada.** El módulo `sponge/api7` corre sobre Sponge API 7, que no es Folia y sigue su propio modelo de threading (no regionizado). Su `dispatchCommand` y `broadcastMessage` (`SpongePlugin.java:131-142`) siguen siendo válidos.

Únicamente hay que confirmar que las nuevas firmas de `CSPlugin` (`runSyncForPlayer`, `runSyncGlobal`, `runForEachOnlinePlayer`) tengan implementaciones `default` razonables en la interfaz o se implementen también en `SpongePlugin`. **Recomendación: implementarlas como `default` que ejecutan el `Runnable` directamente** (los comandos Sponge ya corren en su hilo correcto), y dejar que `BukkitPlugin` las sobreescriba con la lógica condicional.

---

## Riesgos y notas

1. **Reflection-free detection:** se prefiere detectar Folia por presencia de la clase `io.papermc.paper.threadedregions.RegionizedServer` o por `Bukkit.getServer().getClass().getName().contains("Folia")` en lugar de `instanceof`, porque los métodos Folia son `default` y siempre "están" en compile-time, pero pueden no existir en runtime.

2. **Paper API mínimo:** los métodos `getGlobalRegionScheduler` y `EntityScheduler.run` aparecieron en Paper **1.19.4+**. Si se decide soportar Paper 1.16-1.18 habría que añadir polyfills con `BukkitScheduler` clásico como fallback. **Recomendación: soportar Paper 1.19.4+ para Folia; Spigot y Paper antiguos siguen funcionando porque la detección runtime cae al camino clásico.**

3. **Versiones de Spigot-API en build:** hoy `bukkit/build.gradle` declara `1.16.5-R0.1-SNAPSHOT`. Si se añade `paper-api` reciente, hay un conflicto potencial de paquetes en compileOnly. **Recomendación: subir `spigot-api` a 1.19.4+ y mantener `paper-api` 1.20+ como segundo `compileOnly`.** Documentar este cambio en `plugin.yml` (`api-version: 1.19`).

4. **`Bukkit.broadcastMessage` y `Bukkit.getOnlinePlayers`:** ya no se usan directamente. El plugin itera `getOnlinePlayers()` él mismo dentro de un scheduler; eso es correcto.

5. **No usar `CompletableFuture.supplyAsync` sin executor.** El plan asume que en algún punto se cambiará a un executor dedicado (lo sugería `docs/MEJORAS.md`). No es bloqueante para Folia, pero conviene hacerlo en la misma PR.

---

## Resumen de cambios por archivo

| Archivo | Cambio |
|---|---|
| `common/.../api/CSPlugin.java` | +3 métodos abstractos/default (`runSyncForPlayer`, `runSyncGlobal`, `runForEachOnlinePlayer`). |
| `common/.../cmd/VoteCMD.java` | Envolver callback async con `runSyncForPlayer`. |
| `common/.../cmd/StatsCMD.java` | Envolver callback async con `runSyncForPlayer`. |
| `common/.../Updater.java` | Envolver callback async con `runSyncGlobal`/`runSyncForPlayer`. |
| `common/.../Cooldown.java` | `HashMap` → `ConcurrentHashMap`. |
| `bukkit/.../BukkitPlugin.java` | Detección Folia, reescritura de `dispatchCommand`/`broadcastMessage`, implementación de los 3 métodos nuevos. |
| `bukkit/build.gradle` | Añadir `paper-api` 1.19.4+ como `compileOnly`. |
| `bukkit/src/main/resources/plugin.yml` | Subir `api-version: 1.19` (si se sube la API mínima). |
| `sponge/api7/.../SpongePlugin.java` | Implementar los 3 métodos nuevos (o usar `default` en `CSPlugin`). |

Estimación de complejidad: media. El refactor está bien acotado (≈8 archivos, la mayoría cambios pequeños). El mayor reto es la compilación dual Spigot/Paper y los tests manuales en servidor Folia real.
