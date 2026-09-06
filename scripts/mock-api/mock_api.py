#!/usr/bin/env python3
"""
Mock server que simula la API de 40servidoresmc.es para tests de integración.

Estrategia de routing para /api2.php (legacy v2):
    La respuesta a una validación de voto depende del prefijo del campo `nombre`:

    nombre = "success-<algo>"    → status="1"  (voto OK, recompensa al jugador)
    nombre = "notvoted-<algo>"   → status="0"  (no ha votado todavía)
    nombre = "already-<algo>"    → status="2"  (ya canjeado)
    nombre = "invalidkey-<algo>" → status="3"  (clave incorrecta)
    nombre = "delay-<N>-<algo>"  → duerme N segundos, después SUCCESS
    nombre = "500-<algo>"        → HTTP 500
    nombre = "broken-<algo>"     → JSON malformado
    nombre = "empty-<algo>"      → cuerpo vacío

Cualquier otro nombre → SUCCESS (status="1") con un "web" aleatorio.

Para /api/vote/v3/pending (protocolo v3):
    nick = "success-<algo>"      → 1 voto pendiente, puede_votar_ya=true
    nick = "notvoted-<algo>"     → 0 votos, puede_votar_ya=true
    nick = "already-<algo>"      → 0 votos, puede_votar_ya=false, siguiente_voto=2026-09-07T...
    nick = "invalidkey-<algo>"   → HTTP 403
    nick = "500-<algo>"          → HTTP 500
    nick = "broken-<algo>"       → JSON malformado

Para /api/vote/v3/ack (POST):
    Sólo loguea el body recibido y devuelve {"api_version":3,"confirmados":[...],"entregado":true}.

También expone:
    POST /__admin/reset         → limpia el log de peticiones y contadores
    GET  /__admin/log           → JSON con todas las peticiones recibidas
    GET  /__admin/requests      → lista plana de las peticiones (sin meta)
    GET  /__admin/health        → 200 OK con JSON {"status":"ok"}
"""

from __future__ import annotations

import json
import os
import re
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HOST = os.environ.get("MOCK_HOST", "0.0.0.0")
PORT = int(os.environ.get("MOCK_PORT", "8080"))
LOG_DIR = os.environ.get("MOCK_LOG_DIR", "/logs")
LOG_FILE = os.path.join(LOG_DIR, "mock-api.log")
LOG_LOCK = threading.Lock()

# Si /logs no existe y no somos root para crearlo, caemos a un dir local
try:
    os.makedirs(LOG_DIR, exist_ok=True)
except (PermissionError, OSError):
    fallback = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_logs")
    os.makedirs(fallback, exist_ok=True)
    print(f"[mock-api] WARN: no se pudo usar {LOG_DIR}, cayendo a {fallback}", flush=True)
    LOG_DIR = fallback
    LOG_FILE = os.path.join(LOG_DIR, "mock-api.log")

# Accesible desde el proceso principal y los hilos del ThreadingHTTPServer
STATE = {
    "requests": [],          # lista de {ts, method, path, query, body, ua, ip}
    "by_path": {},            # path -> count
    "by_status": {},          # 2xx/4xx/5xx -> count
    "status_count": {},       # VoteStatus.value -> count (para forzar distribución)
    "force_fail": False,      # si True, TODAS las llamadas a /api2.php devuelven 500
    "force_fail_until": 0,   # timestamp epoch; si > ahora, también forcea fail
    "rate_limit_until": 0,    # timestamp epoch; durante este intervalo /api2.php devuelve 429
    "rate_limit_retry_after": 30,  # segundos para Retry-After cuando rate limited
    "circuit_open_until": 0,  # timestamp epoch; durante este intervalo /api2.php devuelve 503
}


def write_to_disk(entry: dict) -> None:
    os.makedirs(LOG_DIR, exist_ok=True)
    with LOG_LOCK:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def record(method: str, path: str, query: dict, ua: str, ip: str, body: bytes | None) -> None:
    entry = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "method": method,
        "path": path,
        "query": query,
        "body": body.decode("utf-8", errors="replace") if body else None,
        "ua": ua,
        "ip": ip,
    }
    STATE["requests"].append(entry)
    STATE["by_path"][path] = STATE["by_path"].get(path, 0) + 1
    write_to_disk(entry)


def vote_response_for(nombre: str) -> tuple[int, dict, str]:
    """
    Devuelve (status_code, response_body, content_type) para un `nombre` dado.
    """
    # Delay: "delay-N-..." → duerme N segundos, luego SUCCESS
    m = re.match(r"^delay-(\d+)-(.*)$", nombre)
    if m:
        secs = min(int(m.group(1)), 60)
        time.sleep(secs)
        return 200, _success_payload("delay"), "application/json"

    # Forzar respuestas que NO funcionan
    if nombre.startswith("500-"):
        return 500, {"error": "Mock internal error"}, "application/json"
    if nombre.startswith("broken-"):
        # JSON inválido — rompe el parser Gson
        return 200, {"this_is": "not closable json"}, "application/json"
    if nombre.startswith("empty-"):
        return 200, "", "application/json"

    # Estados de voto normales
    if nombre.startswith("notvoted-"):
        return 200, _vote_payload("0"), "application/json"
    if nombre.startswith("already-"):
        return 200, _vote_payload("2"), "application/json"
    if nombre.startswith("invalidkey-"):
        return 200, _vote_payload("3"), "application/json"
    if nombre.startswith("success-"):
        return 200, _vote_payload("1"), "application/json"

    # default: SUCCESS con un sufijo
    return 200, _vote_payload("1"), "application/json"


def _vote_payload(status: str) -> dict:
    return {
        "web": "https://www.40servidoresmc.es/votar",
        "status": status,
    }


def _success_payload(reason: str) -> dict:
    return {
        "web": "https://www.40servidoresmc.es/",
        "status": "1",
        "_mock_reason": reason,
    }


def _server_stats_payload() -> dict:
    return {
        "nombre": "MockServer",
        "puesto": 7,
        "votoshoy": 42,
        "votoshoypremiados": 36,
        "votossemanales": 250,
        "votossemanalespremiados": 210,
        "ultimos20votos": [
            {"name": f"player_{i}", "recompensado": 1 if i % 2 == 0 else 0}
            for i in range(20)
        ],
    }


def _pending_votes_payload(nick: str) -> tuple[int, dict, str]:
    """
    Devuelve (status, body, content_type) para /api/vote/v3/pending?nick=<nick>.
    Misma lógica de prefijos que vote_response_for pero con el esquema v3.
    """
    if nick.startswith("invalidkey-"):
        return 403, {"error": "invalid_key", "message": "Clave incorrecta"}, "application/json"
    if nick.startswith("500-"):
        return 500, {"error": "mock_500"}, "application/json"
    if nick.startswith("broken-"):
        return 200, {"this_is": "broken json"}, "application/json"

    base = {
        "api_version": 3,
        "jugador": nick,
        "servidor": {"id": 109, "nombre": "MockServer", "slug": "mockserver", "puesto": 30},
        "reserva_segundos": 300,
    }

    if nick.startswith("notvoted-"):
        return 200, {**base, "votos_pendientes": [], "puede_votar_ya": True, "siguiente_voto": None}, "application/json"
    if nick.startswith("already-"):
        return 200, {
            **base,
            "votos_pendientes": [],
            "puede_votar_ya": False,
            "siguiente_voto": "2026-09-07T03:31:07+02:00",
        }, "application/json"
    # default (incluye "success-") → 1 voto pendiente
    return 200, {
        **base,
        "votos_pendientes": [
            {"id": 123, "fecha": "2026-09-06T17:10:41+02:00", "dia": "2026-09-06", "origen": "web"}
        ],
        "puede_votar_ya": True,
        "siguiente_voto": None,
    }, "application/json"


class MockHandler(BaseHTTPRequestHandler):
    server_version = "Mock40ServidoresMC/1.0"

    # Silenciar logs por defecto en stderr (nosotros logueamos a archivo)
    def log_message(self, format, *args):  # noqa: A002
        pass

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length > 0 else b""

    def _send_json(self, status: int, payload: dict | list) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)
        STATE["by_status"][status] = STATE["by_status"].get(status, 0) + 1

    def _send_body(self, status: int, body: bytes, ctype: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)
        STATE["by_status"][status] = STATE["by_status"].get(status, 0) + 1

    def do_GET(self):  # noqa: N802
        self._handle()

    def do_POST(self):  # noqa: N802
        self._handle()

    def _handle(self) -> None:
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        body = self._read_body()
        ua = self.headers.get("User-Agent", "")
        ip = self.client_address[0] if self.client_address else "?"

        # Endpoints administrativos
        if parsed.path == "/__admin/health":
            self._send_json(200, {"status": "ok", "ts": time.time()})
            return
        if parsed.path == "/__admin/log":
            self._send_json(200, STATE)
            return
        if parsed.path == "/__admin/reset":
            STATE["requests"].clear()
            STATE["by_path"].clear()
            STATE["by_status"].clear()
            STATE["status_count"].clear()
            STATE["force_fail"] = False
            STATE["force_fail_until"] = 0
            try:
                open(LOG_FILE, "w").close()
            except Exception:
                pass
            self._send_json(200, {"ok": True, "message": "state reset"})
            return
        if parsed.path == "/__admin/requests":
            self._send_json(200, STATE["requests"])
            return
        if parsed.path == "/__admin/fail":
            # Toggle: ?on=1|0  ó  ?seconds=N (falla durante N segundos)
            qs = parse_qs(urlparse(self.path).query)
            if "on" in qs:
                STATE["force_fail"] = qs.get("on", ["1"])[0] in ("1", "true", "yes")
                STATE["force_fail_until"] = 0
                msg = f"force_fail={STATE['force_fail']}"
            elif "seconds" in qs:
                STATE["force_fail_until"] = time.time() + int(qs["seconds"][0])
                STATE["force_fail"] = False
                msg = f"force_fail_until={STATE['force_fail_until']}"
            else:
                STATE["force_fail"] = False
                STATE["force_fail_until"] = 0
                msg = "force_fail disabled"
            self._send_json(200, {"ok": True, "message": msg, "state": STATE})
            return
        if parsed.path == "/__admin/rate_limit":
            # ?seconds=N   → durante N segundos, /api2.php devuelve 429 con Retry-After
            # ?retry_after=N → segundos a incluir en el header Retry-After (default 30)
            # ?clear=1       → desactiva
            qs = parse_qs(urlparse(self.path).query)
            if qs.get("clear", ["0"])[0] in ("1", "true", "yes"):
                STATE["rate_limit_until"] = 0
                msg = "rate_limit disabled"
            elif "seconds" in qs:
                STATE["rate_limit_until"] = time.time() + int(qs["seconds"][0])
                if "retry_after" in qs:
                    STATE["rate_limit_retry_after"] = int(qs["retry_after"][0])
                msg = f"rate_limit_until={STATE['rate_limit_until']}, retry_after={STATE['rate_limit_retry_after']}s"
            else:
                STATE["rate_limit_until"] = 0
                msg = "rate_limit disabled"
            self._send_json(200, {"ok": True, "message": msg, "state": STATE})
            return
        if parsed.path == "/__admin/circuit_open":
            # igual que rate_limit pero devuelve 503 (simula el caso donde el server
            # remoto está caído). Útil para validar que el circuit breaker del plugin
            # abre tras 3 fallos consecutivos.
            qs = parse_qs(urlparse(self.path).query)
            if qs.get("clear", ["0"])[0] in ("1", "true", "yes"):
                STATE["circuit_open_until"] = 0
                msg = "circuit_open disabled"
            elif "seconds" in qs:
                STATE["circuit_open_until"] = time.time() + int(qs["seconds"][0])
                msg = f"circuit_open_until={STATE['circuit_open_until']}"
            else:
                STATE["circuit_open_until"] = 0
                msg = "circuit_open disabled"
            self._send_json(200, {"ok": True, "message": msg, "state": STATE})
            return

        # Endpoints del API mock — el plugin ataca estos
        if parsed.path == "/api2.php":
            record("GET", parsed.path, query, ua, ip, body)
            clave = (query.get("clave") or [""])[0]
            nombre = (query.get("nombre") or [""])[0]
            force500 = "force500" in query

            # Toggles globales de fallos (establecidos por /__admin/fail y
            # /__admin/circuit_open). Si están activos, respondemos 500.
            if STATE["force_fail"] or (STATE["force_fail_until"] and STATE["force_fail_until"] > time.time()):
                self._send_body(500, b'{"error":"mock force_fail active"}', "application/json")
                return
            if STATE["circuit_open_until"] and STATE["circuit_open_until"] > time.time():
                self._send_body(503, b'{"error":"mock circuit_open active"}', "application/json")
                return

            # Rate-limit toggle: durante la ventana configurada, respondemos 429
            # con la cabecera Retry-After que el plugin debe leer.
            if STATE["rate_limit_until"] and STATE["rate_limit_until"] > time.time():
                self.send_response(429)
                self.send_header("Content-Type", "application/json")
                self.send_header("Retry-After", str(STATE["rate_limit_retry_after"]))
                self.send_header("Content-Length", "0")
                self.end_headers()
                return

            if "estadisticas" in query:
                # GET /api2.php?clave=...&estadisticas=1[&force500=1]
                if force500:
                    # Útil para forzar el circuit breaker sin tocar el plugin.
                    self._send_body(500, b'{"error":"forced 500 for circuit breaker test"}', "application/json")
                    return
                # GET /api2.php?clave=...&estadisticas=1
                self._send_body(200, json.dumps(_server_stats_payload()).encode("utf-8"), "application/json")
                return
            if not nombre:
                self._send_body(400, b'{"error":"missing nombre"}', "application/json")
                return
            code, payload, ctype = vote_response_for(nombre)
            self._send_body(code, json.dumps(payload).encode("utf-8"), ctype)
            return

        # === v3 protocol ===
        if parsed.path == "/api/vote/v3/pending":
            record("GET", parsed.path, query, ua, ip, body)
            nick = (query.get("nick") or [""])[0]
            if not nick:
                self._send_body(400, b'{"error":"missing nick"}', "application/json")
                return
            # v3 también respeta los toggles globales de fallo / rate-limit.
            if STATE["force_fail"] or (STATE["force_fail_until"] and STATE["force_fail_until"] > time.time()):
                self._send_body(500, b'{"error":"mock force_fail active"}', "application/json")
                return
            if STATE["circuit_open_until"] and STATE["circuit_open_until"] > time.time():
                self._send_body(503, b'{"error":"mock circuit_open active"}', "application/json")
                return
            if STATE["rate_limit_until"] and STATE["rate_limit_until"] > time.time():
                self.send_response(429)
                self.send_header("Content-Type", "application/json")
                self.send_header("Retry-After", str(STATE["rate_limit_retry_after"]))
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            code, payload, ctype = _pending_votes_payload(nick)
            self._send_body(code, json.dumps(payload).encode("utf-8"), ctype)
            return

        if parsed.path == "/api/vote/v3/ack":
            # POST. El plugin envía un body JSON; lo registramos tal cual.
            record("POST", parsed.path, query, ua, ip, body)
            # Ack siempre devuelve éxito; los ids confirmados vienen del body.
            try:
                payload_obj = json.loads(body.decode("utf-8")) if body else {}
            except Exception:
                payload_obj = {}
            ids = payload_obj.get("votos", [])
            entregado = bool(payload_obj.get("entregado", False))
            resp = {
                "api_version": 3,
                "confirmados": ids if entregado else [],
                "ya_confirmados": [],
                "liberados": [] if entregado else ids,
                "desconocidos": [],
                "entregado": True,
            }
            self._send_body(200, json.dumps(resp).encode("utf-8"), "application/json")
            return

        # Si no es un endpoint conocido, devolvemos 404
        record("GET", parsed.path, query, ua, ip, body)
        self._send_json(404, {"error": "no such endpoint", "path": parsed.path})


def main() -> int:
    os.makedirs(LOG_DIR, exist_ok=True)
    open(LOG_FILE, "a").close()  # touch
    server = ThreadingHTTPServer((HOST, PORT), MockHandler)
    print(f"[mock-api] listening on {HOST}:{PORT}; logs at {LOG_FILE}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[mock-api] shutting down", flush=True)
        server.shutdown()
        return 0


if __name__ == "__main__":
    sys.exit(main())
