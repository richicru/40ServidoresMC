#!/usr/bin/env bash
# scripts/test-v3-vote-flow.sh
# Test end-to-end del flujo v3: GET pending → entregar → POST ack
# Como /voto40 requiere jugador real, este script documenta el flujo.
set -uo pipefail
cd "$(dirname "$0")/.."

MOCK_URL="http://127.0.0.1:28080"

echo "=== Test del mock-api: simular el flujo v3 completo ==="

echo ""
echo "--- 1. pending para 'success-alice' (debería traer 1 voto, puede_votar_ya=true) ---"
curl -s "http://${MOCK_URL#http://}/api/vote/v3/pending?nick=success-alice" | python3 -m json.tool | head -25

echo ""
echo "--- 2. pending para 'notvoted-bob' (0 votos, puede_votar_ya=true) ---"
curl -s "http://${MOCK_URL#http://}/api/vote/v3/pending?nick=notvoted-bob" | python3 -m json.tool | head -15

echo ""
echo "--- 3. pending para 'already-carol' (0 votos, puede_votar_ya=false, siguiente_voto) ---"
curl -s "http://${MOCK_URL#http://}/api/vote/v3/pending?nick=already-carol" | python3 -m json.tool | head -15

echo ""
echo "--- 4. pending para 'invalidkey-dave' (HTTP 403) ---"
curl -s -w "\nHTTP %{http_code}\n" "http://${MOCK_URL#http://}/api/vote/v3/pending?nick=invalidkey-dave"

echo ""
echo "--- 5. POST ack (simulando lo que envía el plugin tras entregar premio) ---"
curl -s -X POST "http://${MOCK_URL#http://}/api/vote/v3/ack" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer TESTKEY" \
    -d '{"votos":[123],"entregado":true,"nick":"alice","user_ip":"abc123hash"}' | python3 -m json.tool

echo ""
echo "--- 6. POST ack con entregado:false (simulando fallo de entrega) ---"
curl -s -X POST "http://${MOCK_URL#http://}/api/vote/v3/ack" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer TESTKEY" \
    -d '{"votos":[456],"entregado":false,"nick":"alice","user_ip":"abc123hash"}' | python3 -m json.tool

echo ""
echo "--- admin: ver el log de peticiones del mock ---"
curl -s "http://${MOCK_URL#http://}/admin/requests" 2>/dev/null | python3 -c "import sys, json; d=json.load(sys.stdin); print(f'total: {len(d)}'); [print(f\"  {r['method']} {r['path']} [{','.join(f'{k}={v[0]}' for k,v in r['query'].items())}]\") for r in d[-6:]]" 2>&1 || curl -s "http://${MOCK_URL#http://}/__admin/requests" | python3 -c "import sys, json; d=json.load(sys.stdin); print(f'total: {len(d)}'); [print(f\"  {r['method']} {r['path']} [{','.join(f'{k}={v[0]}' for k,v in r['query'].items())}]\") for r in d[-6:]]"
