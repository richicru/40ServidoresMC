# 40ServidoresMC

Plugin de Minecraft que recompensa a los jugadores por votar a tu servidor en [40servidoresmc.es](https://40servidoresmc.es), mejorando así su posición en el ranking.

## Estado del proyecto

Este fork está mantenido actualmente por **[@richicru](https://github.com/richicru)**, continuando el trabajo iniciado por **Cadiducho** (autor original, [@Cadiducho](https://github.com/Cadiducho)). El proyecto original está disponible en [Cadiducho/40ServidoresMC](https://github.com/Cadiducho/40ServidoresMC); este fork recoge las mejoras, correcciones y documentación que se están aplicando a partir de la versión 3.0.

## Funcionalidad

- **Validación de voto** — El jugador ejecuta `/voto40` tras votar en la web. El plugin consulta la API, comprueba si el voto es válido y entrega la recompensa (mensaje + comandos personalizados) y, opcionalmente, anuncia el voto a todo el servidor.
- **Estadísticas** — `/stats40` muestra la posición en el ranking, votos del día/semana (totales y premiados) y los últimos 20 votos.
- **Prueba de recompensas** — `/test40` simula la entrega de premios sin votar realmente.
- **Actualizaciones** — Al arrancar y bajo demanda (`/update40`) comprueba si hay una versión más reciente.
- **Recarga en caliente** — `/reload40` recarga la configuración sin reiniciar el servidor.
- **Integración con PlaceholderAPI** — 6 placeholders expuestos (`%40servidoresmc_position%`, `%40servidoresmc_day_votes%`, etc.) con caché de 5 min.
- **Mensajes traducibles** — Toda la cadena de textos configurable vía `messages.*` en `config.yml`.
- **Log opcional de IP** — Opt-in para auditoría (`log-ip: false` por defecto, por privacidad).

## Compatibilidad

| Servidor | Estado |
|---|---|
| Spigot / Paper / Purpur / Pufferfish / Leaf | ✅ Soportado |
| Velocity / BungeeCord / Waterfall (como proxy) | ✅ Se ejecuta en el backend |
| Sponge API 7 | ✅ Soportado |
| Glowstone | ✅ Soportado |
| Folia | ✅ Soportado desde v3.0.2 ([doc en docs/FOLIA.md](docs/FOLIA.md)) |
| Mohist / Banner / Arclight | ⚠️ Debería funcionar (no testado en este fork) |
| Fabric / NeoForge | ❌ Fuera de alcance |

Más detalles en [docs/SERVERS.md](docs/SERVERS.md).

## Instalación

1. Descarga el `.jar` correspondiente desde [Releases](https://github.com/richicru/40ServidoresMC/releases).
2. Colócalo en la carpeta `plugins/` de tu servidor.
3. Reinicia el servidor.
4. Edita `plugins/40ServidoresMC/config.yml` y rellena la clave `clave` con el valor obtenido en [40servidoresmc.es/miservidor.php](https://40servidoresmc.es/miservidor.php).
5. `/reload40` para recargar la configuración.

## Comandos

| Comando | Aliases | Permiso | Función |
|---|---|---|---|
| `/voto40` | `votar40`, `vote40`, `mivoto40` | `40servidores.voto` | Validar voto y entregar recompensa |
| `/stats40` | — | `40servidores.stats` | Estadísticas del servidor |
| `/test40` | — | `40servidores.test` | Simular entrega de premios |
| `/update40` | `actualizar40`, `upd40` | `40servidores.actualizar` | Buscar actualizaciones |
| `/reload40` | `recargar40`, `config40` | `40servidores.recargar` | Recargar configuración |

## Compilar

```bash
git clone https://github.com/richicru/40ServidoresMC.git
cd 40ServidoresMC
./gradlew :bukkit:shadowJar
./gradlew :sponge-api7:shadowJar
```

Los JARs resultantes aparecen en `bukkit/build/libs/` y `sponge/api7/build/libs/`.

## Tests

```bash
./gradlew :common:test
```

144 tests unitarios cubren el núcleo del plugin (modelos, comandos, cliente HTTP, caché,
cooldown, protocolo v3, IP sanitizada, reintento de acks). Se complementan con un entorno
de test end-to-end contra Paper y Folia reales (ver [docs/testing/Local-Test-Setup.md](docs/testing/Local-Test-Setup.md)
y `scripts/test-vote-e2e-real.sh`, que conecta un jugador real vía protocolo para probar
`/voto40` de principio a fin — los tests unitarios y el mock HTTP por sí solos no cubren
los schedulers de Folia ni el resultado real de `dispatchCommand`).

## Documentación

- [docs/SPEC.md](docs/SPEC.md) — Especificación técnica completa.
- [docs/MEJORAS.md](docs/MEJORAS.md) — Lista de mejoras aplicadas y pendientes.
- [docs/FOLIA.md](docs/FOLIA.md) — Plan de compatibilidad con Folia.
- [docs/SERVERS.md](docs/SERVERS.md) — Compatibilidad con servidores Minecraft.
- [CHANGELOG.md](CHANGELOG.md) — Historial de cambios.

## Licencia

Ver [LICENSE](LICENSE). El proyecto original es de Cadiducho; este fork mantiene la misma licencia y reconoce la autoría original.

## Contribuir

Issues y pull requests son bienvenidos. Por favor, mantén el estilo del código y añade tests cuando añadas funcionalidad nueva. Recuerda actualizar el [CHANGELOG.md](CHANGELOG.md).
