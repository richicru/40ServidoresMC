# Propuestas de mejora — 40ServidoresMC Plugin

> Análisis derivado del código fuente en la rama `dev` (v3.0). Ordenado por impacto.
> Documento complementario a [SPEC.md](SPEC.md).

## ✅ Mejoras aplicadas (esta rama)

### Primera ronda

- **`StatsCMD` lista de votos vacía.** Corregido en
  [StatsCMD.java:37](../common/src/main/java/com/cadiducho/cservidoresmc/cmd/StatsCMD.java#L37):
  se añade `!serverStats.getLastVotes().isEmpty()` antes de formatear.
- **Manejo de errores HTTP.** `setConnectTimeout`, validación de status code ≠ 2xx,
  rechazo de cuerpo vacío/null en [ApiClient.java](../common/src/main/java/com/cadiducho/cservidoresmc/ApiClient.java).
- **`VoteResponse` nulo.** `ApiClient.fetchData` lanza `IOException` si Gson devuelve null.
- **`Updater` estado estático.** Refactorizado a campos de instancia.
- **Executor dedicado para I/O.** `ApiClient` y `Updater` usan `ExecutorService` con
  threads daemon.
- **`Cooldown` a `ConcurrentHashMap`.** Sin race conditions.

### Segunda ronda

- **Erratas.** `VoteStatus.INVALID_kEY` → `INVALID_KEY`; "àra validar" → "para validar".
- **Cooldown configurable.** Nueva clave `cooldown: 60` en config. Sustituye al valor
  hardcodeado en `VoteCMD`.
- **Branch del Updater configurable.** Nueva clave `update-branch: dev`. El plugin
  construye la URL desde `https://raw.githubusercontent.com/{repo}/{branch}/etc/v3.json`.
- **Mensajes traducibles.** Nuevo enum `MessageKey` con 19 mensajes y método
  `resolve(config, placeholders...)`. Todos los strings hardcodeados de comandos y updater
  ahora pasan por `MessageKey`. Override vía `messages.*` en config.
- **Log de IP opt-in.** Nueva clave `log-ip: false` (default por privacidad). Si está activo,
  tras cada voto válido se registra `[VoteReward] player=X ip=Y` en consola. Nuevo método
  `CSPlugin.getPlayerIp(name)` implementado en Bukkit/Sponge.
- **PlaceholderAPI implementado.** 6 placeholders funcionales con caché interna (`StatsCache`,
  TTL 5 min):
  - `%40servidoresmc_position%`
  - `%40servidoresmc_day_votes%`
  - `%40servidoresmc_day_votes_rewarded%`
  - `%40servidoresmc_week_votes%`
  - `%40servidoresmc_week_votes_rewarded%`
  - `%40servidoresmc_server_name%`

### Tercera ronda (v3.0.1)

- **Gson empaquetado y relocado.** Antes Gson era `compileOnly` en `common` y ni siquiera
  estaba declarado en `bukkit`. En servidores sin Gson en su classpath el plugin daba
  `NoClassDefFoundError`. Ahora es `api`, queda empaquetado en el shadowJar y se reloca a
  `com.cadiducho.cservidoresmc.lib.gson` para evitar choques con Gson del servidor.
- **Circuit breaker en ApiClient.** Nueva clase `CircuitBreaker` (en memoria) que
  monitoriza fallos consecutivos de la API: tras 3 abre el circuito y rechaza llamadas
  durante un backoff exponencial (5s → 10s → ... cap 5 min). Primer éxito resetea.
  Evita martillear la API cuando está caída.
- **User-Agent informativo.** Ambos `ApiClient` y `Updater` envían ahora
  `40ServidoresMC/<ver>/<platform>-<serverVersion>/Java<major>-<vendor>`, sustituyendo
  el `User-Agent: 40ServidoresMC-Plugin/3.0` estático. Permite al autor identificar
  de un vistazo la plataforma y versión de un cliente que reporta un problema.
- **Cache dedicado para `/stats40`.** Nuevo `stats-cmd-cache-seconds` (default **30 s**)
  configurable y separado del cache de placeholders (5 min). Implementado con `getStatsCmdCache()`
  en `CSPlugin`. Evita que un admin martillee la API ejecutando `/stats40` repetidamente.
  Valor `0` desactiva el cache (vuelve al comportamiento anterior).

## 🔴 Robustez y bugs

- ✅ Lista de votos vacía → corregido
- ✅ Errores HTTP → corregido
- ✅ VoteResponse nulo → corregido

## 🟠 Concurrencia

- ✅ Updater static state → corregido
- ✅ supplyAsync sin executor → corregido
- ✅ Cooldown HashMap → corregido

## 🟡 Funcionalidad

- ✅ Cooldown configurable
- ✅ Branch updater configurable
- ✅ Mensajes traducibles
- ✅ Log de IP (opt-in)
- ✅ PlaceholderAPI placeholders
- ❌ Detección automática de votos vía Votifier — **descartado por diseño**: el usuario
  quiere mantener el comando manual `/voto40` (algunos usuarios votan y canjean la
  recompensa luego, en otro sub-server).

## 🟢 Internacionalización y configuración

- ✅ Mensajes a `messages.*` (parcialmente — se pueden traducir todas las claves del catálogo)

## 🔵 Mantenimiento / calidad

- ✅ Erratas corregidas
- ❌ Java 8 → Java 17 (no aplicado: esfuerzo alto, beneficio bajo inmediato)
- ❌ Unificar HOCON + YAML (no aplicado: deuda técnica interna)




## 🟡 Funcionalidad que falta

- **Detección automática de votos (Votifier/NuVotifier).**
  Actualmente el jugador **tiene que ejecutar `/voto40` manualmente** tras votar. Escuchar el
  evento de votación entregaría la recompensa automáticamente — es lo más esperable en un plugin
  de votos moderno.
- **Tab-complete.**
  `CSCommand.tabCompleteCommand` es un stub que devuelve lista vacía
  ([CSCommand.java:29](../common/src/main/java/com/cadiducho/cservidoresmc/cmd/CSCommand.java#L29)).
- **PlaceholderAPI sin placeholders.**
  El [PlaceholderHook](../bukkit/src/main/java/com/cadiducho/cservidoresmc/bukkit/PlaceholderHook.java)
  está registrado pero `onRequest` siempre devuelve `null`. Sería natural exponer
  `%40servidoresmc_posicion%`, `%..._votos_hoy%`, etc.
- **Recompensas más ricas:** recompensas aleatorias, por rachas de votos consecutivos, o según
  permiso/rango. Hoy solo hay una lista fija de comandos.
- **Recordatorio de voto** (mensaje al entrar / temporizado).

## 🟢 Internacionalización y configuración

- **Mensajes hardcodeados en español.**
  Casi todos los textos están incrustados en el código (`"No tienes permiso..."`,
  `"Obteniendo voto..."`). Moverlos a un `messages.yml`/`lang` permitiría traducirlos y
  personalizarlos sin recompilar.
- **Cooldown de 60s hardcodeado**
  en [VoteCMD.java:23](../common/src/main/java/com/cadiducho/cservidoresmc/cmd/VoteCMD.java#L23) —
  debería ser configurable.
- **`configVer` solo avisa, no migra.**
  Cuando la versión de config no coincide, solo imprime un warning; una migración automática
  evitaría errores al actualizar.
- **Dos formatos de config** (HOCON `.conf` en `common`, `.yml` en `bukkit`) — duplicación que
  conviene unificar.

## 🔵 Mantenimiento / calidad

- **Erratas:** el enum `INVALID_kEY` (k minúscula) en
  [VoteStatus.java:9](../common/src/main/java/com/cadiducho/cservidoresmc/model/VoteStatus.java#L9),
  y `"àra validar"` en VoteCMD.
- **Branch del Updater fijo a `dev`**
  ([Updater.java:85](../common/src/main/java/com/cadiducho/cservidoresmc/Updater.java#L85), ya
  marcado con TODO).
- **Tests mínimos:** solo existe `TestUpdater`. Faltan tests de `ApiClient` (mockeando HTTP), de
  la lógica de `VoteCMD` y del `Cooldown`. La `API_URL` está hardcodeada, lo que dificulta testear
  contra un mock.
- **Java 8.** Subir a Java 17 permitiría HTTP client moderno (`java.net.http.HttpClient`),
  records para los modelos, etc.

---

## Próximos pasos sugeridos

1. **PR pequeño de bugfixes:** el de `StatsCMD`, timeouts HTTP y el estado estático del `Updater`.
2. **Mejora de valor:** integración con Votifier/NuVotifier o implementación de los placeholders.
