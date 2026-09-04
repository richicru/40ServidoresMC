# PlaceholderAPI

El plugin expone 6 placeholders para usar con PlaceholderAPI en otros plugins
(TAB, scoreboard, chat format, etc.).

## Instalación

1. Instala [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) en tu servidor.
2. Instala este plugin (`40ServidoresMC`). El hook se registra automáticamente.

## Placeholders disponibles

Todos empiezan por `%40servidoresmc_` y terminan con el identificador entre `%`.

### Posición en el ranking

```
%40servidoresmc_position%
```

Retorna la posición actual del servidor en el ranking de 40servidoresmc.es.

Ejemplo: `Servidor en el TOP #5`

### Votos del día

```
%40servidoresmc_day_votes%
```

Número total de votos recibidos hoy.

### Votos premiados hoy

```
%40servidoresmc_day_votes_rewarded%
```

Número de votos de hoy que han sido canjeados (recompensados).

### Votos semanales

```
%40servidoresmc_week_votes%
```

Número total de votos esta semana.

### Votos premiados semanales

```
%40servidoresmc_week_votes_rewarded%
```

Número de votos semanales que han sido canjeados.

### Nombre del servidor

```
%40servidoresmc_server_name%
```

Nombre del servidor tal como aparece registrado en 40servidoresmc.es.

## Caché

El plugin usa una caché interna de **5 minutos** para evitar llamadas repetidas
a la API. Esto significa que los valores pueden tardar hasta 5 minutos en
actualizarse tras un cambio real.

La caché se renueva automáticamente en segundo plano. Si necesitas forzar
un refresco inmediato, usa `/stats40` que actualiza la caché directamente.

## Ejemplo de uso

Con **TAB**:
```
&6#%40servidoresmc_position% &7%40servidoresmc_server_name%
```

Con **scoreboard**:
```
&e&lVotos del día: &f%40servidoresmc_day_votes%
&e&lPremiados: &a%40servidoresmc_day_votes_rewarded%
&e&lPosición: &6#%40servidoresmc_position%
```

## Si los placeholders no se resuelven

Si ves `%40servidoresmc_position%` literal en el chat o scoreboard:

1. Verifica que PlaceholderAPI está instalado: `/papi list`.
2. Verifica que el hook del plugin está cargado: debería aparecer en `/papi ecloud download 40servidoresmc` o ya cargado.
3. Si la API falla, los placeholders devuelven string vacío `""`. Comprueba la clave `clave` en config.
