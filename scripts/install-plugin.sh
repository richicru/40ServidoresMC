#!/usr/bin/env bash
# scripts/install-plugin.sh
# Construye el JAR de Bukkit con shadowJar y lo copia a las carpetas plugins/
# de Paper y Folia del entorno de testing Docker.
#
# Uso:
#   ./scripts/install-plugin.sh                (instala en ambos)
#   ./scripts/install-plugin.sh --paper        (solo Paper)
#   ./scripts/install-plugin.sh --folia        (solo Folia)
#   ./scripts/install-plugin.sh --rebuild      (recompila limpio)
set -euo pipefail
cd "$(dirname "$0")/.."

REBUILD_ONLY=0
PAPER_ONLY=0
FOLIA_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD_ONLY=1 ;;
    --paper)   PAPER_ONLY=1 ;;
    --folia)   FOLIA_ONLY=1 ;;
    *) echo "Argumento desconocido: $arg"; exit 1 ;;
  esac
done

echo "==> Building plugin shadowJar..."
GRADLE_OPTS="-Dorg.gradle.daemon=false" \
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}" \
  bash gradlew --console=plain :bukkit:shadowJar -q

JAR=$(ls bukkit/build/libs/40ServidoresMC-*-Bukkit.jar 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
  echo "ERROR: no se encontró el JAR. ¿Falló el build?" >&2
  exit 1
fi
echo "==> JAR: $JAR"

install_to() {
  local dir="$1" label="$2"
  mkdir -p "$dir"
  # limpia copias anteriores y deja un único JAR bien nombrado
  rm -f "$dir"/40ServidoresMC-*-Bukkit.jar
  cp "$JAR" "$dir/"
  echo "  ✓ Copiado a $label -> $dir/$(basename "$JAR")"
}

if [ "$FOLIA_ONLY" -eq 0 ] && [ "$REBUILD_ONLY" -eq 0 ]; then
  install_to ".docker/paper/plugins" "Paper"
fi
if [ "$PAPER_ONLY" -eq 0 ] && [ "$REBUILD_ONLY" -eq 0 ]; then
  install_to ".docker/folia/plugins" "Folia"
fi

if [ "$REBUILD_ONLY" -eq 1 ]; then
  echo "==> --rebuild: sólo se ha recompilado, no se ha copiado nada."
fi
echo "==> OK"
