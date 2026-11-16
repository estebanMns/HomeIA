import json

import pytest

from homeia_edge import security

SECRET = "s3cr3t-0123456789abcdef0123456789abcdef"
EDGE = "11111111-2222-3333-4444-555555555555"

# Vector compartido con EdgeSignerTest.java: si cambia el formato en un lado, falla en ambos.
VECTOR_SIG = "780e9862ef5954ee95e70ea60922ff6ffa6944cd905bf629b659a638ea3a2613"


def test_firma_coincide_con_el_vector_compartido_con_java():
    sig = security.sign(SECRET, EDGE, "heartbeat", 1700000000, "abc123", '{"a":1}')
    assert sig == VECTOR_SIG


def test_sobre_firmado_se_verifica_y_no_se_acepta_dos_veces():
    env = security.envelope(SECRET, EDGE, "command", {"requestId": "r1"}, ts=1000, nonce="n1")
    verifier = security.Verifier(SECRET, EDGE, 120, clock=lambda: 1010)
    assert verifier.open("command", env) == {"requestId": "r1"}
    with pytest.raises(security.EnvelopeError, match="repetido"):
        verifier.open("command", env)


def test_rechaza_firma_alterada_otro_edge_y_mensajes_viejos():
    verifier = security.Verifier(SECRET, EDGE, 120, clock=lambda: 1000)
    env = json.loads(security.envelope(SECRET, EDGE, "command", {"x": 1}, ts=1000, nonce="n2"))
    env["body"] = '{"x":2}'
    with pytest.raises(security.EnvelopeError, match="firma"):
        verifier.open("command", json.dumps(env))
    other = security.envelope(SECRET, "99999999-2222-3333-4444-555555555555", "command", {}, ts=1000, nonce="n3")
    with pytest.raises(security.EnvelopeError, match="otro Edge"):
        verifier.open("command", other)
    old = security.envelope(SECRET, EDGE, "command", {}, ts=500, nonce="n4")
    with pytest.raises(security.EnvelopeError, match="ventana"):
        verifier.open("command", old)
    wrong_kind = security.envelope(SECRET, EDGE, "sync", {}, ts=1000, nonce="n5")
    with pytest.raises(security.EnvelopeError):
        verifier.open("command", wrong_kind)


def test_cifrado_para_el_edge_es_reversible_y_atado_al_edge():
    data = security.encrypt(SECRET, EDGE, '{"password":"x"}')
    assert security.decrypt(SECRET, EDGE, data) == '{"password":"x"}'
    with pytest.raises(Exception):
        security.decrypt(SECRET, "otro-edge", data)
