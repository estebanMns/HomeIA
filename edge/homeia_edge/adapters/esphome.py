"""ESPHome por su API nativa (TCP 6053, cifrado Noise opcional) usando aioesphomeapi.

Controla la primera entidad de luz o interruptor del firmware (o la indicada con address "IP#object_id").
Lee además el primer sensor de potencia (device_class "power") para el consumo.
"""
from __future__ import annotations

import asyncio
import inspect
import threading
from typing import Any

from ..netpolicy import private_ipv4
from .base import Adapter, AdapterError, Device, UnsupportedCommand, clamp_brightness

try:  # dependencia opcional: sin ella el adaptador informa el motivo en cada orden
    import aioesphomeapi  # type: ignore
except ImportError:  # pragma: no cover - depende del entorno
    aioesphomeapi = None

PORT = 6053
TIMEOUT = 8


async def _maybe(value):
    return await value if inspect.isawaitable(value) else value


class EsphomeAdapter(Adapter):
    provider = "ESPHOME"

    def __init__(self, client_factory=None):
        self._factory = client_factory
        self._loop = asyncio.new_event_loop()
        threading.Thread(target=self._loop.run_forever, name="esphome-loop", daemon=True).start()

    def _client(self, address: str, device: Device):
        if self._factory:
            return self._factory(address, device)
        if aioesphomeapi is None:
            raise AdapterError("falta la librería aioesphomeapi en el Edge (pip install aioesphomeapi)")
        return aioesphomeapi.APIClient(address, PORT, device.credentials.get("password") or "",
                                       noise_psk=device.credentials.get("encryptionKey") or None,
                                       client_info="HomeIA Edge")

    def execute(self, device: Device, command: str, brightness: int | None) -> dict[str, Any]:
        if command not in {"ON", "OFF", "TOGGLE", "BRIGHTNESS", "STATUS"}:
            raise UnsupportedCommand(f"comando {command} no soportado")
        future = asyncio.run_coroutine_threadsafe(self._run(device, command, brightness), self._loop)
        try:
            return future.result(TIMEOUT + 2)
        except AdapterError:
            raise
        except Exception as exc:  # noqa: BLE001 - errores de red/protocolo de la librería
            raise AdapterError(f"ESPHome: {exc.__class__.__name__}: {exc}") from exc

    async def _run(self, device: Device, command: str, brightness: int | None) -> dict[str, Any]:
        host, _, object_id = device.address.partition("#")
        address = private_ipv4(host)
        client = self._client(address, device)
        await asyncio.wait_for(_maybe(client.connect(login=True)), TIMEOUT)
        try:
            entities, _services = await asyncio.wait_for(_maybe(client.list_entities_services()), TIMEOUT)
            target = self._pick(entities, object_id)
            power_sensor = next((e for e in entities if type(e).__name__ == "SensorInfo"
                                 and getattr(e, "device_class", "") == "power"), None)
            states: dict[int, Any] = {}
            got_target = asyncio.Event()

            def on_state(state):
                states[state.key] = state
                if state.key == target.key:
                    got_target.set()

            await _maybe(client.subscribe_states(on_state))
            # Primero llega el estado actual; con él se resuelve TOGGLE.
            try:
                await asyncio.wait_for(got_target.wait(), 3)
            except asyncio.TimeoutError:
                pass
            current = getattr(states.get(target.key), "state", False)
            is_light = type(target).__name__ == "LightInfo"
            if command in ("ON", "OFF", "TOGGLE"):
                desired = {"ON": True, "OFF": False, "TOGGLE": not current}[command]
                if is_light:
                    await _maybe(client.light_command(key=target.key, state=desired))
                else:
                    await _maybe(client.switch_command(target.key, desired))
            elif command == "BRIGHTNESS":
                if not is_light:
                    raise UnsupportedCommand("la entidad ESPHome no es una luz regulable")
                await _maybe(client.light_command(key=target.key, state=True,
                                                  brightness=clamp_brightness(brightness) / 100))
            if command != "STATUS":
                got_target.clear()
                try:
                    await asyncio.wait_for(got_target.wait(), 3)
                except asyncio.TimeoutError:
                    pass
            final = states.get(target.key)
            result: dict[str, Any] = {"powerState": "ON" if getattr(final, "state", False) else "OFF"}
            if is_light and isinstance(getattr(final, "brightness", None), (int, float)):
                result["brightness"] = round(final.brightness * 100)
            if power_sensor and isinstance(getattr(states.get(power_sensor.key), "state", None), (int, float)):
                result["consumptionWatts"] = float(states[power_sensor.key].state)
            return result
        finally:
            try:
                await _maybe(client.disconnect())
            except Exception:  # noqa: BLE001
                pass

    @staticmethod
    def _pick(entities, object_id: str):
        candidates = [e for e in entities if type(e).__name__ in ("LightInfo", "SwitchInfo")]
        if object_id:
            candidates = [e for e in candidates if getattr(e, "object_id", None) == object_id]
        if not candidates:
            raise AdapterError("el firmware ESPHome no expone una luz ni un interruptor" +
                               (f" con object_id {object_id}" if object_id else ""))
        lights = [e for e in candidates if type(e).__name__ == "LightInfo"]
        return (lights or candidates)[0]
