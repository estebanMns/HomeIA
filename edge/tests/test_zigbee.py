import json

from homeia_edge import zigbee


def test_capacidades_desde_exposes_anidados():
    exposes = [{"type": "light", "features": [{"property": "state"}, {"property": "brightness"},
                                              {"property": "color_temp"}]},
               {"property": "linkquality"}, {"property": "battery"}]
    assert zigbee.capabilities(exposes) == ["BATTERY", "BRIGHTNESS", "COLOR_TEMPERATURE", "POWER"]


def test_inventario_ignora_coordinador_y_nombres_peligrosos():
    devices = [{"type": "Coordinator", "friendly_name": "Coordinator"},
               {"type": "Router", "friendly_name": "sala/lampara", "definition": {}},
               {"type": "EndDevice", "friendly_name": "sensor_puerta",
                "definition": {"vendor": "Aqara", "model": "MCCGQ11LM", "exposes": [{"property": "contact"}]}}]
    result = zigbee.candidates_from_inventory(devices)
    assert [c["address"] for c in result] == ["sensor_puerta"]
    assert result[0]["capabilities"] == ["CONTACT"]


def test_puente_publica_permit_join_y_lo_cierra(monkeypatch):
    published = []
    bridge = zigbee.ZigbeeBridge("zigbee2mqtt", lambda t, p: published.append((t, json.loads(p))),
                                 lambda e: None, lambda c: None)
    bridge.set_permit_join(500)  # se limita a 120
    assert published[-1] == ("zigbee2mqtt/bridge/request/permit_join", {"time": 120})
    bridge.set_permit_join(0)
    assert published[-1][1] == {"time": 0}
    assert bridge._close_timer is None


def test_puente_sigue_estado_eventos_y_estados_de_dispositivos():
    events = []
    bridge = zigbee.ZigbeeBridge("zigbee2mqtt", lambda t, p: None, events.append, lambda c: None)
    bridge.handle("zigbee2mqtt/bridge/state", b'{"state":"online"}')
    bridge.handle("zigbee2mqtt/bridge/info", b'{"permit_join":true}')
    bridge.handle("zigbee2mqtt/bridge/event", b'{"type":"device_joined","data":{"friendly_name":"0x01","ieee_address":"0x01"}}')
    assert bridge.heartbeat()["bridgeState"] == "online" and bridge.heartbeat()["permitJoin"] is True
    assert events[0]["type"] == "ZIGBEE_DEVICE_JOINED"
    assert bridge.handle("zigbee2mqtt/lampara", b'{"state":"ON","brightness":254}') == ("lampara", {"state": "ON", "brightness": 254})
    assert bridge.handle("zigbee2mqtt/lampara/set", b'{}') is None


def test_ordenes_validan_capacidades():
    assert zigbee.command_payload("BRIGHTNESS", 50, ["POWER", "BRIGHTNESS"]) == {"brightness": 127, "state": "ON"}
    for args in (("BRIGHTNESS", 50, ["POWER"]), ("ON", None, ["TEMPERATURE"]), ("BRIGHTNESS", 150, [])):
        try:
            zigbee.command_payload(*args)
            raise AssertionError(args)
        except ValueError:
            pass
