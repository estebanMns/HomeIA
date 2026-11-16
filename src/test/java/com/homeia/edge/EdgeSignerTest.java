package com.homeia.edge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeSignerTest {

    private static final String SECRET = "s3cr3t-0123456789abcdef0123456789abcdef";
    private static final String EDGE = "11111111-2222-3333-4444-555555555555";

    /** Mismo vector que edge/tests/test_security.py: Java y Python deben firmar idéntico. */
    @Test
    void firmaCoincideConElAgentePython() {
        assertEquals("780e9862ef5954ee95e70ea60922ff6ffa6944cd905bf629b659a638ea3a2613",
                EdgeSigner.sign(SECRET, EDGE, "heartbeat", 1700000000L, "abc123", "{\"a\":1}"));
    }

    @Test
    void verificaSoloLaFirmaCorrecta() {
        String sig = EdgeSigner.sign(SECRET, EDGE, "ack", 10, "n", "{}");
        assertTrue(EdgeSigner.verify(SECRET, EDGE, "ack", 10, "n", "{}", sig));
        assertTrue(EdgeSigner.verify(SECRET, EDGE, "ack", 10, "n", "{}", sig.toUpperCase()));
        assertFalse(EdgeSigner.verify(SECRET, EDGE, "ack", 10, "n", "{\"x\":1}", sig));
        assertFalse(EdgeSigner.verify(SECRET, EDGE, "state", 10, "n", "{}", sig));
        assertFalse(EdgeSigner.verify("otro", EDGE, "ack", 10, "n", "{}", sig));
        assertFalse(EdgeSigner.verify(SECRET, EDGE, "ack", 10, "n", "{}", null));
    }

    @Test
    void descifraLoQueCifraElAgentePython() {
        assertEquals("{\"password\":\"x\"}", EdgeSigner.decryptFrom(SECRET, EDGE,
                "8x6wjDwci/iSuq/SbzfPigWOWLhYyTEb34FXWyydm531vqztuAfm797kcqo="));
    }

    @Test
    void cifradoAtadoAlEdge() {
        String data = EdgeSigner.encryptFor(SECRET, EDGE, "hola");
        assertEquals("hola", EdgeSigner.decryptFrom(SECRET, EDGE, data));
        assertThrows(IllegalArgumentException.class, () -> EdgeSigner.decryptFrom(SECRET, "otro", data));
    }

    @Test
    void secretosYNoncesAleatorios() {
        assertNotEquals(EdgeSigner.newSecret(), EdgeSigner.newSecret());
        assertEquals(43, EdgeSigner.newSecret().length());
        assertEquals(24, EdgeSigner.newNonce().length());
    }
}
