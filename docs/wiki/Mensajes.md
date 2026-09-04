# Mensajes traducibles

A partir de la versión 3.0, todos los mensajes visibles al usuario pueden
sobrescribirse desde `config.yml` bajo la sección `messages.*`.

Si una clave no está presente, se usa el valor por defecto en español.

## Ejemplo: traducir al inglés

```yaml
messages:
  vote-fetching: "&7Loading vote..."
  vote-already-rewarded: "&aThanks for voting, but you already got your reward!"
  vote-not-voted-prefix: "&6You haven't voted today! You can do so at &a"
  vote-invalid-key: "&cInvalid key. Visit &bhttps://40servidoresmc.es/miservidor.php &cand fix it."
  vote-error: "&7An error occurred. Try again later or contact an admin"
  vote-exception: "&cAn exception occurred. Please notify an admin"

  stats-invalid-key: "&cInvalid key..."
  stats-header: "&9==> &7{server} &fis at TOP &a#{position}"
  stats-day-votes: "&bVotes today: &6{count}"
  stats-day-votes-rewarded: "&bRewarded votes today: &6{count}"
  stats-week-votes: "&bWeekly votes: &6{count}"
  stats-week-votes-rewarded: "&bRewarded weekly votes: &6{count}"
  stats-last-votes: "&bLast 20 votes: {votes}"
  stats-exception: "&cAn exception occurred. Check the console or notify an admin"

  cmd-no-permission: "&cYou don't have permission to use this command"
  cmd-cooldown: "&6You can't use this command so many times in a row!"
  cmd-error: "&cAn unexpected error occurred"
  cmd-only-player: "&cThis command can only be run by players"

  reload-success: "&aConfiguration reloaded successfully"
  reload-version: "&aRunning version {version}"

  test-header: "&b40ServidoresMC test platform:"

  update-no-info: "Could not retrieve version information."
  update-no-new: "No newer recommended version for your Minecraft version."
```

## Placeholders disponibles

Algunos mensajes aceptan placeholders que se sustituyen dinámicamente:

| Mensaje | Placeholder | Valor |
|---|---|---|
| `stats-header` | `{server}` | Nombre del servidor |
| `stats-header` | `{position}` | Posición en el ranking |
| `stats-day-votes`, etc. | `{count}` | Número de votos |
| `stats-last-votes` | `{votes}` | Lista formateada de últimos votos |
| `reload-version` | `{version}` | Versión del plugin |

## Notas

- Los placeholders usan la sintaxis `{nombre}` (estilo Java MessageFormat).
- Cualquier clave no presente usa el valor por defecto en español.
- Los códigos `&` para colores Minecraft siguen funcionando en los mensajes personalizados.
- Los mensajes traducibles se recargan con `/reload40`.
