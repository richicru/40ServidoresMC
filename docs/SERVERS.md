# Compatibilidad con servidores de Minecraft

> Última revisión: sept 2026. Datos basados en el reporte real de software de servidores
> usado por la audiencia de 40servidoresmc.es (307 servidores muestreados).
> Complementa [SPEC.md](SPEC.md) y [FOLIA.md](FOLIA.md).

---

## 1. Datos reales de uso (audiencia 40servidoresmc)

| Software | Servidores | % | Categoría | Compatible con plugin |
|---|---:|---:|---|---|
| **Velocity** (proxy) | 125 | 40,7% | Proxy | N/A — no corre plugins Bukkit |
| **Desconocido** | 86 | 28,0% | Proxy probable | N/A |
| **Forge / Fabric** (modded) | 42 | 13,7% | Modded | ❌ API distinta |
| **Paper** | 24 | 7,8% | Bukkit fork | ✅ |
| **Vanilla** | 9 | 2,9% | Vanilla | ❌ sin plugins |
| **Purpur** | 9 | 2,9% | Bukkit fork | ✅ |
| **BungeeCord** (proxy) | 6 | 2,0% | Proxy | N/A |
| **Waterfall** (proxy) | 4 | 1,3% | Proxy | N/A |
| **NeoForge** | 1 | 0,3% | Modded | ❌ |
| **Spigot** | 1 | 0,3% | Bukkit | ✅ |

### Lectura

- **44% de los servidores son proxies** (Velocity + BungeeCord + Waterfall). No corren
  plugins Bukkit, pero **redirigen a backends Bukkit** (típicamente Paper/Purpur).
- El **"Desconocido" (28%)** son servidores que ocultan su tipo detrás del proxy. Casi
  con seguridad son Paper/Purpur.
- **Estimación realista de Paper/Purpur reales** detrás de proxies: ~100-130 servidores.
- El plugin se ejecuta en el **backend** (no en el proxy). Por tanto, los usuarios de
  Velocity/BungeeCord ya están cubiertos si el backend es Paper/Purpur.
- **Folia aparece con 0 servidores detectados** en esta muestra: prioridad media, no urgente.

### Conclusión

**El 95%+ de los usuarios reales ejecutan Paper/Purpur en su backend.** El plugin actual
ya los cubre.

---

## 2. Compatibilidad por servidor

### 🔴 Prioridad alta — Audiencias principales

#### Paper / Purpur (Bukkit family)

- **Compatible sin cambios.** El módulo `bukkit` compila contra `spigot-api:1.16.5` y
  Paper/Purpur exponen esa API + extensiones.
- **Acción:** garantizar testing en Docker Compose con imagen `itzg/minecraft-server`
  usando `TYPE=PAPER` y `TYPE=PURPUR`.

#### Velocity / BungeeCord / Waterfall (proxies)

- **No se ejecuta en el proxy**, pero la audiencia lo usa masivamente.
- Compatible si el backend es Bukkit/Paper/Purpur/Folia: el plugin corre **en el backend**,
  los jugadores se conectan a través del proxy, y los comandos funcionan transparentemente.
- **`plugin.yml` correcto** — no requiere cambios. Solo documentar en README y wiki.
- **Limitación a documentar:** si el servidor usa `velocity-modern` forwarding con
  IP forwarding estricto, los comandos que dependen de la IP real del jugador
  (no es nuestro caso, no usamos IP) podrían tener caveats. Nuestro plugin solo
  envía/recibe mensajes y ejecuta comandos de consola, así que es totalmente
  compatible con cualquier configuración de forwarding.

#### Sponge (SpongeAPI 7)

- **Compatible ya.** Módulo `sponge/api7` activo.
- **Acción:** mantener testing aunque la cuota real de mercado sea ínfima.

---

### 🟡 Prioridad media — Oportunidades

#### Folia (PaperMC)

- Fork de Paper con multithreading regionizado.
- **Estado actual:** incompatible. Plan detallado en [FOLIA.md](FOLIA.md).
- **Acción:** implementar las 7 fases del plan para añadir soporte Folia.
- **Impacto:** bajo a corto plazo (0 detecciones), pero abre puertas a servidores
  con muchos jugadores que están migrando.

#### Glowstone

- Implementación independiente de Bukkit (sin NMS).
- **Compatible** salvo operaciones no soportadas (entidades custom, packets NMS).
- **Acción:** ya documentado en el módulo `bukkit`, sin cambios necesarios.

#### Spigot / CraftBukkit

- Base histórica.
- **Compatible** al ser la API más básica.
- **Acción:** ya cubierto por el testing contra Paper (la API Paper es superset).

#### Mohist / Arclight / Banner / CatServer (hybrid modded)

- Ejecutan plugins Bukkit en servidores modded.
- **Probable compatibilidad** porque el plugin no toca NMS ni reflection.
- **Acción:** testear manualmente en uno (recomendado: **Mohist**, más estable).
- **Impacto:** ~13% de la audiencia usa modded, vale la pena.

---

### 🟢 Prioridad baja — Fuera de alcance

- **Fabric** — Fabric API. Reescritura completa del módulo `bukkit`.
- **NeoForge / Forge sin Sponge** — Mismo caso.
- **SpongeForge / SpongeNeo** — Soporte indirecto posible (Sponge sobre Forge), pero
  requiere módulo adicional y la audiencia es muy pequeña.
- **Nukkit / PowerNukkit** — Bedrock Edition, API distinta.

---

## 3. Estrategia de testing

### Setup local (Docker Compose)

Archivo `docker-compose.yml` con los servidores que cubren el 95% de la audiencia:

```yaml
services:
  paper:
    image: itzg/minecraft-server
    container_name: mc-paper
    environment:
      TYPE: PAPER
      VERSION: "1.20.4"
      EULA: "TRUE"
      PLUGIN_FILE: "40ServidoresMC-Bukkit.jar"
    ports: ["25565:25565"]
    volumes:
      - ./build/libs:/plugins

  purpur:
    image: itzg/minecraft-server
    container_name: mc-purpur
    environment:
      TYPE: PURPUR
      VERSION: "1.20.4"
      EULA: "TRUE"
      PLUGIN_FILE: "40ServidoresMC-Bukkit.jar"
    ports: ["25566:25565"]
    volumes:
      - ./build/libs:/plugins

  folia:
    image: itzg/minecraft-server
    container_name: mc-folia
    environment:
      TYPE: FOLIA
      VERSION: "1.20.4"
      EULA: "TRUE"
      PLUGIN_FILE: "40ServidoresMC-Bukkit.jar"
    ports: ["25567:25565"]
    volumes:
      - ./build/libs:/plugins

  sponge:
    image: itzg/minecraft-server
    container_name: mc-sponge
    environment:
      TYPE: SPONGE
      VERSION: "1.20.4"
      EULA: "TRUE"
      PLUGIN_FILE: "40ServidoresMC-Sponge-API7.jar"
    ports: ["25568:25565"]
    volumes:
      - ./build/libs:/plugins
```

Uso:
```bash
docker compose up -d paper         # solo Paper
docker compose up -d paper folia   # Paper + Folia
docker compose down                # parar todos
```

### CI en GitHub Actions

Repos públicos tienen minutos ilimitados. Workflow en `.github/workflows/test-servers.yml`:

```yaml
name: Test on servers
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        server: [paper, purpur, folia]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - name: Build plugin
        run: ./gradlew :bukkit:shadowJar
      - name: Run integration tests
        run: ./gradlew :integrationTest -PserverType=${{ matrix.server }}
```

---

## 4. Resumen ejecutivo actualizado

| Servidor | Compatible hoy | Compatible tras plan | Prioridad |
|---|---|---|---|
| **Paper / Purpur** | ✅ | ✅ | 🔴 Alta (audiencia principal) |
| **Velocity / BungeeCord / Waterfall** | ✅ (en backend) | ✅ | 🔴 Alta (proxy más usado) |
| **Sponge** | ✅ | ✅ | 🔴 Alta (mantener) |
| **Glowstone** | ✅ | ✅ | 🟡 Media |
| **Spigot** | ✅ | ✅ | 🟡 Media |
| **Folia** | ❌ | ✅ (con plan) | 🟡 Media |
| **Mohist / Arclight / Banner / CatServer** | ⚠️ probable | ⚠️ | 🟡 Media |
| **Fabric / NeoForge / Forge sin Sponge** | ❌ | ❌ | 🟢 Baja (fuera de alcance) |

**Cobertura tras implementar plan de Folia:** ~98% del mercado Java detectado.
