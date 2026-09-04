#!/usr/bin/env bash
# scripts/test-validate.sh
# Espera a que Paper y/o Folia terminen de arrancar, mira los logs y verifica:
#   - Que el plugin 40ServidoresMC haya cargado completo
#   - Que no haya errores / stacktraces graves en los logs
#   - Que el servidor esté escuchando en el puerto
#
# Acepta --paper, --folia, o sin args (ambos).
#
# Sale con 0 si todo OK, 1 si algo falla.
set -uo pipefail
cd "$(dirname "$0")/.."

SCOPE="both"
[ "${1:-}" = "--paper" ] && SCOPE="paper"
[ "${1:-}" = "--folia" ] && SCOPE="folia"

fail=0

run_check() {
  local name="$1" container="$2"

  echo ""
  echo "====================================================="
  echo "  Validando $name ($container)"
  echo "====================================================="

  if ! docker ps --format '{{.Names}}' | grep -qx "$container"; then
    echo "  ✗ El contenedor $container no está corriendo."
    echo "    ¿Levantaste el stack?  ./scripts/test-servers-up.sh"
    fail=1
    return
  fi

  echo "  · Logs del contenedor (últimas 200 líneas):"
  local logs
  logs=$(docker logs --tail=200 "$container" 2>&1 || true)
  local log_file="/tmp/${container}.test.log"
  echo "$logs" > "$log_file"
  echo "    (log completo guardado en $log_file)"

  # 1. ¿Servidor arrancó? (acepta "Done (5.367s)" y "Done (5.367 s)")
  if ! echo "$logs" | grep -qE 'Done \([0-9]+\.[0-9]+ *s?\)'; then
    echo "  ! No he encontrado 'Done (X.X s)' en los logs. ¿Sigue arrancando?"
    echo "    Revisa $log_file o espera: docker logs -f $container"
    fail=1
    return
  fi
  echo "  ✓ Servidor arrancó (mensaje 'Done (...)' presente)"

  # 2. ¿El plugin cargó?
  if ! echo "$logs" | grep -qE '40ServidoresMC v[0-9.]+ cargado completamente'; then
    echo "  ✗ El plugin 40ServidoresMC no aparece cargado (línea '... vX.X.X cargado completamente')."
    echo "    Posibles causas: jar no en plugins/, error de inicio, dependencia Gson no resuelta."
    echo "    Busca líneas con [ERROR]/Caused by/Exception en los logs."
    echo "$logs" | grep -E '\[ERROR\]|Exception|Caused by' | head -20 | sed 's/^/    /'
    fail=1
  else
    local version_line
    version_line=$(echo "$logs" | grep -oE '40ServidoresMC v[0-9.]+ cargado completamente' | head -1)
    echo "  ✓ Plugin cargó: $version_line"
  fi

  # 3. ¿Errores críticos? Excluimos los esperados en arranque (eula, online-mode, etc.)
  local errors
  errors=$(echo "$logs" | grep -EE 'java\.lang\.[A-Z][A-Za-z]+Exception|Caused by:|\[ERROR\]' \
    | grep -vE 'com\.mojang\.authlib|^\[INFO\] \[Server\]|Could not serialize|Could not parse|Deprecated|Experimental' \
    | head -30 || true)
  if [ -n "$errors" ]; then
    echo "  ! Errores/excepciones en logs (revisar):"
    echo "$errors" | sed 's/^/    /'
    # no fallamos automáticamente, los marcamos para revisión humana
  else
    echo "  ✓ Sin errores/excepciones en logs"
  fi

  # 4. Avisos típicos de Folia (NoSuchMethod en runtime significan API no encontrada)
  local nosuchmethod
  nosuchmethod=$(echo "$logs" | grep -E 'NoSuchMethodError' || true)
  if [ -n "$nosuchmethod" ]; then
    echo "  ! Detectado NoSuchMethodError - posiblemente incompatibilidad API Paper/Folia:"
    echo "$nosuchmethod" | head -10 | sed 's,^,    ,'
    fail=1
  fi
}

if [ "$SCOPE" = "paper" ] || [ "$SCOPE" = "both" ]; then
  run_check "Paper"  "cs-test-paper"
fi
if [ "$SCOPE" = "folia" ] || [ "$SCOPE" = "both" ]; then
  run_check "Folia"  "cs-test-folia"
fi

echo ""
if [ "$fail" -eq 0 ]; then
  echo "✅ Validación OK"
  exit 0
else
  echo "❌ Validación con errores. Revisa los logs en /tmp/cs-test-*.test.log"
  exit 1
fi
