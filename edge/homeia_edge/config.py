"""Configuración del Edge desde variables de entorno (o /etc/homeia-edge.env vía systemd)."""
from __future__ import annotations

import os
import uuid
from dataclasses import dataclass


@dataclass(frozen=True)
class BrokerConfig:
    host: str
    port: int
    username: str
    password: str
    client_id: str
    tls: bool
    ca_cert: str | None
    client_cert: str | None
    client_key: str | None


@dataclass(frozen=True)
class EdgeConfig:
    edge_id: str
    secret: str
    topic_root: str
    local: BrokerConfig
    # Broker hacia el backend. None = el mismo broker local (backend en la LAN o conectado por VPN).
    uplink: BrokerConfig | None
    zigbee_base_topic: str
    zigbee_enabled: bool
    data_dir: str
    heartbeat_seconds: int
    replay_window_seconds: int

    @property
    def base(self) -> str:
        return f"{self.topic_root}/{self.edge_id}"


def _broker(prefix: str, default_host: str | None, client_id: str) -> BrokerConfig | None:
    host = os.getenv(f"{prefix}_HOST", default_host or "")
    if not host:
        return None
    tls = os.getenv(f"{prefix}_TLS", "false").lower() in {"1", "true", "yes"}
    return BrokerConfig(
        host=host,
        port=int(os.getenv(f"{prefix}_PORT", "8883" if tls else "1883")),
        username=os.getenv(f"{prefix}_USERNAME", ""),
        password=os.getenv(f"{prefix}_PASSWORD", ""),
        client_id=client_id,
        tls=tls,
        ca_cert=os.getenv(f"{prefix}_CA_CERT") or None,
        client_cert=os.getenv(f"{prefix}_CLIENT_CERT") or None,
        client_key=os.getenv(f"{prefix}_CLIENT_KEY") or None,
    )


def load() -> EdgeConfig:
    edge_id = os.getenv("HOMEIA_EDGE_ID", "").strip()
    secret = os.getenv("HOMEIA_EDGE_SECRET", "").strip()
    if not edge_id or not secret:
        raise SystemExit("Define HOMEIA_EDGE_ID y HOMEIA_EDGE_SECRET (se obtienen al enrolar el Edge en HomeIA).")
    uuid.UUID(edge_id)  # falla pronto si se copió mal
    if len(secret) < 32:
        raise SystemExit("HOMEIA_EDGE_SECRET parece incompleto")
    local = _broker("MQTT", "127.0.0.1", f"homeia-edge-{edge_id[:8]}-local")
    assert local is not None
    uplink = _broker("UPLINK_MQTT", None, edge_id)
    return EdgeConfig(
        edge_id=edge_id,
        secret=secret,
        topic_root=os.getenv("HOMEIA_EDGE_TOPIC_ROOT", "homeia/edge").rstrip("/"),
        local=local,
        uplink=uplink,
        zigbee_base_topic=os.getenv("ZIGBEE_BASE_TOPIC", "zigbee2mqtt"),
        zigbee_enabled=os.getenv("ZIGBEE_ENABLED", "true").lower() in {"1", "true", "yes"},
        data_dir=os.getenv("HOMEIA_EDGE_DATA_DIR", "/var/lib/homeia-edge"),
        heartbeat_seconds=max(10, int(os.getenv("HOMEIA_EDGE_HEARTBEAT_SECONDS", "30"))),
        replay_window_seconds=max(30, int(os.getenv("HOMEIA_EDGE_REPLAY_WINDOW_SECONDS", "120"))),
    )
