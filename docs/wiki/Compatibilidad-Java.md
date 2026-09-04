# Compatibilidad con Java

## Versiones soportadas

| Versión Java | Compatible | Notas |
|---|---|---|
| **Java 8** | ✅ | Mínimo soportado (configurado en `build.gradle`) |
| **Java 11** | ✅ | Recomendado para Spigot 1.13+ |
| **Java 17** | ✅ | Recomendado para Paper 1.17+ y Sponge 8+ |
| **Java 21** | ✅ | Última LTS, usado por Paper 1.20.5+ |

## Notas históricas

> **Importante para Java 8 < update 101:**
> Si ejecutas el plugin en una versión de Java 8 anterior a la actualización 101,
> puedes tener problemas con los certificados SSL al conectar con la API.
> La solución es instalar Java 8 update 101 o superior.

Esto ya no aplica para la mayoría de servidores modernos, pero queda aquí como
referencia histórica del wiki original.

## Verificar tu versión de Java

En la consola del servidor, ejecuta:

```
version
```

O desde la línea de comandos:

```bash
java -version
```

## Cambiar de versión de Java

Si tu servidor necesita una versión diferente de Java:

1. Instala la versión de Java deseada.
2. Modifica el script de arranque (`start.sh`, `start.bat`, etc.) para apuntar al nuevo Java.
3. Reinicia el servidor.

Para Paper y forks, también puedes usar scripts como `paperclip` que detectan
la versión de Minecraft y sugieren la versión de Java adecuada.
