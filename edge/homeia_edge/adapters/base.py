"""Contrato común de los adaptadores de fabricantes. Cada adaptador es independiente y se prueba por separado."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


class AdapterError(RuntimeError):
    """Fallo al hablar con el dispositivo (inalcanzable, credenciales, respuesta inesperada)."""


class UnsupportedCommand(AdapterError):
    pass


@dataclass
class Device:
    device_id: str
    provider: str
    address: str
    type: str = "LIGHT"
    capabilities: list[str] = field(default_factory=list)
    credentials: dict[str, str] = field(default_factory=dict)

    def has(self, capability: str) -> bool:
        return capability in self.capabilities


class Adapter:
    provider: str = ""

    def execute(self, device: Device, command: str, brightness: int | None) -> dict[str, Any]:
        """Ejecuta ON/OFF/TOGGLE/BRIGHTNESS/STATUS y devuelve el estado en formato HomeIA."""
        raise NotImplementedError

    def read_state(self, device: Device) -> dict[str, Any]:
        return self.execute(device, "STATUS", None)


def clamp_brightness(value: Any) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError) as exc:
        raise UnsupportedCommand("brillo inválido") from exc
    if not 0 <= number <= 100:
        raise UnsupportedCommand("el brillo debe estar entre 0 y 100")
    return number
