"""Límites de red: el Edge solo habla con IPv4 privadas de la LAN (evita SSRF hacia Internet o la propia máquina)."""
from __future__ import annotations

import ipaddress
import re

_ZIGBEE_NAME = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


def private_ipv4(address: str) -> str:
    """Devuelve la IP normalizada o lanza ValueError."""
    if not isinstance(address, str) or not re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", address.strip()):
        raise ValueError("la dirección debe ser una IPv4 literal")
    ip = ipaddress.IPv4Address(address.strip())
    if not ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast or ip.is_unspecified:
        raise ValueError("solo se permiten IPv4 privadas de la LAN")
    if str(ip).endswith(".0") or str(ip).endswith(".255"):
        raise ValueError("dirección de red o broadcast")
    if not (ip in ipaddress.ip_network("10.0.0.0/8") or ip in ipaddress.ip_network("172.16.0.0/12")
            or ip in ipaddress.ip_network("192.168.0.0/16")):
        raise ValueError("solo se permiten 10.x, 172.16-31.x y 192.168.x")
    return str(ip)


def zigbee_name(name: str) -> str:
    if not isinstance(name, str) or not _ZIGBEE_NAME.match(name):
        raise ValueError("friendly_name Zigbee inválido")
    return name
