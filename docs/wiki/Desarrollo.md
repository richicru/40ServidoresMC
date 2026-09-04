# Desarrollo

Esta página está dirigida a quien quiera compilar, testear o contribuir al plugin.

## Requisitos

- **Java 8** o superior (recomendado Java 17).
- **Gradle** (incluido como wrapper `./gradlew`).

## Clonar el repositorio

```bash
git clone https://github.com/richicru/40ServidoresMC.git
cd 40ServidoresMC
```

## Estructura del proyecto

```
40ServidoresMC/
├── common/          # Lógica independiente de plataforma
│   ├── src/main/    # Código fuente
│   └── src/test/    # Tests unitarios (JUnit 5)
├── bukkit/          # Implementación Bukkit/Spigot/Paper
├── sponge/
│   └── api7/        # Implementación Sponge API 7
├── docs/            # Documentación técnica
│   ├── SPEC.md
│   ├── MEJORAS.md
│   ├── SERVERS.md
│   ├── FOLIA.md
│   └── wiki/        # Documentación wiki-style
├── CHANGELOG.md
├── README.md
└── build.gradle
```

## Compilar

```bash
./gradlew :bukkit:shadowJar      # JAR de Bukkit
./gradlew :sponge:api7:shadowJar # JAR de Sponge
./gradlew build                   # Compilar todo
```

Los JARs resultantes aparecen en:

- `bukkit/build/libs/40ServidoresMC-3.0-Bukkit.jar`
- `sponge/api7/build/libs/40ServidoresMC-3.0-Sponge-API7.jar`

## Tests

```bash
./gradlew :common:test
```

74 tests cubren el núcleo del plugin:

- Cooldown (6)
- VoteStatus deserialization (8)
- ServerStats parsing (7)
- UpdaterInfo (6)
- CSCommand authorization (7)
- ApiClient HTTP (8)
- VoteCMD flow (8)
- StatsCMD flow (5)
- MessageKey catalog (8)
- StatsCache (7)

## Linting

El proyecto no tiene reglas de linting específicas. Sigue el estilo del código existente:

- 4 espacios de indentación.
- Llaves en líneas separadas para clases y métodos.
- `final` en campos inmutables cuando aplique.
- Javadoc en clases públicas y métodos públicos.

## Contribuir

1. Fork el repositorio.
2. Crea una rama: `git checkout -b mi-mejora`.
3. Commit con mensajes descriptivos.
4. Asegúrate de que los tests pasan: `./gradlew :common:test`.
5. Push a tu fork.
6. Abre un Pull Request.

### Áreas donde se aceptan contribuciones

- 🟢 Nuevos placeholders para PlaceholderAPI.
- 🟢 Traducciones de mensajes (`messages.*` por idioma).
- 🟢 Documentación y tutoriales.
- 🟡 Compatibilidad con Folia (ver [FOLIA.md](../FOLIA.md)).
- 🟡 Soporte para BungeeCord/Velocity (módulo nuevo).
- 🟢 Tests adicionales.

### Antes de hacer un PR

- Actualiza [CHANGELOG.md](../../../CHANGELOG.md) si añades funcionalidad visible.
- Si es un cambio mayor (breaking change), actualiza también [SPEC.md](../SPEC.md).
- Si introduces una nueva clave de configuración, documéntala en [Configuracion.md](Configuracion.md).

## Issues

Usa [GitHub Issues](https://github.com/richicru/40ServidoresMC/issues) para:

- 🐛 Reportar bugs (incluye versión del plugin, del servidor, de Minecraft y logs).
- 💡 Sugerir mejoras.
- ❓ Preguntas generales (mejor en Discussions si está habilitado).
