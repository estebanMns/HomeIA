package com.homeia.config;

import com.homeia.security.SecretCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SecretsConfig {

    @Bean
    public SecretCipher secretCipher(HomeIaProperties properties) {
        String key = properties.security() == null ? null : properties.security().secretsKey();
        if (key == null || key.isBlank()) {
            log.warn("HOMEIA_SECRETS_KEY no está definido: los secretos se cifran con una clave derivada de SECRET_KEY. "
                    + "Define HOMEIA_SECRETS_KEY (openssl rand -base64 32) antes de producción.");
        }
        return SecretCipher.fromConfig(key, properties.jwt() == null ? null : properties.jwt().secret());
    }
}
