#!/usr/bin/env bash
# scripts/test-vote-e2e-real.sh
#
# Prueba de verdad end-to-end de /voto40: un jugador REAL (bot de protocolo,
# no RCON ni consola) conectado a un Paper y un Folia REALES del stack de
# test, contra el mock-api.
#
# POR QUÉ EXISTE (2026-09-06): scripts/test-vote-scenarios.sh nunca pudo
# automatizar esto -- /voto40 exige un CSCommandSender que no sea consola, así
# que su escenario "manual_vote40" era literalmente un texto pidiéndole a un
# humano que se conectara con un cliente gráfico y mirara. Ese hueco dejó
# pasar sin detectar, hasta que un operador lo reportó en producción, DOS bugs
# reales que solo se manifiestan con un jugador de verdad ejecutando el
# comando:
#   1. BukkitPlugin.dispatchCommand() reprogramaba el comando en el scheduler
#      y leía el resultado ANTES de que se ejecutase -- siempre devolvía
#      false, así que todo voto en v3 se ackeaba con entregado:false y el
#      mismo voto se podía cobrar sin límite.
#   2. En Folia específicamente, EntityScheduler.run()/GlobalRegionScheduler.run()
#      se llamaban con los argumentos en el orden equivocado (o con aridad
#      equivocada): /voto40 se quedaba colgado para siempre (dos hilos de
#      cservidoresmc-io bloqueados sin límite), o revienta con "Dispatching
#      command async" si se llamaba desde el thread de región de una entidad
#      en vez del de la región global.
# Ninguno de los dos se ve con RCON, con curl contra el mock, ni con los 140
# tests unitarios (todos mockean dispatchCommand()). Hacía falta un jugador
# real. Este script lo automatiza con un bot de protocolo (mineflayer) en vez
# de pedirle a un humano que lo haga a mano cada vez.
#
# Requiere el stack arriba: ./scripts/test-servers-up.sh
# Instala node_modules de scripts/e2e-bot/ la primera vez (requiere Internet).
#
# Uso:
#   ./scripts/test-vote-e2e-real.sh              # Paper + Folia, todos los casos
#   ./scripts/test-vote-e2e-real.sh --paper-only
#   ./scripts/test-vote-e2e-real.sh --folia-only
set -uo pipefail
cd "$(dirname "$0")/.."

BOT_DIR="scripts/e2e-bot"
MOCK_URL="http://127.0.0.1:28080"
RCON_PASS="test"
FAILED=0
TOTAL=0

if [ -t 1 ]; then
  C_RED=$'\e[31m'; C_GREEN=$'\e[32m'; C_BOLD=$'\e[1m'; C_RESET=$'\e[0m'
else
  C_RED=""; C_GREEN=""; C_BOLD=""; C_RESET=""
fi
ok()  { TOTAL=$((TOTAL+1)); printf "%b\n" "${C_GREEN}✓${C_RESET}  $*"; }
ko()  { TOTAL=$((TOTAL+1)); FAILED=$((FAILED+1)); printf "%b\n" "${C_RED}✗${C_RESET}  $*"; }
hdr() { printf "\n%b\n" "${C_BOLD}── $* ──${C_RESET}"; }

ONLY=""
for arg in "$@"; do
  case "$arg" in
    --paper-only) ONLY="paper" ;;
    --folia-only) ONLY="folia" ;;
  esac
done

require_stack() {
  hdr "Pre-check: stack arriba"
  for svc in cs-mock-api cs-test-paper cs-test-folia; do
    state=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo missing)
    if [ "$state" = "healthy" ]; then ok "$svc healthy"; else ko "$svc no healthy ($state). Levanta con ./scripts/test-servers-up.sh"; exit 1; fi
  done
}

ensure_bot_deps() {
  if [ ! -d "$BOT_DIR/node_modules" ]; then
    hdr "Instalando dependencias del bot (mineflayer, una vez)"
    (cd "$BOT_DIR" && npm install --no-audit --no-fund -q) || { ko "npm install falló"; exit 1; }
  fi
}

op_bot_users() {
  local container="$1"
  for name in success-alice notvoted-bob already-carol; do
    docker exec "$container" rcon-cli --port 25575 --password "$RCON_PASS" "op $name" > /dev/null 2>&1
  done
}

reset_mock() { curl -s -X POST "$MOCK_URL/__admin/reset" > /dev/null; }

last_ack_body() {
  curl -s "$MOCK_URL/__admin/requests" | python3 -c "
import sys, json
d = json.load(sys.stdin)
acks = [r for r in d if r['path'] == '/api/vote/v3/ack']
print(acks[-1]['body'] if acks else '')
"
}

run_bot() {
  local host="$1" port="$2" user="$3"
  node "$BOT_DIR/vote-bot.js" "$host" "$port" "$user" voto40 8000 2>/dev/null
}

# $1=host $2=port $3=label (Paper/Folia)
test_platform() {
  local host="$1" port="$2" label="$3"

  hdr "$label: success-alice vota, comandos reales, ack entregado:true"
  reset_mock
  out=$(run_bot "$host" "$port" success-alice)
  if echo "$out" | grep -q 'Gracias por votar'; then
    ok "$label: mensaje de éxito recibido en el chat real"
  else
    ko "$label: no llegó el mensaje de éxito. Salida: $out"
  fi
  ack=$(last_ack_body)
  if echo "$ack" | grep -q '"entregado":true'; then
    ok "$label: el ack real que mandó el plugin lleva entregado:true"
  else
    ko "$label: el ack no llevaba entregado:true (body: ${ack:-<vacío, no llegó ack>})"
  fi

  hdr "$label: already-carol, ya canjeó hoy"
  reset_mock
  out=$(run_bot "$host" "$port" already-carol)
  if echo "$out" | grep -qi 'ya has canjeado'; then
    ok "$label: mensaje de 'ya canjeado' correcto"
  else
    ko "$label: mensaje inesperado: $out"
  fi

  hdr "$label: notvoted-bob, nunca ha votado"
  reset_mock
  out=$(run_bot "$host" "$port" notvoted-bob)
  if echo "$out" | grep -qi 'No has votado hoy'; then
    ok "$label: mensaje de 'no has votado' correcto"
  else
    ko "$label: mensaje inesperado: $out"
  fi
}

require_stack
ensure_bot_deps

if [ "$ONLY" != "folia" ]; then
  op_bot_users cs-test-paper
  test_platform 127.0.0.1 25565 Paper
fi
if [ "$ONLY" != "paper" ]; then
  op_bot_users cs-test-folia
  test_platform 127.0.0.1 25566 Folia
fi

hdr "Resumen"
if [ "$FAILED" -eq 0 ]; then
  printf "%b\n" "${C_GREEN}${C_BOLD}TODOS LOS ESCENARIOS REALES PASARON ($TOTAL/$TOTAL)${C_RESET}"
else
  printf "%b\n" "${C_RED}${C_BOLD}FALLARON $FAILED de $TOTAL${C_RESET}"
  exit 1
fi
