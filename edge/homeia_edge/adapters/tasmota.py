"""Tasmota por HTTP local (/cm). Contraseña web opcional (user/password en la consulta, como exige Tasmota)."""
from __future__ import annotations

from typing import Any
from urllib.parse import urlencode

from .base import Adapter, AdapterError, Device, UnsupportedCommand, clamp_brightness
from .http import request_json


class TasmotaAdapter(Adapter):
    provider = "TASMOTA"

    def __init__(self, http=request_json):
        self.http = http

    def _cmnd(self, device: Device, command: str) -> Any:
        params = {"cmnd": command}
        if device.credentials.get("password"):
            params["user"] = device.credentials.get("username") or "admin"
            params["password"] = device.credentials["password"]
        result = self.http(device.address, "/cm?" + urlencode(params))
        if isinstance(result, dict) and result.get("WARNING"):
            raise AdapterError("Tasmota: " + str(result["WARNING"]))
        return result

    def execute(self, device: Device, command: str, brightness: int | None) -> dict[str, Any]:
        mapping = {"ON": "Power On", "OFF": "Power Off", "TOGGLE": "Power Toggle", "STATUS": "State"}
        if command == "BRIGHTNESS":
            if device.capabilities and not device.has("BRIGHTNESS"):
                raise UnsupportedCommand("este Tasmota no anuncia regulación de brillo")
            self._cmnd(device, f"Dimmer {clamp_brightness(brightness)}")
        elif command in mapping:
            self._cmnd(device, mapping[command])
        else:
            raise UnsupportedCommand(f"comando {command} no soportado")
        state_raw = self._cmnd(device, "State")
        if not isinstance(state_raw, dict):
            raise AdapterError("respuesta Tasmota inesperada")
        power = state_raw.get("POWER", state_raw.get("POWER1"))
        if power not in ("ON", "OFF"):
            raise AdapterError("Tasmota no informó POWER")
        state: dict[str, Any] = {"powerState": power}
        if isinstance(state_raw.get("Dimmer"), (int, float)):
            state["brightness"] = int(state_raw["Dimmer"])
        if device.type == "SMART_OUTLET" or device.has("ENERGY"):
            try:
                sensors = self._cmnd(device, "Status 8")
                energy = (sensors or {}).get("StatusSNS", {}).get("ENERGY", {})
                if isinstance(energy.get("Power"), (int, float)):
                    state["consumptionWatts"] = float(energy["Power"])
                telemetry = {}
                for key, name in (("Voltage", "voltage"), ("Current", "currentA"), ("Total", "energyKwh")):
                    if isinstance(energy.get(key), (int, float)):
                        telemetry[name] = float(energy[key])
                if telemetry:
                    state["telemetry"] = telemetry
            except AdapterError:
                pass  # equipo sin medidor: el estado de encendido sigue siendo válido
        return state
