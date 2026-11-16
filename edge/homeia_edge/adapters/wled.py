"""WLED por su API JSON local (/json/state)."""
from __future__ import annotations

from typing import Any

from .base import Adapter, AdapterError, Device, UnsupportedCommand, clamp_brightness
from .http import request_json


class WledAdapter(Adapter):
    provider = "WLED"

    def __init__(self, http=request_json):
        self.http = http

    def execute(self, device: Device, command: str, brightness: int | None) -> dict[str, Any]:
        if command == "ON":
            body = {"on": True}
        elif command == "OFF":
            body = {"on": False}
        elif command == "TOGGLE":
            body = {"on": "t"}
        elif command == "BRIGHTNESS":
            body = {"on": True, "bri": max(1, round(clamp_brightness(brightness) * 255 / 100))}
        elif command == "STATUS":
            body = None
        else:
            raise UnsupportedCommand(f"comando {command} no soportado")
        if body is not None:
            self.http(device.address, "/json/state", method="POST", body=body)
        state = self.http(device.address, "/json/state")
        if not isinstance(state, dict) or "on" not in state:
            raise AdapterError("respuesta WLED inesperada")
        result: dict[str, Any] = {"powerState": "ON" if state.get("on") else "OFF"}
        if isinstance(state.get("bri"), (int, float)):
            result["brightness"] = round(state["bri"] * 100 / 255)
        return result
