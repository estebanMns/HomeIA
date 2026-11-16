package com.homeia.edge;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Autenticación mutua backend ↔ HomeIA Edge sobre MQTT. Cada mensaje (en ambos sentidos) va firmado con
 * HMAC-SHA256 usando el secreto propio del Edge, así un Edge no puede hacerse pasar por otro ni por el
 * backend, aunque compartan broker. Debe coincidir con edge/homeia_edge/security.py.
 *
 * Texto firmado: "v1\n{edgeId}\n{kind}\n{ts}\n{nonce}\n{body}"
 */
public final class EdgeSigner {

    public static final String VERSION = "v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private EdgeSigner() {
    }

    public static String canonical(String edgeId, String kind, long ts, String nonce, String body) {
        return VERSION + "\n" + edgeId + "\n" + kind + "\n" + ts + "\n" + nonce + "\n" + body;
    }

    public static String sign(String secret, String edgeId, String kind, long ts, String nonce, String body) {
        return HexFormat.of().formatHex(hmac(secret.getBytes(StandardCharsets.UTF_8),
                canonical(edgeId, kind, ts, nonce, body).getBytes(StandardCharsets.UTF_8)));
    }

    /** Comparación en tiempo constante para no filtrar cuántos caracteres coinciden. */
    public static boolean verify(String secret, String edgeId, String kind, long ts, String nonce, String body,
                                 String signature) {
        if (secret == null || signature == null) return false;
        byte[] expected = sign(secret, edgeId, kind, ts, nonce, body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    /** Secreto nuevo de 32 bytes aleatorios en base64url (lo que se copia en la configuración del Edge). */
    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String newNonce() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Cifra datos sensibles (credenciales de un dispositivo) para un Edge concreto. Solo ese Edge puede leerlos,
     * aunque el broker o el canal no tengan TLS. Clave = HMAC(secreto, "homeia-edge-enc-v1"), AAD = edgeId.
     */
    public static String encryptFor(String secret, String edgeId, String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey(secret), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(edgeId.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar para el Edge", e);
        }
    }

    public static String decryptFrom(String secret, String edgeId, String ciphertext) {
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(secret), "AES"),
                    new GCMParameterSpec(128, all, 0, 12));
            cipher.updateAAD(edgeId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("No se pudo descifrar el dato del Edge", e);
        }
    }

    static byte[] encryptionKey(String secret) {
        return hmac(secret.getBytes(StandardCharsets.UTF_8), "homeia-edge-enc-v1".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
