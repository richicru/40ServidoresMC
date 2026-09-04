# Changelog

Todos los cambios relevantes del plugin se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.1] - 2026-09-04

### Corregido (latente)
- **Gson empaquetado en el JAR**: Gson pasaba como `compileOnly` en `common`,
  por lo que el JAR de Bukkit dependía de que el servidor lo proveyera en runtime.
  Spigot/Paper modernos no incluyen Gson en su classpath, lo que produciría un
  `NoClassDefFoundError: com/google/gson/...` en servidores sin Gson adicional.
  - Movido a `api` en `common` (visible transitivamente a Bukkit/Sponge).
  - Relocado a `com.cadiducho.cservidoresmc.lib.gson` en ambos shadowJar,
    evitando choques con cualquier Gson que el servidor pueda tener.
  - El JAR queda ahora completamente autocontenido (no requiere Gson externo).

### Añadido
- **Circuit breaker** en `ApiClient`: tras 3 fallos consecutivos abre el circuito
  y la API no se llama durante un backoff exponencial (5 s, 10 s, 20 s, 40 s, ...)
  hasta un máximo de 5 min. El primer éxito cierra el circuito. Protege al plugin
  de martillear la API cuando está caída.
- **User-Agent informativo**: ahora enviamos
  `40ServidoresMC/<pluginVer>/<platform>-<serverVersion>/Java<major>-<vendor>`
  tanto a la API como a GitHub (Updater). Permite al autor identificar de un vistazo
  la plataforma y versión del cliente que reporta problemas.
- **Cache dedicada para /stats40**: nuevo método `getStatsCmdCache()` en `CSPlugin`
  con TTL corto configurable (default **30 s** vía `stats-cmd-cache-seconds`).
  Separada del cache de placeholders (5 min), evita que admins martilleen la API
  con `/stats40` ejecutándolo repetidamente. Poner a `0` desactiva el cache.

### Cambiado
- `CSPlugin` añade dos métodos default (sin cambios para implementaciones externas):
  `getServerPlatform()` y `getServerVersion()`. Las implementaciones Bukkit/Sponge
  los sobrescriben con valores reales.
- `ApiClient` ahora expone `getCircuitBreaker()` para inspección/debug.

### Tests
- **100 tests unitarios** (anteriormente 74) en 13 clases. Nuevos tests para
  `UserAgent`, `CircuitBreaker` (apertura/cierre/backoff/cap), integración del
  circuit breaker en `ApiClient` y del statsCmdCache en `StatsCMD`.

## [3.0] - 2026-09-04

### Fork mantenido por richicru
Esta versión marca el inicio del fork mantenido por [@richicru](https://github.com/richicru),
continuando el trabajo de Cadiducho. Reconocimiento al autor original.

### Añadido
- Sistema de **mensajes traducibles** (`MessageKey`) con 19 mensajes y soporte para
  placeholders. Override vía `messages.*` en `config.yml`.
- **Cooldown configurable** en segundos (`cooldown: 60`).
- **Branch del Updater configurable** (`update-branch: dev`). La URL del JSON
  de versiones se construye desde `repo + branch`.
- **Log opcional de IP** tras voto válido (`log-ip: false`, opt-in por privacidad).
  Nuevo método `CSPlugin.getPlayerIp(name)`.
- **PlaceholderAPI funcional** con 6 placeholders: `%40servidoresmc_position%`,
  `%40servidoresmc_day_votes%`, `%40servidoresmc_day_votes_rewarded%`,
  `%40servidoresmc_week_votes%`, `%40servidoresmc_week_votes_rewarded%`,
  `%40servidoresmc_server_name%`. Usa `StatsCache` con TTL de 5 min.
- **74 tests unitarios** (anteriormente 1) organizados en 10 clases. Cobertura completa
  de Cooldown, modelos, CSCommand, ApiClient (HTTP contra servidor local), comandos
  VoteCMD y StatsCMD, MessageKey, StatsCache.
- **Documentación nueva**: `docs/FOLIA.md` (plan de compatibilidad Folia),
  `docs/SERVERS.md` (compatibilidad con servidores), `docs/SPEC.md` ampliado,
  `docs/MEJORAS.md` actualizado.

### Cambiado
- **ApiClient**: ahora valida status HTTP (lanza `IOException` con código si ≠ 2xx),
  añade `setConnectTimeout`, rechaza body vacío/null, propaga la causa original en
  `IllegalStateException`. Usa `ExecutorService` dedicado en vez de
  `ForkJoinPool.commonPool()`.
- **Updater**: refactorizado de campos `static` a instancia. Mismas mejoras HTTP que
  `ApiClient`. Usa `ExecutorService` dedicado (`cservidoresmc-updater`).
- **Cooldown**: `HashMap` → `ConcurrentHashMap`. `isCoolingDown` reescrito sin race
  conditions.
- **StatsCMD**: ya no lanza `StringIndexOutOfBoundsException` cuando `lastVotes` está
  vacío pero no es null.
- **configVer**: `3` → `4`.

### Corregido
- `StatsCMD` con lista de votos vacía (`StringIndexOutOfBoundsException`).
- `ApiClient` con respuestas HTTP no-2xx o body vacío.
- `Updater` con respuestas HTTP no-2xx o body vacío.
- `VoteResponse` y `UpdaterInfo` nulos (ahora se detectan y se reporta al usuario).
- Race condition en `Cooldown.isCoolingDown`.
- Erratas: `VoteStatus.INVALID_kEY` → `INVALID_KEY`. "àra validar" → "para validar".

## [2.5] - 2021

Última versión publicada por Cadiducho antes de este fork.

[2.5]: https://github.com/Cadiducho/40ServidoresMC/releases/tag/v2.5
[3.0]: https://github.com/richicru/40ServidoresMC/releases/tag/v3.0
[3.0.1]: https://github.com/richicru/40ServidoresMC/releases/tag/v3.0.1
