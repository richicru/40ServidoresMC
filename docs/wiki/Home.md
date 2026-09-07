# Inicio

Bienvenido a la documentación de **40ServidoresMC**, el plugin que conecta tu servidor
de Minecraft con la web [40servidoresmc.es](https://40servidoresmc.es) para recompensar
a los jugadores que votan.

## ¿Qué hace este plugin?

Cuando un jugador vota por tu servidor en 40servidoresmc.es, ejecuta `/voto40` en el
juego y el plugin valida automáticamente el voto con la API oficial. Si es válido,
entrega una recompensa configurable (mensaje + comandos personalizados) y, opcionalmente,
anuncia el voto al resto del servidor.

## Empezando

1. [Instalación](Instalacion.md)
2. [Compatibilidad con Java](Compatibilidad-Java.md)
3. [Compatibilidad con servidores](Compatibilidad-Servidores.md)
4. [Configuración](Configuracion.md)
5. [Comandos](Comandos.md)

## Características principales

- ✅ Soporte para **Bukkit / Spigot / Paper / Purpur / Pufferfish / Leaf** y derivados
- ✅ Soporte para **Sponge API 7**
- ✅ Soporte para **Folia** (Paper multithreaded regionizado, desde v3.0.2)
- ✅ Compatible con proxies **Velocity / BungeeCord / Waterfall** (se ejecuta en el backend)
- ✅ Recompensas configurables con placeholders (`{0}` = jugador)
- ✅ Mensajes traducibles vía `messages.*` en config
- ✅ Integración con **PlaceholderAPI** (6 placeholders)
- ✅ Cooldown configurable por jugador
- ✅ Comprobación de actualizaciones desde GitHub
- ✅ Log opcional de IP para auditoría
- ✅ Métricas vía bStats

## Sobre el fork

Este fork lo mantiene [@richicru](https://github.com/richicru) desde la
versión 3.0 (ver [CHANGELOG.md](https://github.com/richicru/40ServidoresMC/blob/master/CHANGELOG.md)
para la versión actual y el historial completo). El plugin original fue
desarrollado por [Cadiducho](https://github.com/Cadiducho) hasta la versión
2.5. Este fork continúa el trabajo añadiendo compatibilidad con servidores
modernos, corrección de bugs, tests y documentación.

## Soporte

- 🐛 [Issues](https://github.com/richicru/40ServidoresMC/issues) — bugs y sugerencias
- 📖 [Docs técnicas](../SPEC.md) — especificación técnica del plugin
- 📜 [CHANGELOG](../../CHANGELOG.md) — historial de cambios
