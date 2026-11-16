"""Sobres firmados backend <-> Edge. Debe coincidir con EdgeSigner.java.

Texto firmado: "v1\\n{edgeId}\\n{kind}\\n{ts}\\n{nonce}\\n{body}" con HMAC-SHA256 y el secreto del Edge.
Los datos sensibles (credenciales) llegan cifrados con AES-256-GCM, clave = HMAC(secreto, "homeia-edge-enc-v1").
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import threading
import time
from typing import Any

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

VERSION = "v1"


def canonical(edge_id: str, kind: str, ts: int, nonce: str, body: str) -> str:
    return f"{VERSION}\n{edge_id}\n{kind}\n{ts}\n{nonce}\n{body}"


def sign(secret: str, edge_id: str, kind: str, ts: int, nonce: str, body: str) -> str:
    return hmac.new(secret.encode(), canonical(edge_id, kind, ts, nonce, body).encode(), hashlib.sha256).hexdigest()


def verify(secret: str, edge_id: str, kind: str, ts: int, nonce: str, body: str, signature: str) -> bool:
    if not isinstance(signature, str):
        return False
    return hmac.compare_digest(sign(secret, edge_id, kind, ts, nonce, body), signature.lower())


def dumps(body: Any) -> str:
    return json.dumps(body, separators=(",", ":"), ensure_ascii=False)


def envelope(secret: str, edge_id: str, kind: str, body: Any, ts: int | None = None, nonce: str | None = None) -> str:
    body_json = dumps(body)
    ts = int(time.time()) if ts is None else ts
    nonce = secrets.token_hex(12) if nonce is None else nonce
    return dumps({"v": 1, "edgeId": edge_id, "kind": kind, "ts": ts, "nonce": nonce, "body": body_json,
                  "sig": sign(secret, edge_id, kind, ts, nonce, body_json)})


def _encryption_key(secret: str) -> bytes:
    return hmac.new(secret.encode(), b"homeia-edge-enc-v1", hashlib.sha256).digest()


def encrypt(secret: str, edge_id: str, plaintext: str) -> str:
    iv = secrets.token_bytes(12)
    data = AESGCM(_encryption_key(secret)).encrypt(iv, plaintext.encode(), edge_id.encode())
    return base64.b64encode(iv + data).decode()


def decrypt(secret: str, edge_id: str, ciphertext: str) -> str:
    raw = base64.b64decode(ciphertext)
    return AESGCM(_encryption_key(secret)).decrypt(raw[:12], raw[12:], edge_id.encode()).decode()


class EnvelopeError(ValueError):
    pass


class Verifier:
    """Verifica sobres del backend: tópico coherente, firma, ventana de tiempo y nonce no repetido."""

    def __init__(self, secret: str, edge_id: str, window_seconds: int = 120, clock=time.time):
        self.secret = secret
        self.edge_id = edge_id
        self.window = window_seconds
        self.clock = clock
        self._seen: dict[str, float] = {}
        self._lock = threading.Lock()

    def open(self, kind: str, payload: bytes | str) -> dict[str, Any]:
        try:
            env = json.loads(payload)
        except (ValueError, TypeError) as exc:
            raise EnvelopeError("JSON inválido") from exc
        if not isinstance(env, dict):
            raise EnvelopeError("sobre inválido")
        body = env.get("body")
        ts = env.get("ts")
        nonce = env.get("nonce")
        if env.get("edgeId") != self.edge_id or env.get("kind") != kind:
            raise EnvelopeError("sobre dirigido a otro Edge o tipo incoherente")
        if not isinstance(body, str) or not isinstance(ts, int) or not isinstance(nonce, str) or not 0 < len(nonce) <= 64:
            raise EnvelopeError("campos del sobre inválidos")
        if not verify(self.secret, self.edge_id, kind, ts, nonce, body, env.get("sig", "")):
            raise EnvelopeError("firma inválida")
        now = self.clock()
        if abs(now - ts) > self.window:
            raise EnvelopeError("mensaje fuera de la ventana de tiempo (revisa NTP)")
        with self._lock:
            if len(self._seen) > 20000:
                self._seen = {k: v for k, v in self._seen.items() if v > now - self.window}
            if nonce in self._seen:
                raise EnvelopeError("mensaje repetido")
            self._seen[nonce] = ts
        try:
            parsed = json.loads(body)
        except ValueError as exc:
            raise EnvelopeError("cuerpo inválido") from exc
        if not isinstance(parsed, dict):
            raise EnvelopeError("cuerpo inválido")
        return parsed
