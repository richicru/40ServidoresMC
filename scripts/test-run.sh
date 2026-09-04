#!/usr/bin/env bash
# scripts/test-run.sh
# Flujo completo: build -> install -> up -> validate -> down
# (sin reusar caches, para validar desde cero)
set -euo pipefail
cd "$(dirname "$0")/.."

SCOPE="both"
[ "${1:-}" = "--paper" ] && SCOPE="paper"
[ "${1:-}" = "--folia" ] && SCOPE="folia"
DASH=""
[ "$SCOPE" = "paper" ] && DASH="--paper"
[ "$SCOPE" = "folia" ] && DASH="--folia"

cleanup() {
  echo ""
  echo "==> Cleanup: parando servidores..."
  docker compose -f docker-compose.test.yml down 2>&1 | tail -3 || true
}
trap cleanup EXIT

echo "==> Paso 1/4: Construir plugin y copiar a plugins/"
./scripts/install-plugin.sh $DASH

echo ""
echo "==> Paso 2/4: Levantar stack"
./scripts/test-servers-up.sh -d

echo ""
echo "==> Paso 3/4: Esperar a que arranquen (hasta 3 minutos)"
# Healthcheck está definido en compose para cada servicio.
echo "    Esperando healthcheck..."
SERVICES="cs-test-paper"
[ "$SCOPE" != "paper" ] && SERVICES="$SERVICES cs-test-folia"
for svc in $SERVICES; do
  echo "    · $svc..."
  for i in $(seq 1 36); do
    state=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "starting")
    if [ "$state" = "healthy" ]; then
      echo "      ✓ healthy tras ${i}*5 = $((i*5))s"
      break
    fi
    sleep 5
  done
done

echo ""
echo "==> Paso 4/4: Validar logs"
./scripts/test-validate.sh $DASH
