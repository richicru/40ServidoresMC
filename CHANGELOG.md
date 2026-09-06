# Changelog

Todos los cambios relevantes del plugin se documentan en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto se adhiere a [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.1.0] - 2026-09-06

### Añadido
- **Protocolo v3 de 40ServidoresMC.** `/api/vote/v3/pending` (Bearer auth) →
  entregar premio → `/api/vote/v3/ack`. Arregla un bug real: con `/api2.php`
  el server marca el voto como cobrado antes de que llegue la respuesta, así
  que si se pierde la respuesta el jugador se queda sin premio. Con v3 el ack
  explícito es lo que cierra la transacción.
  - 4 ramas diferenciadas en `VoteCMD`: pending con votos (entregar + ack),
    pending vacío + `puede_votar_ya=true` (vota en la web), pending vacío +
    `puede_votar_ya=false` (ya canjeado, mostramos `siguiente_voto`), HTTP
    403 (clave incorrecta).
  - Si la entrega falla (algún `dispatchCommand` devuelve `false`, o el jugador
    se desconecta durante la entrega), el ack se manda con `entregado:false`
    para liberar la reserva al instante.
  - Si el ack falla a su vez, la reserva expira sola a los 5 minutos (no
    reintentamos: el protocolo v2 ya marca el voto como cobrado y un reintento
    devolvería "ya recompensado").
- **`user_ip` hasheada con SHA-256** antes de mandarla en el ack. Nunca
  almacenamos ni enviamos la IP en claro. IPs inválidas (con puerto, con
  scope IPv6, formato roto) se descartan silenciosamente.
- **Nuevas `MessageKey`**: `VOTE_V3_PENDING_VOTE`, `VOTE_V3_ALREADY_REWARDED`,
  `VOTE_V3_THANKS`, `VOTE_V3_DELIVERY_FAILED`, `VOTE_V3_ACK_FAILED`,
  `VOTE_V3_INVALID_KEY`.
- **Nuevo método en `CSPlugin`**: `runSyncForPlayerWithResult` (versión
  síncrona de `runSyncForPlayer` que devuelve un valor) e `isPlayerOnline`
  (default true, sobrescrito por Bukkit/Sponge).
- **Mock-API**: rutas `/api/vote/v3/pending` y `/api/vote/v3/ack` con routing
  por prefijo de nick (success-, notvoted-, already-, invalidkey-, 500-, broken-).
- **Nuevo escenario `test-v3-vote-flow.sh`** y cinco nuevos escenarios v3
  en `test-vote-scenarios.sh` (27/27 verdes en el entorno de testing local).

### Cambiado
- **`api-url` cambia de semántica**: ahora se interpreta como base URL
  (scheme + host + port). El plugin construye los paths absolutos según el
  endpoint (`/api2.php?clave=` para v2/legacy y stats; `/api/vote/v3/...`
  para v3). Si la URL legacy `.../api2.php?clave=` se mantiene en config, el
  plugin la trunca automáticamente. `getApiBase()` devuelve la base limpia
  truncada; `getBaseUrl()` mantiene la URL legacy para los paths v2.
- **`dispatchCommand` ahora devuelve `boolean`**: true si el comando se
  despachó y ejecutó sin lanzar excepciones; false en caso contrario. Lo
  usa `VoteCMD` para detectar entregas fallidas en el flujo v3.
- Stats (`/stats40`) sigue usando el endpoint legacy `/api2.php` (no hay
  equivalente v3).

### No se hace
- **No hay endpoint que devuelva todos los pendientes del servidor.**
  El flujo sigue siendo bajo demanda (jugador ejecuta `/voto40`).
- **No se reintenta pending.** Reintentar es seguro pero inútil en este
  protocolo — los ids ya vienen en la respuesta.

## [3.0.5] - 2026-09-04

### Corregido (diagnóstico)
- **`VoteCMD` / `StatsCMD` no registraban la causa real del error.** El wrapper
  `IllegalStateException("Cannot execute API call", e)` se logueaba como
  `e.getMessage()` (que es siempre "Cannot execute API call"), perdiendo la
  causa raíz (java.net.SocketTimeoutException, FileNotFoundException, etc.).
  Hoy un reporte del dueño distingue inmediatamente "DNS caído" de "timeout"
  de "el plugin asume que la API está caída".
  - Fix: nuevo helper `VoteCMD.unwrapRootCause(e)` que recorre la cadena de
    causas (con detección de bucles). En los handlers de error de /voto40
    y /stats40, registramos `root.getClass().getName() + ": " + root.getMessage()`
    en consola, no el genérico.

### Cambiado (mensajes al jugador)
- **`CircuitBreaker.CircuitOpenException` ahora se lanza y se trata aparte.**
  Antes `fetchData()` lanzaba un `IOException("Circuit breaker open ...")`
  genérico y el jugador recibía el mismo mensaje que ante cualquier otro
  error (indistinguible de "la API está caída").
  - `fetchData` ahora lanza la excepción propia del circuit breaker con
    `retryAfterMs`.
  - El jugador recibe `&eReintentando en {seconds}s. Tu voto no se ha perdido.`
  - En el log del servidor: `Voto saltado: circuit breaker abierto, reintento en Ns.`

- **HTTP 429 NO alimenta el circuit breaker.** Antes tres 429 seguidos
  abrían el circuito (con backoff de 5 s), igual que ante un fallo de red.
  - Nuevo: clase `RateLimitedException` (RuntimeException). Cuando la API
    devuelve 429 con cabecera `Retry-After`, el plugin la respeta y
    muestra `&eAPI saturada (límite 20/min). Reintenta en {seconds}s.`
  - El circuit breaker sólo se abre ante fallos de red / 5xx genuinos.
  - `parseRetryAfter` soporta el formato entero (RFC 7231 §7.1.3) y cae
    a 5 s de fallback si la cabecera falta o es inválida.

### Añadido
- Nuevas claves `MessageKey` (con defaults ES): `VOTE_CIRCUIT_OPEN`,
  `VOTE_RATE_LIMITED`, `STATS_CIRCUIT_OPEN`, `STATS_RATE_LIMITED`.
  Override vía `messages.*` en config (igual que el resto del catálogo).
- Nuevo escenario en `scripts/test-vote-scenarios.sh` para 429 y circuit
  breaker, validado en mock-api.
- Mock API añade endpoints `/__admin/rate_limit?seconds=N&retry_after=N`
  y `/__admin/circuit_open?seconds=N` para forzar respuestas en tests.

### No se hace
- No se reintenta automáticamente tras un 429 (la API ya marca el voto como
  reclamado y un reintento devolvería `status=2` sin recompensa). El plugin
  sólo informa al jugador del tiempo de espera.

## [3.0.4] - 2026-09-04

### Cambiado
- **URL base de la API configurable.** Movido de constante `private final` a clave
  `api-url` en config. Hoy un cambio de dominio no obliga a publicar un JAR nuevo:
  basta con editar `config.yml` (o `40ServidoresMC.conf`) y reiniciar, o `/reload40`.
  - **Default**: `https://www.40servidoresmc.es/api2.php?clave=` (con `www.`, antes era
    sin `www.` y disparaba un 301 que añadía un handshake TLS extra).
  - Cadena vacía en config también cae al default.
- **`readTimeOut` default subido de 5000 ms a 10000 ms.** El protocolo actual marca el
  voto como "reclamado" en el servidor antes de que llegue la respuesta al cliente;
  un timeout corto (5 s) podía consumir el voto sin entregar el premio al jugador.
  Un timeout más largo reduce ese riesgo sin coste real.

### No se hace
- **No se añaden reintentos sobre `/api2.php`.** Si la primera llamada ya llegó al
  servidor, el voto se considera reclamado y un reintento devolvería "ya recompensado"
  sin entregar premio. La solución adecuada es el protocolo nuevo (pendiente/v3/ack),
  no un retry ingenuo.

## [3.0.3] - 2026-09-04

### Corregido
- **El plugin no creaba la carpeta `plugins/40ServidoresMC/` ni el `config.yml` en
  instalación limpia** (regresión que afectaba a todos los servidores Bukkit/Spigot/Paper/Folia).
  - Causa: `BukkitConfigurationAdapter.reload()` llamaba a
    `YamlConfiguration.loadConfiguration(file)` y, si el archivo no existía, devolvía
    una configuración vacía en silencio. Nunca se llamaba a `saveDefaultConfig()`,
    así que ni la carpeta ni el `config.yml` se generaban.
  - Consecuencia visible: el plugin funcionaba con los defaults hardcodeados, pero
    el usuario no podía editar `config.yml` y veía el warning "`Tu configuración
    es de una versión más antigua...`" en cada arranque.
  - Fix: `BukkitPlugin.onEnable()` ahora llama `saveDefaultConfig()` antes de
    instanciar el adapter. Si el JAR no contiene `config.yml`, queda creado
    en disco y la carpeta aparece.
  - El módulo `sponge/api7` ya lo hacía correctamente (`SpongePlugin.resolveConfig`),
    por lo que no requiere cambios.

## [3.0.2] - 2026-09-04

### Añadido
- **Compatibilidad con Folia.** El plugin ahora carga y opera en Folia
  (Paper multithreaded regionizado). Verificado con Paper 1.20.4 + Folia 1.20.4
  en el entorno de testing local (`docs/testing/Local-Test-Setup.md`).
  - Detección runtime de Folia vía `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`
    (clase `BukkitPlugin.FoliaDetector`).
  - Nueva abstracción en `CSPlugin`: `runSyncForPlayer(name, task)` y
    `runSyncGlobal(task)` y `runForEachOnlinePlayer(action)`. En Folia, el primero
    usa `EntityScheduler`, los otros dos `GlobalRegionScheduler`. En Paper clásico,
    usan `BukkitScheduler.runTask` como siempre.
  - `dispatchCommand` y `broadcastMessage` de `BukkitPlugin` reescritos para usar
    los nuevos métodos (compatible con main-thread-only y region-aware).
  - `VoteCMD`, `StatsCMD` y `Updater.checkearVersion` envuelven todos los callbacks
    async en las nuevas primitivas — antes, los `thenAccept(...)` escribían
    mensajes o ejecutaban comandos desde `ForkJoinPool` (inválido en Folia).
  - `plugin.yml` añade `folia-supported: true` (sin él, Folia rechaza el plugin).

### Cambiado
- Se mantiene `org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT` como `compileOnly`
  en `bukkit/build.gradle` — todas las APIs de Folia se acceden por reflection
  para no requerir Paper API como dependencia de compilación.

### Añadido (infra)
- Entorno de testing local Docker: `docker-compose.test.yml` + scripts en `scripts/`.
  Ver `docs/testing/Local-Test-Setup.md` para flujo completo.
- Skill opencode en `.opencode/skills/40servidoresmc-paper-folia-test.md`
  documentando el patrón de schedulers Folia/Paper.

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
