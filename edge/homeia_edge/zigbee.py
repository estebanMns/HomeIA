"""Integración con Zigbee2MQTT en el broker local: inventario, estado del coordinador, permit_join y eventos."""
from __future__ import annotations

import json
import threading
import time
from typing import Any, Callable

from .netpolicy import zigbee_name

EXPOSE_MAP = {
    "state": "POWER", "brightness": "BRIGHTNESS", "color_xy": "COLOR", "color_hs": "COLOR", "color": "COLOR",
    "color_temp": "COLOR_TEMPERATURE", "temperature": "TEMPERATURE", "humidity": "HUMIDITY",
    "occupancy": "MOTION", "presence": "PRESENCE", "battery": "BATTERY", "power": "ENERGY", "energy": "ENERGY",
    "voltage": "VOLTAGE", "current": "CURRENT", "contact": "CONTACT", "illuminance": "ILLUMINANCE",
    "illuminance_lux": "ILLUMINANCE",
}


def capabilities(exposes: Any) -> list[str]:
    """Convierte definition.exposes de Zigbee2MQTT a capacidades HomeIA (recorre features anidadas)."""
    found: set[str] = set()

    def walk(items: Any) -> None:
        if not isinstance(items, list):
            return
        for item in items:
            if not isinstance(item, dict):
                continue
            prop = item.get("property")
            if isinstance(prop, str) and prop in EXPOSE_MAP:
                found.add(EXPOSE_MAP[prop])
            walk(item.get("features"))

    walk(exposes)
    return sorted(found)


def candidates_from_inventory(devices: Any) -> list[dict[str, Any]]:
    result = []
    if not isinstance(devices, list):
        return result
    for d in devices:
        if not isinstance(d, dict) or d.get("type") == "Coordinator" or not d.get("interview_completed", True):
            continue
        friendly = d.get("friendly_name")
        try:
            zigbee_name(friendly)
        except ValueError:
            continue  # nombres con '/' u otros caracteres no se pueden usar con seguridad en tópicos
        definition = d.get("definition") or {}
        result.append({"name": friendly, "provider": "ZIGBEE", "protocol": "ZIGBEE", "address": friendly,
                       "manufacturer": definition.get("vendor") or d.get("manufacturer"),
                       "model": definition.get("model") or d.get("model_id"), "service": "zigbee2mqtt",
                       "capabilities": capabilities(definition.get("exposes"))})
    return result


def command_payload(command: str, brightness: int | None, caps: list[str]) -> dict[str, Any]:
    if command in ("ON", "OFF", "TOGGLE"):
        if caps and "POWER" not in caps:
            raise ValueError("el dispositivo Zigbee no anuncia POWER")
        return {"state": command}
    if command == "BRIGHTNESS":
        if caps and "BRIGHTNESS" not in caps:
            raise ValueError("el dispositivo Zigbee no anuncia BRIGHTNESS")
        value = int(brightness if brightness is not None else -1)
        if not 0 <= value <= 100:
            raise ValueError("el brillo debe estar entre 0 y 100")
        return {"brightness": round(value * 254 / 100), "state": "ON" if value > 0 else "OFF"}
    if command == "STATUS":
        return {"state": ""}  # se publica en /get
    raise ValueError(f"comando {command} no soportado")


class ZigbeeBridge:
    """Sigue bridge/state, bridge/info y bridge/event; abre y cierra permit_join con cierre verificado."""

    def __init__(self, base_topic: str, publish: Callable[[str, str], None], on_event: Callable[[dict], None],
                 on_inventory: Callable[[list], None]):
        self.base = base_topic
        self.publish = publish
        self.on_event = on_event
        self.on_inventory = on_inventory
        self.bridge_state: str | None = None
        self.permit_join = False
        self._close_timer: threading.Timer | None = None

    def topics(self) -> list[str]:
        return [f"{self.base}/bridge/state", f"{self.base}/bridge/info", f"{self.base}/bridge/event",
                f"{self.base}/bridge/devices", f"{self.base}/+"]

    def handle(self, topic: str, payload: bytes) -> tuple[str, dict] | None:
        """Procesa un mensaje Zigbee2MQTT. Devuelve (friendly_name, estado) si es estado de un dispositivo."""
        if not topic.startswith(self.base + "/"):
            return None
        rest = topic[len(self.base) + 1:]
        text = payload.decode("utf-8", "ignore")
        if rest == "bridge/state":
            try:
                data = json.loads(text)
                self.bridge_state = data.get("state") if isinstance(data, dict) else str(data)
            except ValueError:
                self.bridge_state = text.strip() or None
            return None
        if rest == "bridge/info":
            try:
                info = json.loads(text)
                self.permit_join = bool(info.get("permit_join", False))
            except (ValueError, AttributeError):
                pass
            return None
        if rest == "bridge/devices":
            try:
                self.on_inventory(candidates_from_inventory(json.loads(text)))
            except ValueError:
                pass
            return None
        if rest == "bridge/event":
            try:
                event = json.loads(text)
            except ValueError:
                return None
            kind = event.get("type") if isinstance(event, dict) else None
            data = (event or {}).get("data") or {}
            name = data.get("friendly_name") or data.get("ieee_address") or "?"
            if kind == "device_joined":
                self.on_event({"type": "ZIGBEE_DEVICE_JOINED", "details": f"{name} ({data.get('ieee_address', '')})"})
            elif kind == "device_leave":
                self.on_event({"type": "ZIGBEE_DEVICE_LEFT", "details": str(name)})
            return None
        if "/" in rest or rest.startswith("bridge"):
            return None
        try:
            state = json.loads(text)
        except ValueError:
            return None
        return (rest, state) if isinstance(state, dict) else None

    def set_permit_join(self, seconds: int) -> None:
        seconds = max(0, min(int(seconds), 120))
        self.publish(f"{self.base}/bridge/request/permit_join", json.dumps({"time": seconds}))
        if self._close_timer:
            self._close_timer.cancel()
            self._close_timer = None
        if seconds > 0:
            # Cierre explícito al vencer, aunque Zigbee2MQTT también lo haga: así se verifica en el heartbeat.
            self._close_timer = threading.Timer(seconds + 2, self.set_permit_join, args=(0,))
            self._close_timer.daemon = True
            self._close_timer.start()

    def send(self, friendly_name: str, command: str, brightness: int | None, caps: list[str]) -> None:
        name = zigbee_name(friendly_name)
        payload = command_payload(command, brightness, caps)
        if command == "STATUS":
            self.publish(f"{self.base}/{name}/get", json.dumps({"state": ""}))
        else:
            self.publish(f"{self.base}/{name}/set", json.dumps(payload))

    def heartbeat(self) -> dict[str, Any] | None:
        if self.bridge_state is None:
            return None
        return {"bridgeState": self.bridge_state, "permitJoin": self.permit_join, "checkedAt": int(time.time())}
