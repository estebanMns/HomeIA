"""Shelly Gen1 (API /relay, /light, /status) y Gen2/Plus/Pro (RPC). Relés y dimmers; autenticación opcional."""
from __future__ import annotations

from typing import Any

from .base import Adapter, AdapterError, Device, UnsupportedCommand, clamp_brightness
from .http import request_json


class ShellyAdapter(Adapter):
    provider = "SHELLY"

    def __init__(self, http=request_json):
        self.http = http
        self._generation: dict[str, int] = {}

    def generation(self, device: Device) -> int:
        cached = self._generation.get(device.address)
        if cached:
            return cached
        info = self.http(device.address, "/shelly")  # público incluso con autenticación activa
        gen = int(info.get("gen", 1)) if isinstance(info, dict) else 1
        self._generation[device.address] = gen
        return gen

    def _call(self, device: Device, path: str, gen: int) -> Any:
        user = device.credentials.get("username") or "admin"
        password = device.credentials.get("password")
        return self.http(device.address, path, username=user if password else None, password=password, digest=gen >= 2)

    def execute(self, device: Device, command: str, brightness: int | None) -> dict[str, Any]:
        gen = self.generation(device)
        dimmer = device.has("BRIGHTNESS")
        if command == "BRIGHTNESS" and not dimmer:
            raise UnsupportedCommand("este Shelly no anuncia regulación de brillo")
        return self._gen2(device, command, brightness, dimmer) if gen >= 2 else self._gen1(device, command, brightness, dimmer)

    # ---- Gen2 / Plus / Pro ----
    def _gen2(self, device: Device, command: str, brightness: int | None, dimmer: bool) -> dict[str, Any]:
        component = "Light" if dimmer else "Switch"
        if command == "ON":
            self._call(device, f"/rpc/{component}.Set?id=0&on=true", 2)
        elif command == "OFF":
            self._call(device, f"/rpc/{component}.Set?id=0&on=false", 2)
        elif command == "TOGGLE":
            self._call(device, f"/rpc/{component}.Toggle?id=0", 2)
        elif command == "BRIGHTNESS":
            self._call(device, f"/rpc/Light.Set?id=0&on=true&brightness={clamp_brightness(brightness)}", 2)
        elif command != "STATUS":
            raise UnsupportedCommand(f"comando {command} no soportado")
        status = self._call(device, f"/rpc/{component}.GetStatus?id=0", 2)
        if not isinstance(status, dict) or "output" not in status:
            raise AdapterError("respuesta Shelly inesperada")
        state: dict[str, Any] = {"powerState": "ON" if status.get("output") else "OFF"}
        if isinstance(status.get("apower"), (int, float)):
            state["consumptionWatts"] = float(status["apower"])
        if dimmer and isinstance(status.get("brightness"), (int, float)):
            state["brightness"] = int(status["brightness"])
        telemetry = {}
        if isinstance(status.get("voltage"), (int, float)):
            telemetry["voltage"] = float(status["voltage"])
        if isinstance(status.get("current"), (int, float)):
            telemetry["currentA"] = float(status["current"])
        if isinstance(status.get("aenergy"), dict) and isinstance(status["aenergy"].get("total"), (int, float)):
            telemetry["energyKwh"] = round(float(status["aenergy"]["total"]) / 1000, 4)
        if isinstance(status.get("temperature"), dict) and isinstance(status["temperature"].get("tC"), (int, float)):
            telemetry["deviceTemperatureC"] = float(status["temperature"]["tC"])
        if telemetry:
            state["telemetry"] = telemetry
        return state

    # ---- Gen1 ----
    def _gen1(self, device: Device, command: str, brightness: int | None, dimmer: bool) -> dict[str, Any]:
        resource = "light" if dimmer else "relay"
        if command in ("ON", "OFF", "TOGGLE"):
            self._call(device, f"/{resource}/0?turn={command.lower()}", 1)
        elif command == "BRIGHTNESS":
            self._call(device, f"/light/0?turn=on&brightness={clamp_brightness(brightness)}", 1)
        elif command != "STATUS":
            raise UnsupportedCommand(f"comando {command} no soportado")
        status = self._call(device, "/status", 1)
        if not isinstance(status, dict):
            raise AdapterError("respuesta Shelly inesperada")
        channels = status.get("lights" if dimmer else "relays") or []
        if not channels or not isinstance(channels[0], dict):
            raise AdapterError("el Shelly no informa canales")
        state: dict[str, Any] = {"powerState": "ON" if channels[0].get("ison") else "OFF"}
        if dimmer and isinstance(channels[0].get("brightness"), (int, float)):
            state["brightness"] = int(channels[0]["brightness"])
        meters = status.get("meters") or status.get("emeters") or []
        if meters and isinstance(meters[0], dict) and isinstance(meters[0].get("power"), (int, float)):
            state["consumptionWatts"] = float(meters[0]["power"])
        return state
