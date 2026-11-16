"""Registro de adaptadores por fabricante. Para añadir uno nuevo: implementar Adapter y registrarlo aquí."""
from __future__ import annotations

from .base import Adapter, AdapterError, Device, UnsupportedCommand
from .esphome import EsphomeAdapter
from .shelly import ShellyAdapter
from .tasmota import TasmotaAdapter
from .wled import WledAdapter


def default_adapters() -> dict[str, Adapter]:
    adapters: list[Adapter] = [ShellyAdapter(), TasmotaAdapter(), WledAdapter(), EsphomeAdapter()]
    return {a.provider: a for a in adapters}


__all__ = ["Adapter", "AdapterError", "Device", "UnsupportedCommand", "default_adapters"]
