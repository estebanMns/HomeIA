"""Cliente HTTP mínimo para dispositivos LAN: solo IPv4 privadas, sin redirecciones, tiempo y tamaño acotados."""
from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any

from ..netpolicy import private_ipv4
from .base import AdapterError

TIMEOUT = 4
MAX_BYTES = 256 * 1024
USER_AGENT = "HomeIA-Edge/2"


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # noqa: D401 - nunca seguir redirecciones (SSRF)
        return None


def request_json(address: str, path: str, *, method: str = "GET", body: Any = None,
                 username: str | None = None, password: str | None = None, digest: bool = False,
                 port: int | None = None) -> Any:
    ip = private_ipv4(address)
    if not path.startswith("/"):
        raise AdapterError("ruta inválida")
    url = f"http://{ip}{':' + str(port) if port else ''}{path}"
    handlers: list[urllib.request.BaseHandler] = [_NoRedirect(), urllib.request.ProxyHandler({})]
    if username is not None and password:
        manager = urllib.request.HTTPPasswordMgrWithDefaultRealm()
        manager.add_password(None, url, username, password)
        handlers.append(urllib.request.HTTPDigestAuthHandler(manager) if digest else urllib.request.HTTPBasicAuthHandler(manager))
    opener = urllib.request.build_opener(*handlers)
    data = None
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with opener.open(req, timeout=TIMEOUT) as response:
            raw = response.read(MAX_BYTES + 1)
    except urllib.error.HTTPError as exc:
        if exc.code in (401, 403):
            raise AdapterError("el dispositivo rechazó las credenciales (HTTP %d)" % exc.code) from exc
        raise AdapterError(f"HTTP {exc.code}") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise AdapterError(f"dispositivo inalcanzable: {getattr(exc, 'reason', exc)}") from exc
    if len(raw) > MAX_BYTES:
        raise AdapterError("respuesta demasiado grande")
    try:
        return json.loads(raw or b"null")
    except ValueError as exc:
        raise AdapterError("respuesta no es JSON") from exc
