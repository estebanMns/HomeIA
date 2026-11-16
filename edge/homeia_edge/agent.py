"""Proceso principal de HomeIA Edge.

Canales:
- uplink: broker que comparte con el backend (puede ser el local si el backend está en la LAN o por VPN, o un broker
  en la nube con mTLS: el Edge sale hacia él y no hay que abrir puertos en la casa).
- local: broker de la LAN donde corre Zigbee2MQTT.

Tópicos uplink: {root}/{edgeId}/{tipo}. Todos los mensajes van firmados (security.py).
  Edge -> backend: hello, heartbeat, discovery, ack, state, event
  backend -> Edge: command, sync, scan, permit_join
"""
from __future__ import annotations

import json
import os
import platform
import signal
import ssl
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from . import __version__, config as config_module, security
from .adapters import AdapterError, Device, UnsupportedCommand, default_adapters
from .adapters.http import request_json
from .discovery import Discovery
from .store import Store
from .zigbee import ZigbeeBridge

QUEUED_KINDS = {"ack", "state", "event", "discovery"}
LAN_PROVIDERS = {"SHELLY", "TASMOTA", "ESPHOME", "WLED"}


def _mqtt_client(cfg, client_id: str):
    import paho.mqtt.client as mqtt
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id, clean_session=True)
    if cfg.username:
        client.username_pw_set(cfg.username, cfg.password)
    if cfg.tls:
        client.tls_set(ca_certs=cfg.ca_cert, certfile=cfg.client_cert, keyfile=cfg.client_key,
                       cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLS_CLIENT)
    client.reconnect_delay_set(min_delay=1, max_delay=60)
    client.max_queued_messages_set(1000)
    return client


class Edge:
    def __init__(self, cfg, store: Store | None = None, adapters=None, uplink=None, local=None):
        self.cfg = cfg
        self.store = store or Store(os.path.join(cfg.data_dir, "edge.db"))
        self.adapters = adapters if adapters is not None else default_adapters()
        self.verifier = security.Verifier(cfg.secret, cfg.edge_id, cfg.replay_window_seconds)
        self.started = time.time()
        self.metrics = {"commandsOk": 0, "commandsFailed": 0, "commandsDuplicate": 0, "rejectedMessages": 0}
        self.inventory: dict[str, dict[str, Any]] = self.store.get("inventory", {})
        self.poll_seconds = int(self.store.get("pollSeconds", 30))
        self.reachable: dict[str, bool] = {}
        self.last_zigbee_state: dict[str, tuple[float, str]] = {}
        self.in_flight: set[str] = set()
        self.lock = threading.Lock()
        self.pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="edge-cmd")
        self.stop_event = threading.Event()
        self.uplink = uplink or _mqtt_client(cfg.uplink or cfg.local, cfg.edge_id)
        separate_local = cfg.uplink is not None
        self.local = local or (_mqtt_client(cfg.local, cfg.local.client_id) if separate_local else self.uplink)
        self.uplink_connected = False
        self.zigbee = ZigbeeBridge(cfg.zigbee_base_topic, self._publish_local, self._zigbee_event,
                                   self._zigbee_inventory) if cfg.zigbee_enabled else None
        self.discovery = Discovery(self._discovered, http_probe=request_json)
        self.uplink.on_connect = self._on_uplink_connect
        self.uplink.on_disconnect = self._on_uplink_disconnect
        self.uplink.on_message = self._on_message
        if self.local is not self.uplink:
            self.local.on_connect = self._on_local_connect
            self.local.on_message = self._on_message

    # ---------------- MQTT ----------------
    def _topic(self, kind: str) -> str:
        return f"{self.cfg.base}/{kind}"

    def _on_uplink_connect(self, client, userdata, flags, reason_code, properties=None):
        if getattr(reason_code, "is_failure", False):
            print(f"Conexión uplink rechazada: {reason_code}")
            return
        self.uplink_connected = True
        for kind in ("command", "sync", "scan", "permit_join"):
            client.subscribe(self._topic(kind), qos=1)
        if self.local is self.uplink:
            self._subscribe_zigbee(client)
        print(f"Conectado al broker uplink; Edge {self.cfg.edge_id}")
        self.publish("hello", {"version": __version__, "platform": f"{platform.system()} {platform.machine()}",
                               "python": platform.python_version(), "inventory": len(self.inventory),
                               "providers": sorted(LAN_PROVIDERS | ({"ZIGBEE"} if self.zigbee else set()))})
        threading.Thread(target=self.flush_outbox, daemon=True).start()

    def _on_uplink_disconnect(self, client, userdata, flags, reason_code, properties=None):
        self.uplink_connected = False
        print(f"Uplink desconectado ({reason_code}); los eventos se guardan en la cola local")

    def _on_local_connect(self, client, userdata, flags, reason_code, properties=None):
        self._subscribe_zigbee(client)

    def _subscribe_zigbee(self, client):
        if self.zigbee:
            for topic in self.zigbee.topics():
                client.subscribe(topic, qos=0)

    def _publish_local(self, topic: str, payload: str) -> None:
        self.local.publish(topic, payload, qos=1)

    def publish(self, kind: str, body: dict[str, Any]) -> bool:
        """Firma y publica. Si no hay conexión, encola los mensajes importantes para reenviarlos después."""
        if self.uplink_connected:
            info = self.uplink.publish(self._topic(kind), security.envelope(self.cfg.secret, self.cfg.edge_id, kind, body), qos=1)
            if getattr(info, "rc", 0) == 0:
                return True
        if kind in QUEUED_KINDS:
            self.store.enqueue(kind, body)
        return False

    def flush_outbox(self) -> None:
        while self.uplink_connected:
            batch = self.store.pending(50)
            if not batch:
                return
            for message_id, kind, body in batch:
                if not self.uplink_connected:
                    return
                info = self.uplink.publish(self._topic(kind),
                                           security.envelope(self.cfg.secret, self.cfg.edge_id, kind, body), qos=1)
                if getattr(info, "rc", 0) != 0:
                    return
                self.store.remove(message_id)
            time.sleep(0.2)

    def _on_message(self, client, userdata, msg):
        topic = msg.topic
        if topic.startswith(self.cfg.base + "/"):
            kind = topic[len(self.cfg.base) + 1:]
            try:
                body = self.verifier.open(kind, msg.payload)
            except security.EnvelopeError as exc:
                self.metrics["rejectedMessages"] += 1
                print(f"Mensaje {kind} rechazado: {exc}")
                return
            self.handle(kind, body)
            return
        if self.zigbee:
            result = self.zigbee.handle(topic, msg.payload)
            if result:
                self._forward_zigbee_state(*result)

    # ---------------- Mensajes del backend ----------------
    def handle(self, kind: str, body: dict[str, Any]) -> None:
        if kind == "command":
            self.pool.submit(self.execute_command, body)
        elif kind == "sync":
            self.apply_sync(body)
        elif kind == "scan":
            threading.Thread(target=self.discovery.scan, daemon=True).start()
        elif kind == "permit_join":
            if not self.zigbee:
                self.publish("event", {"type": "DEVICE_ERROR", "details": "Zigbee no está habilitado en este Edge"})
                return
            self.zigbee.set_permit_join(int(body.get("seconds", 0)))

    def apply_sync(self, body: dict[str, Any]) -> None:
        devices = {}
        for item in body.get("devices") or []:
            if isinstance(item, dict) and item.get("deviceId") and item.get("provider"):
                devices[item["deviceId"]] = item
        with self.lock:
            self.inventory = devices
            self.poll_seconds = max(10, int(body.get("pollSeconds") or 30))
        self.store.put("inventory", devices)
        self.store.put("pollSeconds", self.poll_seconds)
        print(f"Inventario sincronizado: {len(devices)} dispositivo(s)")
        if self.zigbee:
            # Pide el estado actual de los dispositivos Zigbee tras un reinicio.
            for item in devices.values():
                if item.get("provider") == "ZIGBEE":
                    try:
                        self.zigbee.send(item["address"], "STATUS", None, item.get("capabilities") or [])
                    except ValueError:
                        pass

    def _device(self, body: dict[str, Any]) -> Device:
        creds: dict[str, str] = {}
        if body.get("credentials"):
            creds = json.loads(security.decrypt(self.cfg.secret, self.cfg.edge_id, body["credentials"]))
        return Device(device_id=str(body.get("deviceId")), provider=str(body.get("provider")),
                      address=str(body.get("address") or ""), type=str(body.get("type") or "LIGHT"),
                      capabilities=[c for c in (body.get("capabilities") or []) if isinstance(c, str)],
                      credentials=creds)

    def execute_command(self, body: dict[str, Any]) -> dict[str, Any]:
        request_id = str(body.get("requestId") or "")
        base = {"requestId": request_id, "deviceId": body.get("deviceId"), "provider": body.get("provider")}
        if not request_id:
            return {}
        previous = self.store.recall(request_id)
        if previous is not None:
            # Reintento del backend: no se repite la acción, se repite la respuesta.
            self.metrics["commandsDuplicate"] += 1
            ack = dict(previous)
            if ack.get("status") == "OK":
                ack["status"] = "DUPLICATE"
            self.publish("ack", ack)
            return ack
        with self.lock:
            if request_id in self.in_flight:
                return {}
            self.in_flight.add(request_id)
        try:
            if int(body.get("expiresAt") or 0) < time.time():
                ack = {**base, "status": "EXPIRED", "error": "la orden llegó vencida"}
            else:
                ack = {**base, **self._run(body)}
            self.store.remember(request_id, ack)
            self.metrics["commandsOk" if ack["status"] == "OK" else "commandsFailed"] += 1
            self.publish("ack", ack)
            return ack
        finally:
            with self.lock:
                self.in_flight.discard(request_id)

    def _run(self, body: dict[str, Any]) -> dict[str, Any]:
        provider = body.get("provider")
        command = str(body.get("command"))
        brightness = body.get("brightness")
        try:
            device = self._device(body)
            if provider == "ZIGBEE":
                if not self.zigbee:
                    raise AdapterError("Zigbee no está habilitado en este Edge")
                self.zigbee.send(device.address, command, brightness, device.capabilities)
                return {"status": "OK"}  # el estado llega cuando Zigbee2MQTT lo publique
            adapter = self.adapters.get(provider)
            if adapter is None:
                raise AdapterError(f"proveedor {provider} no habilitado en este Edge")
            state = adapter.execute(device, command, brightness)
            self.reachable[device.device_id] = True
            return {"status": "OK", "state": state}
        except (AdapterError, UnsupportedCommand, ValueError) as exc:
            return {"status": "FAILED", "error": str(exc)[:400]}
        except Exception as exc:  # noqa: BLE001 - la orden no debe tumbar el agente
            return {"status": "FAILED", "error": f"error interno: {exc.__class__.__name__}"}

    # ---------------- Zigbee ----------------
    def _zigbee_event(self, event: dict) -> None:
        self.publish("event", event)

    def _zigbee_inventory(self, candidates: list) -> None:
        for c in candidates:
            with self.discovery.lock:
                self.discovery.candidates[f"ZIGBEE|{c['address']}"] = c
        self._discovered(candidates, only=True)

    def _forward_zigbee_state(self, friendly: str, state: dict) -> None:
        device_id = next((d for d, item in self.inventory.items()
                          if item.get("provider") == "ZIGBEE" and item.get("address") == friendly), None)
        if not device_id:
            return
        serialized = json.dumps(state, sort_keys=True)
        if len(serialized) > 16_384:
            return
        now = time.time()
        last = self.last_zigbee_state.get(device_id)
        if last and last[1] == serialized and now - last[0] < 30:
            return  # sin cambios: no saturar el uplink
        self.last_zigbee_state[device_id] = (now, serialized)
        self.publish("state", {"deviceId": device_id, "provider": "ZIGBEE", "state": state})

    # ---------------- Descubrimiento ----------------
    def _discovered(self, candidates: list, only: bool = False) -> None:
        if only:
            with self.discovery.lock:
                candidates = list(self.discovery.candidates.values())
        self.publish("discovery", {"scanId": str(int(time.time())), "candidates": candidates[:500]})

    # ---------------- Bucles ----------------
    def heartbeat_loop(self) -> None:
        while not self.stop_event.wait(self.cfg.heartbeat_seconds):
            self.publish("heartbeat", self.heartbeat_body())

    def heartbeat_body(self) -> dict[str, Any]:
        body: dict[str, Any] = {"version": __version__, "uptimeSeconds": int(time.time() - self.started),
                                "queueDepth": self.store.depth(), "devices": len(self.inventory),
                                "metrics": {**self.metrics, "loadAvg1": round(os.getloadavg()[0], 2) if hasattr(os, "getloadavg") else None}}
        if self.zigbee and self.zigbee.heartbeat():
            body["zigbee"] = self.zigbee.heartbeat()
        return body

    def poll_loop(self) -> None:
        """Lee el estado de los dispositivos Wi-Fi para confirmar cambios hechos fuera de HomeIA."""
        while not self.stop_event.wait(self.poll_seconds):
            for device_id, item in list(self.inventory.items()):
                if item.get("provider") not in LAN_PROVIDERS or self.stop_event.is_set():
                    continue
                adapter = self.adapters.get(item["provider"])
                if not adapter:
                    continue
                try:
                    state = adapter.read_state(self._device(item))
                    if self.reachable.get(device_id) is False:
                        print(f"{device_id} vuelve a responder")
                    self.reachable[device_id] = True
                    self.publish("state", {"deviceId": device_id, "provider": item["provider"], "state": state})
                except Exception as exc:  # noqa: BLE001
                    if self.reachable.get(device_id, True):
                        self.publish("state", {"deviceId": device_id, "provider": item["provider"],
                                               "state": {"reachable": False}})
                        self.publish("event", {"type": "DEVICE_UNREACHABLE", "deviceId": device_id,
                                               "details": str(exc)[:300]})
                    self.reachable[device_id] = False

    def run(self) -> None:
        signal.signal(signal.SIGTERM, lambda *_: self.stop())
        self.discovery.start_mdns()
        up = self.cfg.uplink or self.cfg.local
        self.uplink.connect_async(up.host, up.port, keepalive=45)
        self.uplink.loop_start()
        if self.local is not self.uplink:
            self.local.connect_async(self.cfg.local.host, self.cfg.local.port, keepalive=45)
            self.local.loop_start()
        threading.Thread(target=self.heartbeat_loop, daemon=True, name="heartbeat").start()
        threading.Thread(target=self.poll_loop, daemon=True, name="poll").start()
        self.stop_event.wait()

    def stop(self) -> None:
        self.stop_event.set()
        for client in {id(self.uplink): self.uplink, id(self.local): self.local}.values():
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:  # noqa: BLE001
                pass


def main() -> None:
    cfg = config_module.load()
    print(f"HomeIA Edge {__version__} · {cfg.edge_id} · uplink {(cfg.uplink or cfg.local).host}")
    Edge(cfg).run()
