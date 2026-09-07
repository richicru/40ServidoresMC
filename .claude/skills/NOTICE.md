# Origen de las skills de este directorio

`minecraft-plugin-dev/` y `minecraft-testing/` proceden de
[Jahrome907/minecraft-agent-skills](https://github.com/Jahrome907/minecraft-agent-skills)
(MIT License, copyright (c) 2026 Jahrome907), copiadas tal cual siguiendo el
método de instalación que documenta su propio README ("Copy only the selected
skill folders"). No son contenido propio de este repositorio.

Elegidas frente a otras alternativas encontradas (`minecraft-bukkit-pro` de
`sickn33/antigravity-awesome-skills`, la skill de `github/awesome-copilot`)
porque su contenido es concreto, verificable y está al día con Paper
26.x/Java 25 -- lo mismo que corre `docker-compose.test.yml` -- y porque su
guía de schedulers de Folia (`minecraft-plugin-dev/references/runtime-patterns.md`)
coincide byte a byte con la firma real de `EntityScheduler.run()` /
`GlobalRegionScheduler.run()` verificada extrayendo las clases del jar real de
Folia (ver el changelog del bugfix de scheduling de 2026-09-06): confirmación
independiente de que el fix aplicado ese día era correcto.

No se instaló `minecraft-ci-release` (el proyecto no tiene CI, y esa skill
está pensada para mods NeoForge/Fabric, no para plugins Bukkit) ni
`minecraft-essentials-ops` (opera el plugin de terceros EssentialsX, que este
proyecto no usa).

Si se actualizan, volver a copiar desde el repo origen en vez de editar estos
ficheros a mano -- son una copia externa, no documentación propia.
