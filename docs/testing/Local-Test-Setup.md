# Testing local: Paper y Folia en Docker

Esta carpeta describe cómo validar el plugin contra **Paper** (Bukkit/Spigot/Paper clásico)
y contra **Folia** (fork multithreaded de Paper) en local, sin necesidad de instalar
Minecraft en tu máquina.

## TL;DR

```bash
# 1) Construir el plugin y copiarlo a los plugins/ de cada servicio
./scripts/install-plugin.sh

# 2) Levantar ambos servidores en background
./scripts/test-servers-up.sh

# 3) Esperar a que terminen (1-3 min la primera vez, descarga JAR)
docker compose -f docker-compose.test.yml logs -f cs-test-paper   # Ctrl+C para salir

# 4) Validar que el plugin cargó correctamente
./scripts/test-validate.sh

# 5) O hacer todo lo anterior en un solo comando
./scripts/test-run.sh

# Al terminar
./scripts/test-servers-down.sh
```

## Servicios disponibles

| Servicio | Tipo   | Puerto host | Puerto contenedor | Notas                            |
| -------- | ------ | ----------- | ------------------ | -------------------------------- |
| paper    | PAPER  | 25565       | 25565              | Paper 1.20.4 (compatible Java 17) |
| folia    | FOLIA  | 25566       | 25565              | Folia 1.20.4 (compatible Java 17) |

Desde tu cliente Minecraft, conéctate a:

- `localhost:25565` para Paper
- `localhost:25566` para Folia

## Cómo está organizado

- `docker-compose.test.yml` — define `cs-test-paper` y `cs-test-folia`.
- `.docker/paper/` — datos persistentes de Paper (se puede borrar para empezar de cero).
- `.docker/folia/` — datos persistentes de Folia.
- `scripts/install-plugin.sh` — compila y copia el JAR al directorio de plugins.
- `scripts/test-servers-up.sh` — levanta los contenedores.
- `scripts/test-servers-down.sh` — para y borra los contenedores.
- `scripts/test-validate.sh` — analiza los logs y comprueba que el plugin carga sin errores.
- `scripts/test-run.sh` — flujo completo: build + install + up + wait + validate + down.

## Cosas que valida `test-validate.sh`

1. El servidor arrancó (mensaje `Done (X.X s)` en logs).
2. El plugin cargó (mensaje `40ServidoresMC v3.0.X cargado completamente`).
3. No hay errores/excepciones graves (`java.lang.*Exception`, `[ERROR]`).
4. No hay `NoSuchMethodError` (síntoma típico de incompatibilidad Paper/Folia API).

Los resultados se guardan en `/tmp/cs-test-paper.test.log` y `/tmp/cs-test-folia.test.log`.

## Decisiones de diseño

### Folia + el flujo IO de la API

La API de 40servidoresmc.es (`ApiClient`) ejecuta `fetchServerStats()` y `validateVote()`
desde threads de I/O (`cservidoresmc-io`). En Folia no se puede acceder a la API de Bukkit
desde cualquier thread. Por eso, los callbacks:

```java
apiClient.validateVote(name).thenAccept(response -> {
    sender.sendMessage(...);   // necesita thread del jugador
    plugin.dispatchCommand(...);  // necesita GlobalRegionScheduler
    plugin.broadcastMessage(...); // debe enrutar por region scheduler por jugador
});
```

Se han envuelto en métodos de `CSPlugin`:

- `runSyncForPlayer(name, Runnable)` — agenda en el `EntityScheduler` del jugador (Folia) o
  en el main thread scheduler (Paper).
- `runSyncGlobal(Runnable)` — agenda en `GlobalRegionScheduler` (Folia) o main thread (Paper).
- `runForEachOnlinePlayer(Consumer<CSCommandSender>)` — en Folia, enruta por entidad; en
  clásico lo ejecuta en main thread.

### Detección runtime de Folia

BukkitPlugin detecta Folia una vez, en `onEnable`:

```java
this.folia = tryClass("io.papermc.paper.threadedregions.RegionizedServer");
```

Si la clase existe, asumimos Folia. Si no, caemos al camino clásico de BukkitScheduler.
Esto permite que el mismo JAR funcione en cualquiera de las dos plataformas.

### Compatibilidad Bukkit/Spigot/Paper/Folia en un único JAR

- `bukkit/build.gradle` declara dos `compileOnly`:
  - `io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT` (incluye Bukkit + Spigot + Paper + Folia APIs).
  - Esto **reemplaza** el anterior `org.spigotmc:spigot-api` (Paper API es un superset directo).
- En runtime, los métodos nuevos de Folia (`getGlobalRegionScheduler`, `EntityScheduler`)
  son `default`/`null` en Paper, y simplemente no existen en Spigot puro. Por eso la
  detección runtime es esencial.
- Si Spigot no expone el método, caemos al camino clásico sin errores.

## Voto real end-to-end (jugador de verdad, no RCON ni mock por curl)

`scripts/test-vote-e2e-real.sh` (2026-09-07) conecta un bot de protocolo real
(mineflayer, `scripts/e2e-bot/`) a Paper y Folia como jugador de verdad —no
consola, no RCON— y ejecuta `/voto40`. Es el único camino que ejercita
`dispatchCommand()` y los schedulers de Folia de verdad: tres bugs críticos
(dos de ellos exclusivos de Folia) sólo se manifestaban con un jugador real
ejecutando el comando, y ni los 144 tests unitarios ni
`test-vote-scenarios.sh` (que sólo golpea el mock HTTP con `curl`) los
detectaban. Requiere el stack arriba y npm/Node para el bot (se instala solo
la primera vez). Verifica tanto el mensaje real en el chat del jugador como
el body real del `ack` que el plugin manda al server (`entregado:true`/`false`).

```bash
./scripts/test-vote-e2e-real.sh              # Paper + Folia, los 3 casos (éxito/ya canjeado/nunca votó)
./scripts/test-vote-e2e-real.sh --paper-only
./scripts/test-vote-e2e-real.sh --folia-only
```

## Limitaciones conocidas

- **El validado de `test-validate.sh`/`test-vote-scenarios.sh` se centra en el
  arranque y en el mock HTTP**, porque ahí se ven la mayoría de incompatibilidades
  de thread/region. Las pruebas unitarias (144 en `common`) cubren la lógica de
  negocio. Para el voto real de un jugador contra Paper/Folia, usa
  `test-vote-e2e-real.sh` (arriba) — es lo que hace falta para pillar bugs de
  scheduler o de `dispatchCommand()`.
- **Modo creative + peaceful**: configurado así para evitar ticks del mundo. No afectan
  al test del plugin.
- **Java 17**: Paper 1.20.4 requiere Java 17+; Folia también. Usamos `java-17-openjdk`
  del sistema. Si tu Gradle está en Java 21, puedes hacer `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.
- **Image pull pesa ~1 GB**: la primera vez tarda varios minutos. Tras eso, los arranques
  son segundos.

## Excluido del repo

Añadimos al `.gitignore`:

```
/.docker/
docker-compose.test.yml
scripts/*.log
```
Esto evita meter al repo los worlds generados ni los JARs de servidor.

## Solución de problemas comunes

| Síntoma | Causa posible | Solución |
| ------- | ------------- | -------- |
| `bind: address already in use 0.0.0.0:25565` | Otra instancia de Minecraft escuchando | `lsof -i :25565` y matar; o cambiar puerto en `docker-compose.test.yml` |
| `eula.txt denied` / `EULA not accepted` | Falta `EULA: "TRUE"` en el compose | Ya está incluido; revisa `docker compose logs` |
| Plugin no carga: `ClassNotFoundException: com.google.gson...` | El JAR no se rebuildeó tras la migración | `./scripts/install-plugin.sh --rebuild && ./scripts/test-validate.sh` |
| Plugin no carga: `IllegalStateException` de scheduler | Estás ejecutando en Folia sin las modificaciones | Confirma que has compilado con la versión v3.0.2 |
| Arranque lento | Primera vez: descarga JAR de Paper/Folia (~1 GB) | Esperar 3-5 min. Posteriores arranques, segundos. |
| `image itzg/minecraft-server not found` | Falta `pull` | Ejecutar `docker pull itzg/minecraft-server` manualmente la primera vez |
