# Configuración

El plugin se configura desde `plugins/40ServidoresMC/config.yml` (Bukkit) o
`config/40ServidoresMC.conf` (Sponge).

## Configuración por defecto

```yaml
debug: false
clave: key
broadcast:
    activado: true
    mensajeBroadcast: '&aGracias a {0} por votarnos! El ya ha obtenido su premio, usa &2/voto40&a y obten el tuyo'
mensaje: '&6Gracias por votarnos! Aqui tienes tu premio: '
comandosCustom:
- money add {0} 10
readTimeOut: 5000
cooldown: 60
update-branch: dev
log-ip: false
tag: "&8[&b40ServidoresMC&8]"

# Mensajes traducibles. Vacío por defecto.
messages: {}

configVer: 4
```

## Referencia de claves

| Clave | Tipo | Default | Descripción |
|---|---|---|---|
| `debug` | bool | `false` | Activa logs de depuración en consola. |
| `clave` | string | `"key"` | Clave del servidor (obtenida en 40servidoresmc.es). **Reemplazar siempre**. |
| `broadcast.activado` | bool | `true` | Si true, anuncia el voto a todos los jugadores. |
| `broadcast.mensajeBroadcast` | string | (ver arriba) | Mensaje del broadcast. `{0}` se sustituye por el nombre del jugador. |
| `mensaje` | string | `"&6Gracias..."` | Mensaje que recibe el jugador al canjear el voto. |
| `comandosCustom` | lista | `["money add {0} 10"]` | Comandos a ejecutar como recompensa. `{0}` = jugador. |
| `readTimeOut` | int | `5000` | Timeout de lectura HTTP (ms). |
| `cooldown` | int | `60` | Segundos entre validaciones de voto por jugador. |
| `update-branch` | string | `"dev"` | Rama de GitHub para buscar actualizaciones. |
| `log-ip` | bool | `false` | Si true, registra `[VoteReward] player=X ip=Y` tras cada voto. |
| `tag` | string | `"&8[&b40ServidoresMC&8]"` | Prefijo en mensajes al jugador. |
| `messages.*` | mapa | `{}` | Mensajes traducibles (ver [Mensajes](Mensajes.md)). |
| `configVer` | int | `4` | Versión de la configuración (no modificar manualmente). |

## Colores

Los códigos `&` se traducen a colores Minecraft:

| Código | Color |
|---|---|
| `&0` a `&9` | Colores básicos |
| `&a` a `&f` | Colores claros |
| `&l` | Negrita |
| `&o` | Itálica |
| `&n` | Subrayado |
| `&m` | Tachado |
| `&k` | Ofuscado |
| `&r` | Reset |

Ejemplo: `"&aVerde &lnegrita &rreset"` muestra "Verde **negrita** reset".

## Comandos personalizados (recompensas)

Cada vez que un jugador vota correctamente, se ejecuta cada línea de
`comandosCustom` como si fuera la consola. `{0}` se reemplaza por el nombre
del jugador.

Ejemplos:

```yaml
comandosCustom:
- "give {0} diamond 5"
- "eco give {0} 1000"
- "lp user {0} permission set vip.true"
- "broadcast {0} ha votado y ha recibido su premio!"
```

## Códigos de color hexadecimales (1.16+)

Si tu servidor soporta Minecraft 1.16+, puedes usar códigos hexadecimales:

```yaml
mensaje: "&#FF5555Gracias por votar!"
```

## Validación al arrancar

Al iniciar, el plugin comprueba:

- Que `configVer` coincida con la versión esperada. Si no, muestra un warning.
- Que `clave` no sea el valor por defecto `"key"`. Si lo es, el plugin no funcionará.

Ambos mensajes aparecen al inicio en la consola del servidor.
