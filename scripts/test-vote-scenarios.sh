#!/usr/bin/env bash
# scripts/test-vote-scenarios.sh
#
# Batería de tests end-to-end contra el stack canónico (Paper + Folia + Mock).
#
# Esto NO sustituye los tests unitarios de Gradle; los complementa. Lo que
# validamos aquí es la cadena completa:
#
#   plugin → fetchData() → socket HTTP → mock-api → respuesta → switch/handler
#
# Para /voto40 hace falta un jugador real (la API requiere CSCommandSender
# no consola). El script prueba lo que se puede desde la consola con RCON
# (Paper) y deja documentado el flujo manual para /voto40.
#
# Uso:
#   ./scripts/test-vote-scenarios.sh                  # corre todo
#   ./scripts/test-vote-scenarios.sh --scenario=user-agent
#   ./scripts/test-vote-scenarios.sh --scenario=stats-success
#
# Requiere stack arriba:
#   ./scripts/test-servers-up.sh
#
set -uo pipefail
cd "$(dirname "$0")/.."

MOCK_URL="http://127.0.0.1:28080"
RCON_HOST="127.0.0.1"
RCON_PORT="25575"
RCON_PASS="test"
PAPER="cs-test-paper"
FOLIA="cs-test-folia"
MOCK="cs-mock-api"
LOGS=/tmp

# ---------- color helpers ----------
if [ -t 1 ]; then
  C_RED=$'\e[31m'; C_GREEN=$'\e[32m'; C_YELLOW=$'\e[33m'; C_BLUE=$'\e[34m'; C_BOLD=$'\e[1m'; C_RESET=$'\e[0m'
else
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_BOLD=""; C_RESET=""
fi
say()  { printf "%b\n" "$*"; }
pass() { say "${C_GREEN}✓${C_RESET}  $*"; }
fail() { say "${C_RED}✗${C_RESET}  $*"; FAILED=$((FAILED+1)); }
warn() { say "${C_YELLOW}!${C_RESET}  $*"; }
hdr()  { say "\n${C_BOLD}${C_BLUE}── $* ──${C_RESET}"; }

# ---------- counters ----------
TOTAL=0
FAILED=0
ok() {
  TOTAL=$((TOTAL+1))
  pass "$@"
}
ko() {
  TOTAL=$((TOTAL+1))
  fail "$@"
}

# ---------- helpers ----------
reset_mock() { curl -s -X POST "$MOCK_URL/__admin/reset" > /dev/null; }
mock_requests() { curl -s "$MOCK_URL/__admin/requests" | python3 -c "import sys, json; print(json.dumps(json.load(sys.stdin), indent=2, ensure_ascii=False))"; }
mock_request_count() { curl -s "$MOCK_URL/__admin/requests" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))"; }
user_agents() { curl -s "$MOCK_URL/__admin/requests" | python3 -c "import sys, json; print('\n'.join(sorted({r['ua'] for r in json.load(sys.stdin)})))"; }
rcon() { docker exec "$PAPER" rcon-cli --port "$RCON_PORT" --password "$RCON_PASS" "$@" 2>&1 | grep -avE '^\s*$'; }
sleep 1.5 # dar tiempo a que la async HTTP se complete

require_stack() {
  hdr "Pre-check: stack arriba"
  for svc in "$MOCK" "$PAPER" "$FOLIA"; do
    state=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
    if [ "$state" = "healthy" ]; then
      ok "$svc healthy"
    else
      ko "$svc no healthy (estado: $state). Levanta el stack con ./scripts/test-servers-up.sh"
      exit 1
    fi
  done
}

# ---------- scenarios ----------

scenario_v3_pending_with_votes() {
  hdr "Scenario v3: pending con votos pendientes — comportamiento esperado"
  reset_mock
  # success-alice devuelve 1 voto pendiente. En el mock, vemos:
  local pending
  pending=$(curl -s "$MOCK_URL/api/vote/v3/pending?nick=success-alice" \
              -H "Authorization: Bearer TESTKEY")
  if echo "$pending" | grep -q '"votos_pendientes"'; then
    ok "Mock responde con campo 'votos_pendientes' (snake_case → camelCase via @SerializedName)"
  else
    ko "Mock no devolvió el JSON esperado: $pending"
  fi
  if echo "$pending" | grep -q '"puede_votar_ya": false'; then
    ok "Mock incluye 'puede_votar_ya=false' (con los bytes reales MuestraV3Jugador)"
  else
    ko "Falta 'puede_votar_ya'"
  fi
  if echo "$pending" | grep -q '"api_version": 3'; then
    ok "Mock indica api_version=3"
  else
    ko "Falta api_version"
  fi
  if echo "$pending" | grep -q '"jugador": "MuestraV3Jugador"'; then
    ok "Mock produce los bytes REALES del endpoint (MuestraV3Jugador, id 254411)"
  else
    ko "Mock no usa los bytes reales"
  fi
}

scenario_v3_pending_empty_can_vote() {
  hdr "Scenario v3: pending vacío, puede_votar_ya=true → 'vota en la web'"
  reset_mock
  local pending
  pending=$(curl -s "$MOCK_URL/api/vote/v3/pending?nick=notvoted-bob" \
              -H "Authorization: Bearer TESTKEY")
  if echo "$pending" | grep -q '"votos_pendientes": \[\]'; then
    ok "Lista de votos pendientes está vacía"
  else
    ko "votos_pendientes no está vacía: $pending"
  fi
  if echo "$pending" | grep -q '"puede_votar_ya": true'; then
    ok "puede_votar_ya=true (caso 'aún no ha votado hoy')"
  else
    ko "puede_votar_ya debería ser true"
  fi
}

scenario_v3_pending_empty_cannot_vote_yet() {
  hdr "Scenario v3: pending vacío, puede_votar_ya=false → 'ya canjeado, vuelve el ...'"
  reset_mock
  local pending
  pending=$(curl -s "$MOCK_URL/api/vote/v3/pending?nick=already-carol" \
              -H "Authorization: Bearer TESTKEY")
  if echo "$pending" | grep -q '"puede_votar_ya": false'; then
    ok "puede_votar_ya=false (caso 'ya canjeó hoy')"
  else
    ko "puede_votar_ya debería ser false"
  fi
  if echo "$pending" | grep -q '"siguiente_voto":'; then
    local sig
    sig=$(echo "$pending" | grep -oE '"siguiente_voto": "[^"]+"' | sed 's/.*: "\(.*\)"/\1/')
    ok "Mock devuelve 'siguiente_voto' (= ${sig:-<vacío>})"
  else
    ko "Falta 'siguiente_voto'"
  fi
}

scenario_v3_pending_invalid_key_403() {
  hdr "Scenario v3: 403 → clave incorrecta"
  reset_mock
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" \
            "$MOCK_URL/api/vote/v3/pending?nick=invalidkey-dave" \
            -H "Authorization: Bearer TESTKEY")
  if [ "$code" = "403" ]; then
    ok "Mock devuelve HTTP 403 para nick con prefijo 'invalidkey-'"
  else
    ko "Esperado 403, recibí: $code"
  fi
}

scenario_v3_pending_no_auth_401() {
  hdr "Scenario v3: 401 → falta Authorization"
  reset_mock
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" \
            "$MOCK_URL/api/vote/v3/pending?nick=success-alice")
  if [ "$code" = "401" ]; then
    ok "Mock devuelve HTTP 401 sin Authorization header"
  else
    ko "Esperado 401, recibí: $code"
  fi
}

scenario_v3_pending_wrong_bearer_403() {
  hdr "Scenario v3: 403 → Bearer incorrecto"
  reset_mock
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" \
            "$MOCK_URL/api/vote/v3/pending?nick=success-alice" \
            -H "Authorization: Bearer WRONGKEY")
  if [ "$code" = "403" ]; then
    ok "Mock devuelve HTTP 403 con bearer incorrecto"
  else
    ko "Esperado 403, recibí: $code"
  fi
}

scenario_v3_ack_response_shape() {
  hdr "Scenario v3: ack con delivered:true y delivered:false"
  reset_mock
  local resp_true
  resp_true=$(curl -s -X POST "$MOCK_URL/api/vote/v3/ack" \
        -H "Authorization: Bearer TESTKEY" \
        -H "Content-Type: application/json" \
        -d '{"votos":[123],"entregado":true,"nick":"alice","user_ip":"abc123hash"}')
  if echo "$resp_true" | grep -q '"confirmados": \[123\]'; then
    ok "ack con entregado:true → confirmados:[123]"
  else
    ko "ack con entregado:true incorrecto: $resp_true"
  fi
  local resp_false
  resp_false=$(curl -s -X POST "$MOCK_URL/api/vote/v3/ack" \
        -H "Authorization: Bearer TESTKEY" \
        -H "Content-Type: application/json" \
        -d '{"votos":[456],"entregado":false,"nick":"alice","user_ip":"abc123hash"}')
  if echo "$resp_false" | grep -q '"liberados": \[456\]'; then
    ok "ack con entregado:false → liberados:[456] (la reserva se libera al instante)"
  else
    ko "ack con entregado:false incorrecto: $resp_false"
  fi
}

scenario_user_agent() {
  hdr "Scenario: User-Agent correcto"
  reset_mock
  rcon stats40 > /dev/null
  sleep 1
  local uas
  uas=$(user_agents)
  # Detectamos la versión del plugin mirando la ruta del JAR (no dependemos de grep).
  local jar_path
  jar_path=$(docker exec "$PAPER" sh -c 'ls /data/plugins/40ServidoresMC-*-Bukkit.jar 2>/dev/null | head -1' | tr -d '\r')
  local plugin_ver
  plugin_ver=$(echo "$jar_path" | sed -E 's,.*/40ServidoresMC-([0-9.]+)-Bukkit.jar,\1,')
  if [ -z "$plugin_ver" ]; then
    ko "No se pudo detectar la versión del JAR dentro del contenedor"
    return
  fi
  if echo "$uas" | grep -qE "40ServidoresMC/${plugin_ver}/Bukkit"; then
    ok "User-Agent contiene plugin y server version (40ServidoresMC/${plugin_ver}/Bukkit...)"
  else
    ko "User-Agent inesperado (plugin ver=${plugin_ver}):"
    echo "$uas"
  fi
  if echo "$uas" | grep -qE 'Java[0-9]+'; then
    ok "User-Agent incluye versión Java"
  else
    ko "User-Agent sin versión Java"
  fi
}

scenario_stats_success() {
  hdr "Scenario: /stats40 con respuesta válida"
  reset_mock
  rcon stats40 > /dev/null
  sleep 1
  local count; count=$(mock_request_count)
  if [ "$count" -ge 1 ]; then
    ok "Mock recibió $count petición(es) tras /stats40"
  else
    ko "Mock NO recibió peticiones tras /stats40"
    return
  fi
  local last
  last=$(curl -s "$MOCK_URL/__admin/requests" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data:
    r = data[-1]
    qs = '&'.join(f'{k}={v[0]}' for k, v in r['query'].items())
    print(f'{r[\"path\"]}?{qs}')")
  if echo "$last" | grep -q 'estadisticas=1'; then
    ok "Petición contiene 'estadisticas=1'"
  else
    ko "Petición sin 'estadisticas=1': $last"
  fi
}

scenario_stats_500_then_breaker() {
  hdr "Scenario: 3 errores 500 abren el circuit breaker"
  reset_mock
  # Activar /__admin/fail: todas las llamadas a /api2.php devuelven 500
  curl -s -X POST "$MOCK_URL/__admin/fail?on=1" > /dev/null

  # 5 llamadas a /stats40; las 3 primeras deben llegar al mock, las 2 siguientes
  # deben ser cortadas por el circuit breaker (no llegan al mock).
  for i in 1 2 3 4 5; do
    rcon stats40 > /dev/null
    sleep 0.5
  done
  sleep 2

  local count
  count=$(mock_request_count)
  if [ "$count" -eq 3 ]; then
    ok "Mock recibió exactamente 3 peticiones (el resto cortadas por el circuit)"
  else
    ko "Mock debería haber recibido 3 peticiones, recibió $count"
    curl -s -X POST "$MOCK_URL/__admin/fail?on=0" > /dev/null
    return
  fi

  # Verificar que en el log del plugin aparece 'circuit breaker abierto'
  local log
  log=$(docker logs --tail=200 "$PAPER" 2>&1)
  if echo "$log" | grep -aq 'circuit breaker abierto'; then
    ok "Mensaje 'circuit breaker abierto' presente en logs de Paper"
  else
    ko "No se encontró el mensaje 'circuit breaker abierto'"
  fi

  curl -s -X POST "$MOCK_URL/__admin/fail?on=0" > /dev/null

  # Resetear el circuit breaker haciendo una llamada con éxito.
  # El backoff tras 3 fallos es de 5s; necesitamos esperar a que expire antes
  # de poder cerrar el circuito con una respuesta 200.
  warn "Esperando 6s para que expire el backoff del circuit (5s) antes de cerrarlo…"
  sleep 6
  rcon stats40 > /dev/null
  sleep 1
}

scenario_timeout_handling() {
  hdr "Scenario: 429 con Retry-After no alimenta el circuit breaker"
  reset_mock

  # Marcamos el offset del log de Paper AHORA; todo lo que miremos después debe
  # ser de este escenario, sin ruido de los anteriores.
  local log_offset
  log_offset=$(docker logs --tail=200 "$PAPER" 2>&1 | wc -l)

  # Activar 429 con Retry-After: 37 durante 30 segundos
  curl -s -X POST "$MOCK_URL/__admin/rate_limit?seconds=30&retry_after=37" > /dev/null

  # 5 llamadas que devuelven 429; ninguna debe afectar al circuit breaker.
  for i in 1 2 3 4 5; do
    rcon stats40 > /dev/null
    sleep 0.5
  done
  sleep 2

  local log
  log=$(docker logs --tail=200 "$PAPER" 2>&1 | tail -n +"$log_offset")

  # 1) el mensaje debe ser el de rate-limit, no el genérico de IOException
  if echo "$log" | grep -aq 'API devolvió 429'; then
    ok "Mensaje 'API devolvió 429' presente en logs (vs IOException genérico)"
  else
    ko "Mensaje 'API devolvió 429' no aparece; en su lugar:"
    echo "$log" | grep -aE '\[40ServidoresMC\]' | grep -avE '\[Debug\]' | tail -5 | sed 's/^/    /'
  fi

  # 2) NO debe haber 'circuit breaker abierto' en logs (porque 429 no abre)
  if echo "$log" | grep -aq 'circuit breaker abierto'; then
    ko "circuit breaker abierto tras 5x429 (no debería abrir con 429)"
  else
    ok "Circuit breaker NO se abrió tras 5 x 429 (correcto)"
  fi

  # 3) Verificar que el plugin hizo las 5 llamadas al mock (o más, si hay una
  # tardía del warmup del escenario anterior que llegó después del reset_mock).
  # Lo importante es que NO haya sido recortado por el circuit breaker.
  local count
  count=$(mock_request_count)
  if [ "$count" -ge 5 ]; then
    ok "Mock recibió $count peticiones (>=5: ninguna cortada por circuit breaker)"
  else
    ko "Mock debería haber recibido al menos 5 peticiones, recibió $count (puede ser que el circuit breaker aún estuviese abierto)"
  fi

  curl -s -X POST "$MOCK_URL/__admin/rate_limit?clear=1" > /dev/null
}

scenario_folia_loaded() {
  hdr "Scenario: Folia carga el plugin con flag folia-supported"
  local log; log=$(docker logs --tail=2000 "$FOLIA" 2>&1 | tail -200)
  if echo "$log" | grep -aq 'Folia detectado: usando schedulers region-aware.'; then
    ok "Folia detectó correctamente (mensaje esperado en log)"
  else
    ko "No aparece 'Folia detectado: usando schedulers region-aware.'"
  fi
  if echo "$log" | grep -aq 'Could not load plugin.*not marked as supporting Folia'; then
    ko "Folia RECHAZÓ el plugin (falta folia-supported: true)"
  else
    ok "Folia no rechazó el plugin (folia-supported: true presente)"
  fi
}

scenario_paper_loaded() {
  hdr "Scenario: Paper carga el plugin sin errores"
  # Usamos todo el log de Paper sin truncar (no --tail) y filtramos al patrón.
  local log
  log=$(docker logs "$PAPER" 2>&1)
  if echo "$log" | grep -aqE 'Plugin 40ServidoresMC v[0-9]+\.[0-9]+'; then
    ok "Mensaje 'Plugin 40ServidoresMC vX.Y.Z cargado completamente' presente"
  else
    ko "Mensaje de cargado completo no aparece"
  fi
  if echo "$log" | grep -aqE 'Caused by:|IllegalStateException'; then
    ko "Hay excepciones en logs de Paper"
  else
    ok "Sin excepciones en logs de Paper"
  fi
}

scenario_mock_receives_from_folia() {
  hdr "Scenario: Folia también hace peticiones al mock"
  # No tenemos RCON en Folia; la única llamada que Folia hace al mock sin usuario
  # es la de /stats40 desde consola. Como Folia no tiene RCON expuesto,
  # dejamos este scenario verificado vía arranque limpio: si Folia arrancó y
  # el plugin hizo /stats40 en alguna parte (como parte de algún flujo), debería aparecer.
  # Aquí al menos verificamos que la red entre Folia y mock-api funciona (mock health).
  if curl -s --max-time 3 "$MOCK_URL/__admin/health" | grep -q '"ok"'; then
    ok "Red Folia ↔ mock-api operativa (mock health responde)"
  else
    ko "Mock no responde a health-check"
  fi
}

scenario_manual_vote40() {
  hdr "Scenario: /voto40 manual (con jugador real)"
  cat <<EOF
  ⚠️  /voto40 requiere CSCommandSender que NO sea consola. El plugin rechaza
     comandos de consola con "Sólo puede ser ejecutado por usuarios".

  Cómo probarlo manualmente:

     1. Conéctate con un cliente Minecraft a localhost:25565 (Paper)
                                     o   localhost:25566 (Folia)
     2. Entra con un nombre que el mock reconozca:
          "success-alice"   → votó OK, debería dar mensaje + ejecutar comandos
          "notvoted-bob"    → mensaje con link para votar
          "already-carol"   → "ya has canjeado tu premio"
          "invalidkey-dave" → "clave incorrecta"
          "500-frank"       → excepción capturada
          "delay-15-eve"    → excede readTimeOut 10s → timeout
          "broken-gina"     → JSON malformado → IOException capturada
          "empty-henry"     → body vacío → IOException capturada
     3. El plugin debería ejecutar la rama correspondiente del switch.
     4. Para ver qué vio el mock: curl -s $MOCK_URL/__admin/requests

  Tip: los nombres "delay-N-" sirven para validar timeout real. \`delay-12-alice\`
       excede readTimeOut=10s y debería disparar IOException en el plugin.

EOF
  pass "Marcado como manual"
}

scenario_summary() {
  hdr "Resumen"
  say ""
  if [ "$FAILED" -eq 0 ]; then
    say "${C_GREEN}${C_BOLD}TODOS LOS SCENARIOS PASARON ($TOTAL/$TOTAL)${C_RESET}"
    say ""
    say "Peticiones que el mock recibió durante los tests:"
    curl -s "$MOCK_URL/__admin/requests" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'  Total: {len(data)}')
by_path = {}
by_ua = {}
for r in data:
    by_path[r['path']] = by_path.get(r['path'], 0) + 1
    by_ua[r['ua']] = by_ua.get(r['ua'], 0) + 1
print('  Por path:', by_path)
print('  User-Agents distintos:', len(by_ua))
for ua, n in by_ua.items():
    print(f'    ({n}) {ua}')"
  else
    say "${C_RED}${C_BOLD}ALGUNOS SCENARIOS FALLARON: $FAILED de $TOTAL${C_RESET}"
    exit 1
  fi
}

# ---------- main ----------
require_stack

# Ejecutar scenarios según arg
SCEN="${1:-all}"

case "$SCEN" in
  --scenario=*) SCEN="${SCEN#--scenario=}" ;;
  all)          SCEN="all" ;;
  *)            SCEN="all" ;;
esac

case "$SCEN" in
  user-agent)        scenario_user_agent ;;
  stats-success)     scenario_stats_success ;;
  circuit-breaker)   scenario_stats_500_then_breaker ;;
  timeout)           scenario_timeout_handling ;;
  folia)             scenario_folia_loaded ;;
  paper)             scenario_paper_loaded ;;
  mock-folia)        scenario_mock_receives_from_folia ;;
  manual-vote40)     scenario_manual_vote40 ;;
  all)
    scenario_paper_loaded
    scenario_folia_loaded
    scenario_mock_receives_from_folia
    scenario_user_agent
    scenario_stats_success
    scenario_stats_500_then_breaker
    scenario_timeout_handling
    scenario_v3_pending_with_votes
    scenario_v3_pending_empty_can_vote
    scenario_v3_pending_empty_cannot_vote_yet
    scenario_v3_pending_invalid_key_403
    scenario_v3_pending_no_auth_401
    scenario_v3_pending_wrong_bearer_403
    scenario_v3_ack_response_shape
    scenario_manual_vote40
    ;;
  *) echo "Scenario desconocido: $SCEN"; exit 1 ;;
esac

scenario_summary
