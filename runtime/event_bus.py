import json
import threading
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from game_adapter import STATE_PATH, execute_action


ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "game-manifest.json"
INBOX_PATH = ROOT / "data" / "events_inbox.jsonl"
SEEN_EVENT_IDS = set()
SEEN_EVENT_ORDER = deque()
SEEN_EVENT_LOCK = threading.Lock()
MAX_SEEN_EVENTS = 5000


def load_manifest():
    with MANIFEST_PATH.open("r", encoding="utf-8-sig") as file:
        manifest = json.load(file)
    if not manifest.get("gameId"):
        raise ValueError("manifest sin gameId")
    if "actions" not in manifest or not isinstance(manifest.get("actions"), list):
        raise ValueError("manifest sin actions[]")
    return manifest


def action_ids(manifest):
    return {str(action.get("id", "")).strip() for action in manifest["actions"]}


def reserve_event(event_id):
    if not event_id:
        return True
    with SEEN_EVENT_LOCK:
        if event_id in SEEN_EVENT_IDS:
            return False
        SEEN_EVENT_IDS.add(event_id)
        SEEN_EVENT_ORDER.append(event_id)
        while len(SEEN_EVENT_ORDER) > MAX_SEEN_EVENTS:
            SEEN_EVENT_IDS.discard(SEEN_EVENT_ORDER.popleft())
        return True


def release_event(event_id):
    if not event_id:
        return
    with SEEN_EVENT_LOCK:
        SEEN_EVENT_IDS.discard(event_id)


class Handler(BaseHTTPRequestHandler):
    def send_json(self, status, body):
        raw = b"" if status == 204 else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Private-Network", "true")
        self.end_headers()
        if raw:
            self.wfile.write(raw)

    def do_OPTIONS(self):
        self.send_json(204, {})

    def do_GET(self):
        try:
            manifest = load_manifest()
            if self.path in ("/manifest", "/game-manifest.json"):
                self.send_json(200, manifest)
                return
            if self.path == "/actions":
                self.send_json(200, {"gameId": manifest["gameId"], "actions": manifest["actions"]})
                return
            if self.path == "/health":
                self.send_json(200, {"ok": True, "gameId": manifest["gameId"]})
                return
            if self.path == "/state":
                if STATE_PATH.exists():
                    with STATE_PATH.open("r", encoding="utf-8") as file:
                        self.send_json(200, json.load(file))
                else:
                    self.send_json(200, {"roundState": "idle", "participants": {}})
                return
            self.send_json(404, {"ok": False, "error": "ruta no encontrada"})
        except Exception as error:
            self.send_json(500, {"ok": False, "error": str(error)})

    def do_POST(self):
        try:
            manifest = load_manifest()
            event_path = str(manifest.get("eventBus", {}).get("path", "/event"))
            if self.path != event_path:
                self.send_json(404, {"ok": False, "error": "ruta no encontrada"})
                return

            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length) or b"{}")
            action_id = str(payload.get("action", "")).strip()
            event_id = str(payload.get("eventId", "")).strip()

            if action_id not in action_ids(manifest):
                self.send_json(404, {"ok": False, "error": f"accion no declarada: {action_id}"})
                return
            if not reserve_event(event_id):
                self.send_json(200, {"ok": True, "duplicate": True, "eventId": event_id, "executed": False})
                return

            try:
                INBOX_PATH.parent.mkdir(parents=True, exist_ok=True)
                with INBOX_PATH.open("a", encoding="utf-8") as file:
                    file.write(json.dumps(payload, ensure_ascii=False) + "\n")
                result = execute_action(action_id, payload, manifest)
            except Exception:
                release_event(event_id)
                raise

            if isinstance(result, dict) and result.get("accepted") is False:
                self.send_json(409, {"ok": False, "executed": False, "result": result})
                return
            self.send_json(200, {"ok": True, "queued": True, "executed": action_id, "result": result})
        except Exception as error:
            self.send_json(500, {"ok": False, "error": str(error)})

    def log_message(self, format, *args):
        print(f"[minecraft-event-bus] {format % args}")


def main():
    manifest = load_manifest()
    event_bus = manifest.get("eventBus", {})
    host = str(event_bus.get("host", "127.0.0.1"))
    port = int(event_bus.get("port", 9010))
    print(f"Event bus de {manifest['gameId']} escuchando en http://{host}:{port}")
    print(f"Manifest: http://{host}:{port}/manifest")
    ThreadingHTTPServer((host, port), Handler).serve_forever()


if __name__ == "__main__":
    main()
