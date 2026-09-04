#!/usr/bin/env bash
# scripts/test-servers-down.sh
# Para y borra los contenedores del entorno de testing (no borra los jars en plugins/).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Parando y borrando contenedores..."
docker compose -f docker-compose.test.yml down
echo "==> OK"
