"""Descubrimiento LAN por mDNS y SSDP. Encontrar un equipo no lo empareja: solo lo propone al backend."""
from __future__ import annotations

import socket
import threading
import time
from typing import Any, Callable

from .netpolicy import private_ipv4

MDNS_SERVICES = ("_http._tcp.local.", "_shelly._tcp.local.", "_esphomelib._tcp.local.", "_wled._tcp.local.")


def classify(name: str, service_type: str, properties: dict) -> str | None:
    text = f"{name} {service_type} {properties}".lower()
    if "_esphomelib" in service_type or "esphome" in text:
        return "ESPHOME"
    if "_wled" in service_type or "wled" in text:
        return "WLED"
    if "shelly" in text:
        return "SHELLY"
    if "tasmota" in text:
        return "TASMOTA"
    return None


def default_capabilities(provider: str, model: str | None) -> list[str]:
    model = (model or "").lower()
    if provider == "WLED":
        return ["BRIGHTNESS", "COLOR", "POWER"]
    if provider == "SHELLY":
        caps = {"POWER"}
        if any(k in model for k in ("dimmer", "rgbw", "bulb", "vintage", "duo")):
            caps.add("BRIGHTNESS")
        if any(k in model for k in ("pm", "plug", "em", "pro")):
            caps.add("ENERGY")
        return sorted(caps)
    if provider == "TASMOTA":
        return ["POWER"]
    return []  # ESPHome: se decide por el tipo al incorporarlo


class Discovery:
    def __init__(self, on_candidates: Callable[[list[dict[str, Any]]], None], http_probe=None):
        self.candidates: dict[str, dict[str, Any]] = {}
        self.lock = threading.Lock()
        self.on_candidates = on_candidates
        self.http_probe = http_probe
        self._zc = None

    def add(self, provider: str, address: str, name: str, service: str, model: str | None = None) -> None:
        try:
            address = private_ipv4(address)
        except ValueError:
            return
        if provider == "SHELLY" and self.http_probe and not model:
            try:
                info = self.http_probe(address, "/shelly")
                model = (info or {}).get("model") or (info or {}).get("type")
            except Exception:  # noqa: BLE001 - el sondeo es opcional
                pass
        candidate = {"name": name, "provider": provider, "protocol": "WIFI", "address": address,
                     "manufacturer": {"ESPHOME": "ESPHome", "WLED": "WLED"}.get(provider, provider.title()),
                     "model": model, "service": service, "capabilities": default_capabilities(provider, model)}
        with self.lock:
            self.candidates[f"{provider}|{address}"] = candidate

    def start_mdns(self) -> None:
        try:
            from zeroconf import IPVersion, ServiceBrowser, ServiceListener, Zeroconf
        except ImportError:
            print("zeroconf no está instalado: se omite mDNS")
            return
        outer = self

        class Listener(ServiceListener):
            def add_service(self, zc, service_type, name):
                self._resolve(zc, service_type, name)

            def update_service(self, zc, service_type, name):
                self._resolve(zc, service_type, name)

            def remove_service(self, zc, service_type, name):
                pass

            def _resolve(self, zc, service_type, name):
                info = zc.get_service_info(service_type, name, timeout=1500)
                if not info:
                    return
                addresses = info.parsed_addresses(IPVersion.V4)
                props = {k.decode(errors="ignore") if isinstance(k, bytes) else k:
                         v.decode(errors="ignore") if isinstance(v, bytes) else v
                         for k, v in (info.properties or {}).items()}
                provider = classify(name, service_type, props)
                if provider and addresses:
                    outer.add(provider, addresses[0], name.split(".")[0], service_type,
                              props.get("model") or props.get("app"))

        self._zc = Zeroconf(ip_version=IPVersion.V4)
        listener = Listener()
        for service in MDNS_SERVICES:
            try:
                ServiceBrowser(self._zc, service, listener)
            except Exception as exc:  # noqa: BLE001
                print(f"No se pudo iniciar mDNS {service}: {exc}")

    def ssdp_scan(self, wait: float = 2.5) -> None:
        message = ('M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n'
                   'MAN: "ssdp:discover"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n').encode()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.settimeout(0.35)
        try:
            sock.sendto(message, ("239.255.255.250", 1900))
            deadline = time.time() + wait
            while time.time() < deadline:
                try:
                    data, addr = sock.recvfrom(8192)
                except socket.timeout:
                    continue
                low = data.decode("utf-8", "ignore").lower()
                provider = "SHELLY" if "shelly" in low else ("TASMOTA" if "tasmota" in low else None)
                if provider:
                    self.add(provider, addr[0], f"{provider.title()} {addr[0]}", "ssdp")
        except OSError as exc:
            print(f"SSDP no disponible: {exc}")
        finally:
            sock.close()

    def scan(self) -> None:
        self.ssdp_scan()
        time.sleep(2.0)  # tiempo para que mDNS resuelva servicios
        with self.lock:
            snapshot = list(self.candidates.values())
        self.on_candidates(snapshot)
