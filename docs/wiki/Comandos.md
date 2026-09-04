# Comandos

## Lista completa

| Comando | Aliases | Permiso | Descripción |
|---|---|---|---|
| `/voto40` | `votar40`, `vote40`, `mivoto40` | `40servidores.voto` | Validar voto y entregar recompensa |
| `/stats40` | — | `40servidores.stats` | Estadísticas del servidor |
| `/test40` | — | `40servidores.test` | Simular entrega de premios |
| `/update40` | `actualizar40`, `upd40` | `40servidores.actualizar` | Buscar actualizaciones |
| `/reload40` | `recargar40`, `config40` | `40servidores.recargar` | Recargar configuración |

## `/voto40`

Valida el último voto del jugador en 40servidoresmc.es y entrega la recompensa
si es válido.

**Permiso:** `40servidores.voto` (por defecto, todos los jugadores).

**Cooldown:** `cooldown` segundos (default 60) entre ejecuciones por jugador.
Si el jugador ejecuta el comando mientras está en cooldown, recibe un mensaje
indicándolo.

**Solo jugadores:** No funciona desde la consola.

**Flujo:**
1. Jugador ejecuta `/voto40`.
2. Si está en cooldown → mensaje y termina.
3. Si no, se aplica el cooldown y se envía "Obteniendo voto...".
4. El plugin consulta la API en segundo plano.
5. Según la respuesta:
   - **SUCCESS** → se entrega el premio (mensaje + comandos custom) y broadcast si está activado.
   - **NOT_VOTED** → mensaje con link a la web.
   - **ALREADY_VOTED** → "ya has obtenido tu premio".
   - **INVALID_KEY** → mensaje de error de configuración.

## `/stats40`

Muestra estadísticas del servidor en el ranking de 40servidoresmc.es:

- Posición actual
- Votos del día (totales y premiados)
- Votos semanales (totales y premiados)
- Últimos 20 votos (con color verde si premiado, rojo si no)

**Permiso:** `40servidores.stats`

**Sin cooldown**, se puede usar libremente.

## `/test40`

Simula la entrega de premios sin necesidad de votar realmente. Útil para
configurar y probar las recompensas.

**Permiso:** `40servidores.test`

**Solo jugadores.**

## `/update40`

Comprueba manualmente si hay una nueva versión del plugin en GitHub.

**Permiso:** `40servidores.actualizar`

**Respuestas:**
- "Versión actualizada" — estás al día.
- "Versión desactualizada. Nueva versión: X. Changelog: Y. Descarga en: Z" — hay una nueva.
- "No hay versión más moderna recomendada para tu versión de Minecraft" — no hay coincidencia exacta.
- "No se pudo obtener la información de versiones" — error de red.

## `/reload40`

Recarga `config.yml` sin reiniciar el servidor.

**Permiso:** `40servidores.recargar`

Útil tras editar la configuración sin reiniciar.

## Permisos

Dar/quitar permisos a grupos (ejemplo con LuckPerms):

```bash
/lp group default permission set 40servidores.voto true
/lp group vip permission set 40servidores.test true
/lp group admin permission set 40servidores.recargar true
```
