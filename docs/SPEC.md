# Especificación técnica — 40ServidoresMC Plugin

> Documento de especificación generado a partir del código fuente en la rama `dev` (v3.0).

## 1. Resumen

40ServidoresMC es una web de rankings de servidores de Minecraft Online. Este plugin
conecta un servidor de Minecraft con la web [40servidoresmc.es](https://40servidoresmc.es)
para **recompensar a los jugadores que votan** al servidor y, así, mejorar su posición en
el ranking.

El proyecto es un plugin **multiplataforma**: una base de lógica común (`common`) compartida
entre varias implementaciones concretas por plataforma (Bukkit/Spigot y Sponge), pensada para
poder extenderse a BungeeCord y otras.

- **Autor:** Cadiducho
- **Versión actual:** 3.0
- **Licencia:** ver [LICENSE](../LICENSE)
- **Wiki:** https://github.com/Cadiducho/40ServidoresMC/wiki

## 2. Funcionalidad

1. **Validar voto y entregar recompensa** — Un jugador vota en la web y luego ejecuta el
   comando de voto en el servidor. El plugin consulta la API, comprueba si el voto es válido
   y, si lo es, entrega la recompensa (mensaje + comandos personalizados) y, opcionalmente,
   anuncia el voto a todo el servidor (broadcast).
2. **Estadísticas del servidor** — Muestra posición en el ranking, votos del día, votos
   semanales (totales y premiados) y los últimos 20 votos.
3. **Probar recompensas** — Permite a un administrador simular la entrega de premios sin
   votar realmente.
4. **Comprobación de actualizaciones** — Al arrancar y bajo demanda, comprueba contra un
   JSON en GitHub si existe una versión más reciente recomendada para la versión de Minecraft
   en uso.
5. **Recarga de configuración** en caliente.
6. **Integración con PlaceholderAPI** (en Bukkit, opcional / softdepend) — expone:
   `%40servidoresmc_position%`, `%40servidoresmc_day_votes%`,
   `%40servidoresmc_day_votes_rewarded%`, `%40servidoresmc_week_votes%`,
   `%40servidoresmc_week_votes_rewarded%`, `%40servidoresmc_server_name%`.
   Usa caché interna de 5 min.
7. **Métricas** vía bStats.
8. **Log opcional de IP** al validar voto (opt-in, off por defecto por privacidad).
9. **Mensajes traducibles** vía `messages.*` en la configuración.

## 2.1 Compatibilidad con servidores

Cobertura verificada según datos reales de la audiencia (ver [SERVERS.md](SERVERS.md)):

- **Paper / Purpur / Spigot / Pufferfish / Leaf** — soportados hoy (módulo `bukkit`).
- **Velocity / BungeeCord / Waterfall** — el plugin corre en el backend; 44% de la
  audiencia los usa como proxy. Sin cambios necesarios.
- **Sponge API 7** — soportado hoy (módulo `sponge/api7`).
- **Folia** — **plan pendiente** en [FOLIA.md](FOLIA.md). Requiere refactor de los
  callbacks asíncronos y de los usos de `BukkitScheduler`.

## 3. Arquitectura

### 3.1 Estructura de módulos (Gradle multi-proyecto)

```
40ServidoresMC/
├── common/          # Lógica compartida, independiente de plataforma
├── bukkit/          # Implementación Bukkit / Spigot / Glowstone (shadowJar)
└── sponge/
    └── api7/        # Implementación Sponge API 7 (shadowJar)
        (api8 y BungeeCord previstos pero no incluidos)
```

Definido en [settings.gradle](../settings.gradle). El módulo `sponge` raíz y `sponge:api8`
están comentados / no activos.

### 3.2 Patrón de diseño

El núcleo (`common`) define **interfaces de abstracción de plataforma** que cada
implementación concreta rellena. La lógica de comandos, API, recompensas y actualizaciones
vive una sola vez en `common`; las plataformas sólo aportan los "adaptadores".

Interfaces / abstracciones clave en `common`:

| Tipo | Rol |
|------|-----|
| [`CSPlugin`](../common/src/main/java/com/cadiducho/cservidoresmc/api/CSPlugin.java) | Contrato del plugin: logging, ejecución de comandos de consola, broadcast, acceso a config/API/updater. |
| [`CSConfiguration`](../common/src/main/java/com/cadiducho/cservidoresmc/config/CSConfiguration.java) | Abstracción de lectura de configuración (string/int/bool/list/map). |
| [`CSCommandSender`](../common/src/main/java/com/cadiducho/cservidoresmc/api/CSCommandSender.java) | Quien ejecuta un comando (jugador o consola): mensajes, permisos, nombre. |
| [`CSConsoleSender`](../common/src/main/java/com/cadiducho/cservidoresmc/api/CSConsoleSender.java) | Implementación común del sender de consola (siempre con permisos). |

Cada plataforma implementa `CSPlugin`, un adaptador de configuración y un `CSCommandSender`
propio que envuelve al sender nativo.

### 3.3 Flujo de arranque (ejemplo Bukkit)

Ver [`BukkitPlugin.onEnable()`](../bukkit/src/main/java/com/cadiducho/cservidoresmc/bukkit/BukkitPlugin.java):

1. Cargar/crear `config.yml` mediante `BukkitConfigurationAdapter`.
2. Instanciar `ApiClient` (con `Gson`).
3. Registrar comandos creando `CSCommandManager`.
4. Registrar la extensión de PlaceholderAPI si el plugin está presente.
5. Inicializar métricas bStats (plugin id `3909`).
6. Crear `Updater` y comprobar nuevas versiones.
7. Validar la clave/versión de configuración (`checkDefaultKey()`).

## 4. Componentes del módulo `common`

### 4.1 Cliente de API — [`ApiClient`](../common/src/main/java/com/cadiducho/cservidoresmc/ApiClient.java)

- Endpoint base: `https://40servidoresmc.es/api2.php?clave=<clave>`
- Peticiones HTTP GET asíncronas mediante `CompletableFuture.supplyAsync`.
- Respuestas JSON deserializadas con Gson.
- `readTimeout` configurable (`readTimeOut`).

| Operación | Parámetros | Respuesta |
|-----------|-----------|-----------|
| `validateVote(player)` | `&nombre=<jugador>` | `VoteResponse` |
| `fetchServerStats()` | `&estadisticas=1` | `ServerStats` |

### 4.2 Modelos ([`model/`](../common/src/main/java/com/cadiducho/cservidoresmc/model/))

- **`VoteResponse`** — `web` (URL de la web) + `status` (`VoteStatus`).
- **`VoteStatus`** (enum, mapeado por código de la API):
  - `0` → `NOT_VOTED` (no ha votado hoy)
  - `1` → `SUCCESS` (voto válido, entregar premio)
  - `2` → `ALREADY_VOTED` (ya recibió el premio)
  - `3` → `INVALID_KEY` (clave incorrecta)
- **`ServerStats`** — `nombre`, `puesto`, votos del día/semanales (totales y premiados) y
  `ultimos20votos` (lista de `ServerVote`).
- **`ServerVote`** — `usuario` + flag `recompensado`.
- **`updater/UpdaterInfo`** — mapas `pluginVersions` y `minecraftVersions`; resuelve la
  versión recomendada para una versión de Minecraft.

### 4.3 Sistema de comandos ([`cmd/`](../common/src/main/java/com/cadiducho/cservidoresmc/cmd/))

- **`CSCommand`** (abstracto) — nombre, permiso, alias, descripción, ayuda; método
  `execute(...)` que devuelve un `CommandResult` (`SUCCESS`, `COOLDOWN`, `NO_PERMISSION`,
  `ONLY_PLAYER`, `ERROR`). Incluye stub para tab-complete (pendiente).
- **`CSCommandManager`** — registra todos los comandos y sus alias, resuelve permisos y
  traduce `CommandResult` a mensajes para el usuario.

Comandos registrados:

| Comando | Alias | Permiso | Función |
|---------|-------|---------|---------|
| `voto40` | `votar40`, `vote40`, `mivoto40` | `40servidores.voto` | Valida el voto y entrega la recompensa. Cooldown 60s, sólo jugadores. |
| `stats40` | — | `40servidores.stats` | Muestra estadísticas del servidor. |
| `test40` | — | `40servidores.test` | Simula la entrega de recompensas. Sólo jugadores. |
| `update40` | `actualizar40`, `upd40` | `40servidores.actualizar` | Comprueba actualizaciones. |
| `reload40` | `recargar40`, `config40` | `40servidores.recargar` | Recarga la configuración. |

> Nota: en [plugin.yml](../bukkit/src/main/resources/plugin.yml) los comandos se registran
> como `vote40`, `update40`, `reload40`, `stats40`, `test40` con sus alias.

### 4.4 Lógica de voto — [`VoteCMD`](../common/src/main/java/com/cadiducho/cservidoresmc/cmd/VoteCMD.java)

1. Rechaza ejecución desde consola y aplica cooldown de 60s por jugador.
2. Llama a `ApiClient.validateVote(nombre)` de forma asíncrona.
3. Según `VoteStatus`:
   - `SUCCESS` → envía `mensaje`, ejecuta cada comando de `comandosCustom` (sustituyendo
     `{0}` por el nombre del jugador) y, si `broadcast.activado`, anuncia el voto.
   - `NOT_VOTED` → enlace a la web para votar.
   - `ALREADY_VOTED` / `INVALID_KEY` / error → mensaje correspondiente.

### 4.5 Utilidades

- **[`Cooldown`](../common/src/main/java/com/cadiducho/cservidoresmc/Cooldown.java)** — cooldown
  por nombre de jugador con expiración temporal (mapa en memoria).
- **[`Updater`](../common/src/main/java/com/cadiducho/cservidoresmc/Updater.java)** — descarga
  `etc/v3.json` desde GitHub (`raw.githubusercontent.com/.../dev/etc/v3.json`),
  compara la versión recomendada para la versión de Minecraft instalada y avisa si hay una
  más reciente. *(Pendiente: el branch está fijado a `dev`.)*

## 5. Implementaciones por plataforma

### 5.1 Bukkit / Spigot / Glowstone ([`bukkit/`](../bukkit/))

- [`BukkitPlugin`](../bukkit/src/main/java/com/cadiducho/cservidoresmc/bukkit/BukkitPlugin.java)
  extiende `JavaPlugin` e implementa `CSPlugin`.
- `BukkitConfigurationAdapter` — adaptador de `config.yml`.
- `BukkitCommandSender` — envoltorio del `CommandSender` de Bukkit.
- `PlaceholderHook` — extensión de PlaceholderAPI (identificador `40servidoresmc`; sin
  placeholders implementados todavía).
- `dispatchCommand` se ejecuta de forma síncrona vía el scheduler; `broadcastMessage`
  traduce códigos de color `&`.
- Dependencias: Spigot API 1.16.5, PlaceholderAPI 2.10.9 (compileOnly), bStats 3.0.0.
- Empaquetado: **shadowJar** con relocation de `org.bstats` y `ninja.leaping.configurate`.
- `api-version: 1.13`.

### 5.2 Sponge API 7 ([`sponge/api7/`](../sponge/api7/))

- `SpongePlugin`, `SpongeConfigAdapter`, `SpongeCommandExecutor`, `SpongeCommandSender`.
- Dependencias: SpongeAPI 7.4.0, bStats-sponge 2.2.1.
- Empaquetado: shadowJar con clasificador `Sponge-API7`.

## 6. Configuración

Claves de configuración (ver [config.yml](../bukkit/src/main/resources/config.yml) /
[40ServidoresMC.conf](../common/src/main/resources/40ServidoresMC.conf)):

| Clave | Tipo | Descripción |
|-------|------|-------------|
| `debug` | bool | Activa logs de depuración. |
| `clave` | string | Clave del servidor obtenida en la web (por defecto `key`, inválida). |
| `broadcast.activado` | bool | Anunciar votos a todo el servidor. |
| `broadcast.mensajeBroadcast` | string | Mensaje de anuncio (`{0}` = jugador). |
| `mensaje` | string | Mensaje al recibir el premio. |
| `comandosCustom` | lista | Comandos a ejecutar como recompensa (`{0}` = jugador). |
| `tag` | string | Prefijo/tag del plugin en los mensajes. |
| `readTimeOut` | int | Timeout de lectura HTTP (ms). |
| `cooldown` | int | Segundos entre validaciones de voto por jugador. Default: `60`. |
| `update-branch` | string | Rama de GitHub a comprobar para actualizaciones. Default: `dev`. |
| `log-ip` | bool | Si está activo, registra en consola `[VoteReward] player=X ip=Y` tras cada voto válido. Default: `false` (privacidad). |
| `messages.*` | mapa | Mensajes traducibles. Ver [§6.1](#61-mensajes-traducibles). |
| `configVer` | int | Versión de la configuración (actual: `4`). |

Validaciones al arrancar (`checkDefaultKey`):
- Avisa si `configVer` no coincide con la versión esperada (`4`).
- Avisa si `clave` sigue siendo `key` (no configurada) — el plugin no funcionará.

> El módulo `common` usa formato HOCON (`.conf`) vía Configurate; Bukkit usa `config.yml`.

### 6.1 Mensajes traducibles

Todos los textos mostrados al usuario se pueden sobrescribir bajo la clave `messages.*`.
Cualquier clave omitida usa el valor por defecto en español. Ejemplo de traducción al inglés:

```yaml
messages:
  vote-already-rewarded: "&aThanks for voting, but you already got your reward!"
  cmd-no-permission: "&cYou don't have permission to use this command"
  stats-header: "&9==> &7{server} &fis at TOP &a#{position}"
```

Los placeholders disponibles son `{server}`, `{position}`, `{count}`, `{votes}`, `{version}`, `{player}` según el mensaje.

## 7. Build y dependencias

- **Sistema de build:** Gradle (wrapper incluido). Multi-proyecto con `subprojects` comunes
  en [build.gradle](../build.gradle).
- **Java:** source/target 1.8, encoding UTF-8.
- **Lombok** (plugin freefair 6.4.3) — anotaciones `@Data`, `@Getter`, `@RequiredArgsConstructor`, etc.
- **Gson 2.9.0** — serialización JSON.
- **Guava 23.0**.
- **Configurate 3.7.2** (core, yaml, gson, hocon) — gestión de configuración en `common`.
- **bStats** — métricas.
- **shadow 7.1.0** — fat-jar con relocation de librerías por plataforma.
- **Mockito 5.7.0** (`testImplementation` en `common`) — mocks para tests de comandos.

Artefactos generados (shadowJar): `40ServidoresMC-<version>-Bukkit.jar`,
`40ServidoresMC-<version>-Sponge-API7.jar`.

### Compilar

```bash
./gradlew build              # Compila todos los módulos
./gradlew :bukkit:shadowJar  # Jar de Bukkit
./gradlew :sponge:api7:shadowJar
./gradlew :common:test       # Tests del núcleo (JUnit 5)
```

## 8. Pruebas

Los tests unitarios viven en `common/src/test/java/com/cadiducho/cservidoresmc/` y usan
**JUnit 5 (Jupiter)**. Se ejecutan con `./gradlew :common:test`.

### 8.1 Inventario de tests (49 tests, todos en verde)

| Clase | Tests | Cubre |
|---|---:|---|
| [`TestUpdater`](../common/src/test/java/com/cadiducho/cservidoresmc/TestUpdater.java) | 1 | Parsing de `v3.json` desde GitHub. |
| [`TestUpdaterInfo`](../common/src/test/java/com/cadiducho/cservidoresmc/TestUpdaterInfo.java) | 6 | Mapeo Minecraft → versión recomendada; casos vacíos, desconocidos, múltiples. |
| [`TestVoteStatus`](../common/src/test/java/com/cadiducho/cservidoresmc/TestVoteStatus.java) | 8 | Deserialización Gson del enum `VoteStatus` (`0`–`3`) y de `VoteResponse` completo. |
| [`TestServerStats`](../common/src/test/java/com/cadiducho/cservidoresmc/TestServerStats.java) | 7 | Parsing de `ServerStats` con lista de votos llena, vacía y nula; bug conocido `StringIndexOutOfBoundsException` documentado. |
| [`TestCSCommand`](../common/src/test/java/com/cadiducho/cservidoresmc/TestCSCommand.java) | 7 | Lógica de autorización, metadatos, tab-complete stub y `CommandResult` enum. |
| [`TestCooldown`](../common/src/test/java/com/cadiducho/cservidoresmc/TestCooldown.java) | 6 | Cooldown por jugador, expiración, aislamiento entre jugadores. |
| [`TestApiClient`](../common/src/test/java/com/cadiducho/cservidoresmc/TestApiClient.java) | 6 | HTTP contra servidor local (`com.sun.net.httpserver`): éxito, error 500, JSON malformado, lectura de la clave desde config. |
| [`TestVoteCMD`](../common/src/test/java/com/cadiducho/cservidoresmc/cmd/TestVoteCMD.java) | 8 | Flujo completo de `/voto40` con Mockito: SUCCESS, NOT_VOTED, ALREADY_VOTED, INVALID_KEY, cooldown, broadcast, excepciones. |

### 8.2 Helpers de tests

- [`TestSupport`](../common/src/test/java/com/cadiducho/cservidoresmc/TestSupport.java) —
  mocks en memoria: `MockPlugin` (CSPlugin), `MockConfiguration` (CSConfiguration),
  `MockCommandSender` (CSCommandSender). Permiten probar comandos sin levantar un servidor
  Bukkit/Sponge real.
- **Mockito 5.7.0** — usado en `TestVoteCMD` para mockear `ApiClient` y `CSPlugin`.

### 8.3 Áreas NO cubiertas aún

- Integración contra servidor real (Paper/Folia/Sponge). Pendiente de configurar Docker
  Compose (ver [SERVERS.md](SERVERS.md)).
- `Updater.checkearVersion` con mock del endpoint HTTP (hoy solo se prueba el parsing).
- PlaceholderAPI placeholders reales.
- `ReloadCMD`, `StatsCMD`, `UpdateCMD`, `TestCMD` (similar a `VoteCMD` cuando se quiera).



## 9. Recursos externos

| Recurso | Uso |
|---------|-----|
| `https://40servidoresmc.es/api2.php` | API de votos y estadísticas. |
| `https://40servidoresmc.es/miservidor.php` | Panel del servidor (obtener la clave). |
| `raw.githubusercontent.com/richicru/40ServidoresMC/dev/etc/v3.json` | Datos de versiones para el Updater. |
| `https://github.com/Cadiducho/40ServidoresMC/releases` | Descargas/changelog de releases. |
| bStats (id `3909`) | Métricas de uso. |

## 10. Trabajo pendiente / conocido (TODOs en el código)

- Tab-complete de comandos (`CSCommand.tabCompleteCommand` es un stub).
- Branch del Updater fijado a `dev` (ver TODO en `Updater.fetchUpdate`).
- PlaceholderAPI: hook registrado pero sin placeholders implementados.
- Soporte de Sponge API 8 y BungeeCord previsto (módulos comentados en `settings.gradle`).
