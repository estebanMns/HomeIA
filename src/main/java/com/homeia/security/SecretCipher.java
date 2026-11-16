package com.homeia.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado AES-256-GCM para secretos en reposo (secretos de Edge, credenciales de dispositivos).
 * Formato: "v1:" + base64(iv[12] | texto cifrado + tag[16]). Un texto manipulado no se descifra.
 */
public final class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretCipher(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("La clave de cifrado debe tener 32 bytes");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    /**
     * Clave desde HOMEIA_SECRETS_KEY (32 bytes en base64). Sin ella se deriva de la clave JWT con una etiqueta
     * propia: funciona, pero rotar SECRET_KEY dejaría ilegibles los secretos guardados.
     */
    public static SecretCipher fromConfig(String base64Key, String fallbackSecret) {
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
            return new SecretCipher(decoded);
        }
        if (fallbackSecret == null || fallbackSecret.isBlank()) {
            throw new IllegalStateException("Define HOMEIA_SECRETS_KEY (32 bytes en base64) o SECRET_KEY");
        }
        return new SecretCipher(sha256(("homeia-secrets-v1|" + fallbackSecret).getBytes(StandardCharsets.UTF_8)));
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Formato de secreto cifrado desconocido");
        }
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (all.length < IV_BYTES + 16) {
                throw new IllegalArgumentException("Secreto cifrado incompleto");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("El secreto no se pudo descifrar (clave distinta o datos alterados)", e);
        }
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
