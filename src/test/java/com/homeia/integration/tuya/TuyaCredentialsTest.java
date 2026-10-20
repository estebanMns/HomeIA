package com.homeia.integration.tuya;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TuyaCredentialsTest {

    @Test
    void quitaComillasYEspaciosDeLasCredenciales() {
        assertEquals("abc123", TuyaCredentials.clean("\"abc123\""));
        assertEquals("abc123", TuyaCredentials.clean("  'abc123'  "));
        assertEquals("abc123", TuyaCredentials.clean(" abc123 "));
        assertEquals("abc123", TuyaCredentials.clean("\" abc123 \""));
        assertEquals("", TuyaCredentials.clean(null));
    }
}
