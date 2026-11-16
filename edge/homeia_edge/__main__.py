"""HomeIA Edge: descubrimiento LAN y puente de eventos a MQTT. No empareja ni controla sin autorización."""
import json
import os
import socket
import threading
import time
import uuid
from typing import Any

import paho.mqtt.client as mqtt
from zeroconf import IPVersion, ServiceBrowser, ServiceInfo, ServiceListener, Zeroconf

BROKER = os.getenv("MQTT_HOST", "mosquitto")
PORT = int(os.getenv("MQTT_PORT", "1883"))
USERNAME = os.getenv("MQTT_USERNAME", "")
PASSWORD = os.getenv("MQTT_PASSWORD", "")
CLIENT_ID = os.getenv("MQTT_CLIENT_ID", "homeia-edge")

class DiscoveryListener(ServiceListener):
    def __init__(self, candidates: dict[str, dict[str, Any]], lock: threading.Lock):
        self.candidates = candidates
        self.lock = lock
    def add_service(self, zc, service_type, name): self._resolve(zc, service_type, name)
    def update_service(self, zc, service_type, name): self._resolve(zc, service_type, name)
    def remove_service(self, zc, service_type, name): pass
    def _resolve(self, zc, service_type, name):
        info = zc.get_service_info(service_type, name, timeout=1500)
        if not info: return
        addresses = info.parsed_addresses(IPVersion.V4)
        if not addresses: return
        host = addresses[0]
        text = (name + " " + service_type + " " + str(info.properties)).lower()
        provider = None
        if "shelly" in text: provider = "SHELLY"
        elif "tasmota" in text: provider = "TASMOTA"
        elif "esphome" in text: provider = "ESPHOME"
        if not provider: return
        key = f"{provider}|{host}|{name}"
        candidate = {"name": name.split(".")[0], "provider": provider, "protocol": "WIFI",
            "address": host, "manufacturer": provider.title(), "model": service_type, "service": name,
            "capabilities": []}
        with self.lock: self.candidates[key] = candidate

class Edge:
    def __init__(self):
        self.candidates: dict[str, dict[str, Any]] = {}
        self.lock = threading.Lock()
        self.zc = Zeroconf(ip_version=IPVersion.V4)
        self.listener = DiscoveryListener(self.candidates, self.lock)
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=CLIENT_ID)
        if USERNAME: self.client.username_pw_set(USERNAME, PASSWORD)
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message

    def on_connect(self, client, userdata, flags, reason_code, properties):
        print(f"MQTT conectado: {reason_code}")
        client.subscribe("homeia/edge/scan", qos=1)
        client.subscribe("homeia/edge/command", qos=1)
        client.subscribe("zigbee2mqtt/bridge/devices", qos=1)

    def on_message(self, client, userdata, msg):
        if msg.topic == "homeia/edge/scan":
            threading.Thread(target=self.scan_and_publish, daemon=True).start()
        elif msg.topic == "zigbee2mqtt/bridge/devices":
            self.ingest_zigbee(msg.payload)
        elif msg.topic == "homeia/edge/command":
            self.execute_device_command(msg.payload)

    def ingest_zigbee(self, payload: bytes):
        try:
            devices = json.loads(payload.decode("utf-8"))
            for d in devices:
                if d.get("type") == "Coordinator": continue
                friendly = d.get("friendly_name")
                if not friendly: continue
                key = f"ZIGBEE|{friendly}"
                capabilities = self.zigbee_capabilities(d.get("definition", {}).get("exposes", []))
                candidate = {"name": friendly, "provider": "ZIGBEE", "protocol": "ZIGBEE",
                    "address": friendly, "manufacturer": d.get("vendor"), "model": d.get("model_id"),
                    "service": "zigbee2mqtt", "capabilities": capabilities}
                with self.lock: self.candidates[key] = candidate
            self.publish_candidates()
        except Exception as exc: print(f"No se pudo interpretar inventario Zigbee: {exc}")

    @staticmethod
    def zigbee_capabilities(exposes: list[Any]) -> list[str]:
        """Convierte las propiedades anunciadas por Zigbee2MQTT a capacidades HomeIA."""
        mapping = {
            "state": "POWER", "brightness": "BRIGHTNESS", "color": "COLOR",
            "color_temp": "COLOR_TEMPERATURE", "temperature": "TEMPERATURE",
            "humidity": "HUMIDITY", "occupancy": "MOTION", "presence": "PRESENCE",
            "battery": "BATTERY", "power": "ENERGY", "voltage": "VOLTAGE",
            "current": "CURRENT", "contact": "CONTACT", "illuminance": "ILLUMINANCE",
        }
        found: set[str] = set()
        def walk(items: list[Any]):
            for item in items:
                if not isinstance(item, dict): continue
                prop = item.get("property")
                if isinstance(prop, str) and prop in mapping: found.add(mapping[prop])
                features = item.get("features")
                if isinstance(features, list): walk(features)
        walk(exposes if isinstance(exposes, list) else [])
        return sorted(found)


    def execute_device_command(self, payload: bytes):
        """Ejecuta solo comandos Shelly/Tasmota hacia IPv4 privadas de la LAN."""
        try:
            from urllib.parse import quote
            from urllib.request import Request, urlopen
            command = json.loads(payload.decode("utf-8"))
            provider = command.get("provider")
            address = command.get("address")
            action = command.get("command")
            import ipaddress
            ip = ipaddress.ip_address(address)
            if ip.version != 4 or not ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast:
                raise ValueError("solo se permiten direcciones IPv4 privadas de la LAN")
            if action not in {"ON", "OFF", "TOGGLE", "BRIGHTNESS", "STATUS"}:
                raise ValueError("comando no permitido")
            if provider == "SHELLY":
                if action == "BRIGHTNESS": raise ValueError("brillo no disponible para este driver Shelly")
                if action == "STATUS": path = "/rpc/Switch.GetStatus?id=0"
                elif action == "TOGGLE": path = "/rpc/Switch.Toggle?id=0"
                else:
                    state = "true" if action == "ON" else "false"
                    path = f"/rpc/Switch.Set?id=0&on={state}"
            elif provider == "TASMOTA":
                tasmota_command = {"ON": "Power On", "OFF": "Power Off", "TOGGLE": "Power Toggle",
                    "BRIGHTNESS": f"Dimmer {max(0, min(100, int(command.get('brightness') or 0)))}", "STATUS": "Status 0"}[action]
                path = "/cm?cmnd=" + quote(tasmota_command)
            else:
                raise ValueError("proveedor no habilitado en Edge")
            request = Request(f"http://{ip}{path}", headers={"User-Agent": "HomeIA-Edge/1.0"})
            with urlopen(request, timeout=4) as response:
                if response.status < 200 or response.status >= 300: raise RuntimeError(f"HTTP {response.status}")
                response.read(4096)
            print(f"Comando {action} enviado a {provider} {ip}")
        except Exception as exc:
            print(f"Comando local rechazado/no ejecutado: {exc}")

    def ssdp_scan(self):
        message = ('M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n'
                   'MAN: "ssdp:discover"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n').encode()
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.settimeout(0.35)
        try:
            sock.sendto(message, ("239.255.255.250", 1900))
            deadline = time.time() + 2.5
            while time.time() < deadline:
                try: data, addr = sock.recvfrom(8192)
                except socket.timeout: continue
                text = data.decode("utf-8", "ignore")
                low = text.lower()
                provider = "SHELLY" if "shelly" in low else ("TASMOTA" if "tasmota" in low else None)
                if provider:
                    with self.lock:
                        self.candidates[f"{provider}|{addr[0]}"] = {"name": f"{provider.title()} {addr[0]}",
                            "provider": provider, "protocol": "WIFI", "address": addr[0],
                            "manufacturer": provider.title(), "model": None, "service": "ssdp", "capabilities": []}
        finally: sock.close()

    def scan_and_publish(self):
        print("Iniciando descubrimiento LAN")
        try:
            self.ssdp_scan()
            time.sleep(2.5)  # da tiempo a los callbacks mDNS para resolver los servicios
            self.publish_candidates()
        except Exception as exc: print(f"Error de descubrimiento: {exc}")

    def publish_candidates(self):
        with self.lock: snapshot = list(self.candidates.values())
        payload = json.dumps({"scanId": str(uuid.uuid4()), "candidates": snapshot})
        self.client.publish("homeia/edge/discovery", payload, qos=1, retain=True)

    def run(self):
        for service in ("_http._tcp.local.", "_shelly._tcp.local.", "_esphomelib._tcp.local.", "_tasmota._tcp.local."):
            try: ServiceBrowser(self.zc, service, self.listener)
            except Exception as exc: print(f"No se pudo iniciar browser {service}: {exc}")
        self.client.connect(BROKER, PORT, keepalive=45)
        self.client.loop_forever()

if __name__ == "__main__": Edge().run()
