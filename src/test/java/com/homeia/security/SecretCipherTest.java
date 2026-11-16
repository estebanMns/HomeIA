package com.homeia.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    @Test
    void cifraYDescifraConIvAleatorio() {
        SecretCipher cipher = SecretCipher.fromConfig(Base64.getEncoder().encodeToString(new byte[32]), null);
        String a = cipher.encrypt("clave-shelly");
        String b = cipher.encrypt("clave-shelly");
        assertNotEquals(a, b);
        assertTrue(a.startsWith("v1:"));
        assertEquals("clave-shelly", cipher.decrypt(a));
    }

    @Test
    void detectaManipulacionYClaveDistinta() {
        SecretCipher cipher = SecretCipher.fromConfig(null, "jwt-secret");
        String data = cipher.encrypt("x");
        char[] chars = data.toCharArray();
        chars[10] = chars[10] == 'A' ? 'B' : 'A';
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new String(chars)));
        assertThrows(IllegalArgumentException.class, () -> SecretCipher.fromConfig(null, "otra").decrypt(data));
    }

    @Test
    void exigeClaveDe32Bytes() {
        assertThrows(IllegalArgumentException.class,
                () -> SecretCipher.fromConfig(Base64.getEncoder().encodeToString(new byte[16]), null));
        assertThrows(IllegalStateException.class, () -> SecretCipher.fromConfig("", ""));
    }
}
