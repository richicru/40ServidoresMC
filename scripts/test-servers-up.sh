#!/usr/bin/env bash
# scripts/test-servers-up.sh
# Levanta el stack Paper+Folia (descarga imágenes a la primera vez).
# Reusa caches de contenedor. Configurado para no consumir CPU excesiva.
#
# Uso:
#   ./scripts/test-servers-up.sh          (levanta y se queda en foreground)
#   ./scripts/test-servers-up.sh -d       (background)
set -euo pipefail
cd "$(dirname "$0")/.."

DETACH="-d"
for arg in "$@"; do
  case "$arg" in
    -d|--detach) DETACH="-d" ;;
    -f|--foreground) DETACH="" ;;
  esac
done

echo "==> Limpiando contenedores previos..."
docker compose -f docker-compose.test.yml down --remove-orphans 2>&1 | grep -vE "^$" || true

echo "==> Levantando stack (esto puede tardar: descarga Paper/Folia y los levanta)..."
docker compose -f docker-compose.test.yml up $DETACH

if [ -n "$DETACH" ]; then
  echo ""
  echo "==> Para ver logs en directo: docker compose -f docker-compose.test.yml logs -f"
  echo "==> Para ver logs de un servicio: docker logs -f cs-test-paper  (o cs-test-folia)"
  echo "==> Estado: docker compose -f docker-compose.test.yml ps"
fi
