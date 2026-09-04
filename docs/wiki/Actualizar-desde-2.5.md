# Actualizar desde v2.5

Esta guía describe cómo migrar de la versión 2.5 (última versión de Cadiducho) a la versión 3.0.

## Cambios incompatibles

### 1. Java mínimo

2.5 requería Java 8+. 3.0 también requiere Java 8+. **No hay cambio.**

### 2. Formato de config

2.5 usaba formato `.yml` (Bukkit) y `.conf` (Sponge).

3.0 mantiene ambos formatos. **No hay cambio en el formato**, pero se añadieron
nuevas claves (ver más abajo).

### 3. `configVer`

2.5 tenía `configVer: 3`. 3.0 espera `configVer: 4`.

Si tu config sigue en `configVer: 3`, el plugin mostrará un warning al arrancar
pero seguirá funcionando.

### 4. Nuevas claves de configuración

| Clave nueva | Default | Acción |
|---|---|---|
| `cooldown` | `60` | Añadir si quieres tunear el cooldown (antes era hardcoded). |
| `update-branch` | `development` | Añadir solo si quieres comprobar otra rama. |
| `log-ip` | `false` | Añadir `true` si quieres registrar IP en votos. |
| `messages` | `{}` | Añadir si quieres traducir mensajes. |

### 5. Cambios en `comandosCustom` y `broadcast`

Sin cambios. Los placeholders `{0}` siguen funcionando igual.

## Pasos para actualizar

1. **Haz backup** de tu `config.yml` actual.

2. **Descarga** `40ServidoresMC-3.0-Bukkit.jar` (o `-Sponge-API7.jar`).

3. **Reemplaza** el JAR antiguo en `plugins/`.

4. **Reinicia el servidor.**

5. **Edita** `plugins/40ServidoresMC/config.yml` y:
   - Verifica que `clave` sigue siendo tu clave válida.
   - Añade `cooldown: 60` si quieres configurar el cooldown (opcional).
   - Añade `update-branch: development` si lo necesitas (opcional).
   - Añade `log-ip: false` (recomendado dejarlo en `false` por privacidad).
   - Actualiza `configVer: 4` (el plugin lo sugiere pero no falla si no lo haces).

6. **Recarga** con `/reload40`.

7. **Verifica** con `/stats40` que el plugin se conecta correctamente a la API.

## Si algo falla

### "Clave incorrecta" tras actualizar

Probablemente tu clave ha cambiado o expirado. Obtén una nueva en
[40servidoresmc.es/miservidor.php](https://40servidoresmc.es/miservidor.php).

### El broadcast no aparece

Comprueba:
- `broadcast.activado: true` en config.
- Que el jugador que ejecuta `/voto40` tiene votos disponibles (si ya usó el premio hoy, no se emite broadcast).

### Errores en consola tras actualizar

Activa `debug: true` en config y recarga con `/reload40`. Los logs detallados
mostrarán el origen del problema.
