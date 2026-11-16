"""Estado persistente del Edge (SQLite): cola de salida, órdenes ya procesadas e inventario.

- La cola guarda mensajes (ack, state, event, discovery) mientras no hay conexión con el backend; se firman al
  enviarse, así no caducan por la ventana anti-repetición.
- processed permite responder a un reintento con el mismo resultado sin volver a ejecutar la orden.
- El inventario sobrevive a reinicios: el Edge sigue sondeando aunque el backend aún no haya enviado sync.
"""
from __future__ import annotations

import json
import os
import sqlite3
import threading
import time
from typing import Any

MAX_OUTBOX = 5000


class Store:
    def __init__(self, path: str):
        if path != ":memory:":
            os.makedirs(os.path.dirname(path) or ".", mode=0o700, exist_ok=True)
        self._db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._lock = threading.Lock()
        with self._lock:
            self._db.execute("PRAGMA journal_mode=WAL")
            self._db.execute("CREATE TABLE IF NOT EXISTS outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL,"
                             " body TEXT NOT NULL, created REAL NOT NULL)")
            self._db.execute("CREATE TABLE IF NOT EXISTS processed (request_id TEXT PRIMARY KEY, result TEXT NOT NULL,"
                             " created REAL NOT NULL)")
            self._db.execute("CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

    # ---- cola de salida ----
    def enqueue(self, kind: str, body: dict[str, Any]) -> None:
        with self._lock:
            self._db.execute("INSERT INTO outbox (kind, body, created) VALUES (?, ?, ?)", (kind, json.dumps(body), time.time()))
            # Si la desconexión es larga se descartan los más antiguos: el estado actual vuelve a enviarse al sondear.
            self._db.execute("DELETE FROM outbox WHERE id NOT IN (SELECT id FROM outbox ORDER BY id DESC LIMIT ?)", (MAX_OUTBOX,))

    def pending(self, limit: int = 100) -> list[tuple[int, str, dict[str, Any]]]:
        with self._lock:
            rows = self._db.execute("SELECT id, kind, body FROM outbox ORDER BY id LIMIT ?", (limit,)).fetchall()
        return [(row[0], row[1], json.loads(row[2])) for row in rows]

    def remove(self, message_id: int) -> None:
        with self._lock:
            self._db.execute("DELETE FROM outbox WHERE id = ?", (message_id,))

    def depth(self) -> int:
        with self._lock:
            return self._db.execute("SELECT COUNT(*) FROM outbox").fetchone()[0]

    # ---- deduplicación de órdenes ----
    def remember(self, request_id: str, result: dict[str, Any]) -> None:
        with self._lock:
            self._db.execute("INSERT OR REPLACE INTO processed (request_id, result, created) VALUES (?, ?, ?)",
                             (request_id, json.dumps(result), time.time()))
            self._db.execute("DELETE FROM processed WHERE created < ?", (time.time() - 86400,))

    def recall(self, request_id: str) -> dict[str, Any] | None:
        with self._lock:
            row = self._db.execute("SELECT result FROM processed WHERE request_id = ?", (request_id,)).fetchone()
        return json.loads(row[0]) if row else None

    # ---- clave/valor (inventario) ----
    def put(self, key: str, value: Any) -> None:
        with self._lock:
            self._db.execute("INSERT OR REPLACE INTO kv (key, value) VALUES (?, ?)", (key, json.dumps(value)))

    def get(self, key: str, default: Any = None) -> Any:
        with self._lock:
            row = self._db.execute("SELECT value FROM kv WHERE key = ?", (key,)).fetchone()
        return json.loads(row[0]) if row else default
