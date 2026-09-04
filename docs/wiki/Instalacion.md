# Instalación

## Requisitos previos

1. Servidor de Minecraft compatible (ver [Compatibilidad con servidores](Compatibilidad-Servidores.md))
2. Java 8 o superior (ver [Compatibilidad con Java](Compatibilidad-Java.md))
3. Una cuenta en [40servidoresmc.es](https://40servidoresmc.es) y tu servidor registrado

## Paso a paso

### 1. Descarga el plugin

Descarga la última versión desde la página de [Releases](https://github.com/richicru/40ServidoresMC/releases).

Para **Bukkit/Spigot/Paper/Purpur**: `40ServidoresMC-3.0-Bukkit.jar`
Para **Sponge**: `40ServidoresMC-3.0-Sponge-API7.jar`

### 2. Instala en tu servidor

Copia el JAR descargado en la carpeta `plugins/` de tu servidor:

```
mi-servidor/
├── plugins/
│   └── 40ServidoresMC-3.0-Bukkit.jar
├── server.jar
└── ...
```

### 3. Reinicia el servidor

Arranca el servidor. El plugin generará `plugins/40ServidoresMC/config.yml` automáticamente.

### 4. Configura la clave del servidor

1. Ve a [40servidoresmc.es/miservidor.php](https://40servidoresmc.es/miservidor.php).
2. Copia tu clave personal del servidor.
3. Edita `plugins/40ServidoresMC/config.yml` y reemplaza el valor de `clave`:

```yaml
clave: "tu-clave-aqui"
```

### 5. Recarga la configuración

En el juego o consola del servidor:

```
/reload40
```

Verás un mensaje confirmando que la configuración se ha recargado correctamente.

### 6. Verifica la instalación

Ejecuta `/stats40` para comprobar que el plugin se conecta a la API y obtiene
las estadísticas de tu servidor.

Si ves la posición en el ranking, votos del día, etc. — todo está OK.

Si ves "Clave incorrecta", vuelve al paso 4 y comprueba que la clave es correcta.

## Permisos

Por defecto, los jugadores no necesitan ningún permiso para usar `/voto40`.
Los comandos administrativos requieren los siguientes permisos:

| Permiso | Comando |
|---|---|
| `40servidores.voto` | `/voto40` (por defecto todos) |
| `40servidores.stats` | `/stats40` |
| `40servidores.test` | `/test40` |
| `40servidores.actualizar` | `/update40` |
| `40servidores.recargar` | `/reload40` |

Para más detalles, ver [Comandos](Comandos.md).
