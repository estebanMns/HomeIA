import json
import time
import types

from homeia_edge import security
from homeia_edge.adapters.base import Adapter
from homeia_edge.agent import Edge
from homeia_edge.config import BrokerConfig, EdgeConfig
from homeia_edge.store import Store

SECRET = "s3cr3t-0123456789abcdef0123456789abcdef"
EDGE_ID = "11111111-2222-3333-4444-555555555555"


class FakeClient:
    def __init__(self):
        self.published = []
        self.connected = True

    def publish(self, topic, payload, qos=0):
        self.published.append((topic, json.loads(payload) if payload.startswith("{") else payload))
        return types.SimpleNamespace(rc=0 if self.connected else 4)

    def subscribe(self, *a, **k):
        pass


class CountingAdapter(Adapter):
    provider = "SHELLY"

    def __init__(self):
        self.calls = 0

    def execute(self, device, command, brightness):
        self.calls += 1
        assert device.credentials == {"password": "x"}
        return {"powerState": "ON"}


def make_edge():
    broker = BrokerConfig("127.0.0.1", 1883, "", "", "c", False, None, None, None)
    cfg = EdgeConfig(EDGE_ID, SECRET, "homeia/edge", broker, None, "zigbee2mqtt", False, "/tmp", 30, 120)
    client = FakeClient()
    adapter = CountingAdapter()
    edge = Edge(cfg, store=Store(":memory:"), adapters={"SHELLY": adapter}, uplink=client, local=client)
    edge.uplink_connected = True
    return edge, client, adapter


def acks(client):
    return [json.loads(p["body"]) for t, p in client.published if t.endswith("/ack")]


def test_orden_se_ejecuta_una_vez_y_el_reintento_repite_la_respuesta():
    edge, client, adapter = make_edge()
    body = {"requestId": "r1", "deviceId": "d1", "provider": "SHELLY", "address": "192.168.1.2", "command": "ON",
            "expiresAt": int(time.time()) + 30,
            "credentials": security.encrypt(SECRET, EDGE_ID, '{"password":"x"}')}
    edge.execute_command(body)
    edge.execute_command(body)
    assert adapter.calls == 1
    first, second = acks(client)
    assert first["status"] == "OK" and first["state"] == {"powerState": "ON"}
    assert second["status"] == "DUPLICATE"


def test_orden_vencida_no_se_ejecuta():
    edge, client, adapter = make_edge()
    edge.execute_command({"requestId": "r2", "deviceId": "d1", "provider": "SHELLY", "address": "192.168.1.2",
                          "command": "ON", "expiresAt": int(time.time()) - 1})
    assert adapter.calls == 0 and acks(client)[0]["status"] == "EXPIRED"


def test_sin_conexion_los_acks_van_a_la_cola_y_se_reenvian_firmados():
    edge, client, adapter = make_edge()
    edge.uplink_connected = False
    edge.publish("ack", {"requestId": "r3", "status": "OK"})
    edge.publish("heartbeat", {"x": 1})  # el heartbeat no se encola
    assert edge.store.depth() == 1
    edge.uplink_connected = True
    edge.flush_outbox()
    assert edge.store.depth() == 0
    topic, envelope = client.published[-1]
    assert topic == f"homeia/edge/{EDGE_ID}/ack"
    assert security.verify(SECRET, EDGE_ID, "ack", envelope["ts"], envelope["nonce"], envelope["body"], envelope["sig"])


def test_mensajes_con_firma_invalida_se_descartan():
    edge, client, adapter = make_edge()
    bad = security.envelope("otro-secreto-0123456789abcdef0123456", EDGE_ID, "scan", {})
    edge._on_message(None, None, types.SimpleNamespace(topic=f"homeia/edge/{EDGE_ID}/scan", payload=bad.encode()))
    assert edge.metrics["rejectedMessages"] == 1


def test_sync_guarda_inventario_persistente():
    edge, client, adapter = make_edge()
    edge.apply_sync({"devices": [{"deviceId": "d1", "provider": "SHELLY", "address": "192.168.1.2"}], "pollSeconds": 5})
    assert edge.store.get("inventory")["d1"]["address"] == "192.168.1.2"
    assert edge.poll_seconds == 10  # mínimo
