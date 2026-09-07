# Compatibilidad con servidores

## Estado actual

| Servidor | Compatible | Notas |
|---|---|---|
| **Spigot / CraftBukkit** | ✅ | Base histórica. Probado en Spigot 1.16.5+ |
| **Paper** | ✅ | Fork más popular. **Recomendado**. |
| **Purpur** | ✅ | Fork de Paper con configuración extra. |
| **Pufferfish** | ✅ | Fork de Paper enfocado en rendimiento. |
| **Leaf** | ✅ | Fork de Paper optimizado para PvP. |
| **Glowstone** | ✅ | Implementación independiente de Bukkit. |
| **Sponge API 7** | ✅ | Módulo separado `40ServidoresMC-Sponge-API7.jar`. |
| **Velocity / BungeeCord / Waterfall** | ✅ | El plugin se ejecuta en el **backend**, no en el proxy. |
| **Folia** | ✅ | Soportado desde v3.0.2 (schedulers region-aware por reflection). Verificado con un jugador real vía protocolo, no sólo tests unitarios (ver `scripts/test-vote-e2e-real.sh`). |
| **Mohist / Arclight / Banner / CatServer** | ⚠️ | Debería funcionar, no testeado en este fork. |
| **Fabric / NeoForge** | ❌ | API distinta, fuera de alcance. |
| **Nukkit / Bedrock Dedicated Server** | ❌ | Bedrock Edition, no soportado. |

## Versiones de Minecraft

El plugin no usa APIs específicas de una versión de Minecraft concreta, así que
debería funcionar en cualquier versión desde 1.8 hasta la última disponible.

El módulo `bukkit` compila contra `spigot-api:1.16.5`. Paper y forks exponen
esa API + extensiones, así que versiones superiores funcionan transparentemente.

## Proxies (Velocity, BungeeCord, Waterfall)

El plugin **no se ejecuta en el proxy**, sino en cada backend conectado. Los
jugadores votan, escriben `/voto40` en el chat (que pasa por el proxy al backend),
y el plugin valida el voto normalmente.

**No requiere configuración adicional** en el proxy. El forwarding de jugadores
funciona transparentemente.

## Datos de la audiencia

Según el reporte de servidores de 40servidoresmc.es:

- 44% usan proxy (Velocity/BungeeCord/Waterfall)
- ~13% Paper/Purpur detectable (probablemente muchos más detrás de proxies)
- ~14% modded (Forge/Fabric/NeoForge)
- 0% Folia detectado a fecha de v3.0 (**dato histórico, previo al soporte añadido en v3.0.2** -- no se ha vuelto a medir desde entonces)

Ver [SERVERS.md](../SERVERS.md) para el análisis completo.
