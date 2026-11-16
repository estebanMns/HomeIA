package com.homeia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.homeia.integration.Json;
import com.homeia.persistence.DeviceCredentialEntity;
import com.homeia.persistence.DeviceCredentialRepository;
import com.homeia.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Credenciales locales por dispositivo (usuario/contraseña HTTP de Shelly o Tasmota, clave de cifrado
 * ESPHome). Se guardan cifradas con AES-256-GCM, nunca se devuelven por la API ni se escriben en el log y
 * solo viajan al Edge cifradas con la clave de ese Edge.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCredentialService {

    public record Credentials(String username, String password, String encryptionKey) {
        public boolean isEmpty() {
            return blank(username) && blank(password) && blank(encryptionKey);
        }

        private static boolean blank(String v) {
            return v == null || v.isBlank();
        }
    }

    private final DeviceCredentialRepository repository;
    private final SecretCipher cipher;

    @Transactional
    public void set(UUID deviceId, Credentials credentials) {
        if (credentials == null || credentials.isEmpty()) {
            repository.deleteById(deviceId);
            return;
        }
        check(credentials.username(), 64, "usuario");
        check(credentials.password(), 128, "contraseña");
        check(credentials.encryptionKey(), 128, "clave de cifrado");
        Map<String, String> plain = new LinkedHashMap<>();
        if (credentials.username() != null && !credentials.username().isBlank()) plain.put("username", credentials.username());
        if (credentials.password() != null && !credentials.password().isBlank()) plain.put("password", credentials.password());
        if (credentials.encryptionKey() != null && !credentials.encryptionKey().isBlank()) {
            plain.put("encryptionKey", credentials.encryptionKey().trim());
        }
        DeviceCredentialEntity entity = repository.findById(deviceId)
                .orElseGet(() -> new DeviceCredentialEntity(deviceId, ""));
        entity.setCiphertext(cipher.encrypt(Json.write(plain)));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean has(UUID deviceId) {
        return repository.existsById(deviceId);
    }

    /** Credenciales en claro, solo para cifrarlas de nuevo hacia el Edge. */
    @Transactional(readOnly = true)
    public Optional<Map<String, String>> plaintext(UUID deviceId) {
        // No lanza: se usa dentro de otras transacciones y una excepción las marcaría para revertirse.
        return repository.findById(deviceId).flatMap(entity -> {
            try {
                JsonNode node = Json.read(cipher.decrypt(entity.getCiphertext()));
                Map<String, String> result = new LinkedHashMap<>();
                node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
                return Optional.of(result);
            } catch (RuntimeException e) {
                log.error("No se pudieron descifrar las credenciales del dispositivo {} (¿cambió HOMEIA_SECRETS_KEY?)", deviceId);
                return Optional.empty();
            }
        });
    }

    private static void check(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw ApiException.badRequest("El campo " + label + " supera " + max + " caracteres");
        }
    }
}
