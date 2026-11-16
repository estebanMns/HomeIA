import pytest

from homeia_edge.adapters.base import AdapterError, Device, UnsupportedCommand
from homeia_edge.adapters.http import request_json
from homeia_edge.adapters.shelly import ShellyAdapter
from homeia_edge.adapters.tasmota import TasmotaAdapter
from homeia_edge.adapters.wled import WledAdapter


class FakeHttp:
    def __init__(self, routes):
        self.routes = routes
        self.calls = []

    def __call__(self, address, path, **kwargs):
        self.calls.append((path, kwargs))
        for prefix, response in self.routes.items():
            if path.startswith(prefix):
                return response(path, kwargs) if callable(response) else response
        raise AdapterError("ruta no simulada " + path)


def test_shelly_gen2_rele_con_digest_y_lectura_de_potencia():
    http = FakeHttp({"/shelly": {"gen": 2, "model": "SNPL-00112EU"},
                     "/rpc/Switch.Set": {"was_on": False},
                     "/rpc/Switch.GetStatus": {"output": True, "apower": 12.5, "voltage": 120.1,
                                               "aenergy": {"total": 1500}}})
    device = Device("d1", "SHELLY", "192.168.1.20", "SMART_OUTLET", ["POWER", "ENERGY"],
                    {"password": "clave"})
    state = ShellyAdapter(http).execute(device, "ON", None)
    assert state == {"powerState": "ON", "consumptionWatts": 12.5,
                     "telemetry": {"voltage": 120.1, "energyKwh": 1.5}}
    set_call = next(c for c in http.calls if c[0].startswith("/rpc/Switch.Set"))
    assert "on=true" in set_call[0]
    assert set_call[1]["digest"] is True and set_call[1]["username"] == "admin"


def test_shelly_gen1_dimmer():
    http = FakeHttp({"/shelly": {"type": "SHDM-2"},
                     "/light/0": {},
                     "/status": {"lights": [{"ison": True, "brightness": 40}], "meters": [{"power": 7}]}})
    device = Device("d2", "SHELLY", "10.0.0.5", "LIGHT", ["POWER", "BRIGHTNESS"])
    state = ShellyAdapter(http).execute(device, "BRIGHTNESS", 40)
    assert state == {"powerState": "ON", "brightness": 40, "consumptionWatts": 7.0}
    assert any("brightness=40" in c[0] for c in http.calls)


def test_shelly_rechaza_brillo_si_no_lo_anuncia():
    http = FakeHttp({"/shelly": {"gen": 2}})
    with pytest.raises(UnsupportedCommand):
        ShellyAdapter(http).execute(Device("d", "SHELLY", "192.168.1.2", "LIGHT", ["POWER"]), "BRIGHTNESS", 50)


def test_tasmota_con_contrasena_y_medidor():
    http = FakeHttp({"/cm?cmnd=Power": {"POWER": "OFF"},
                     "/cm?cmnd=State": {"POWER": "OFF", "Dimmer": 0},
                     "/cm?cmnd=Status": {"StatusSNS": {"ENERGY": {"Power": 0, "Voltage": 119, "Total": 3.2}}}})
    device = Device("t", "TASMOTA", "192.168.0.9", "SMART_OUTLET", [], {"username": "admin", "password": "p w"})
    state = TasmotaAdapter(http).execute(device, "OFF", None)
    assert state["powerState"] == "OFF" and state["consumptionWatts"] == 0.0
    assert state["telemetry"] == {"voltage": 119.0, "energyKwh": 3.2}
    assert "password=p+w" in http.calls[0][0] and "user=admin" in http.calls[0][0]


def test_tasmota_informa_aviso_de_autenticacion():
    http = FakeHttp({"/cm": {"WARNING": "Need user=<username>&password=<password>"}})
    with pytest.raises(AdapterError, match="Need user"):
        TasmotaAdapter(http).execute(Device("t", "TASMOTA", "192.168.0.9"), "ON", None)


def test_wled_brillo_se_convierte_a_0_255():
    posted = {}

    def state(path, kwargs):
        if kwargs.get("method") == "POST":
            posted.update(kwargs["body"])
            return {"success": True}
        return {"on": True, "bri": 128}

    state_result = WledAdapter(FakeHttp({"/json/state": state})).execute(
        Device("w", "WLED", "192.168.1.30"), "BRIGHTNESS", 50)
    assert posted == {"on": True, "bri": 128}
    assert state_result == {"powerState": "ON", "brightness": 50}


@pytest.mark.parametrize("address", ["127.0.0.1", "8.8.8.8", "169.254.1.1", "192.168.1.255", "localhost",
                                     "192.168.1.1:80", "http://192.168.1.2"])
def test_http_solo_permite_ipv4_privadas(address):
    with pytest.raises(ValueError):
        request_json(address, "/shelly")
