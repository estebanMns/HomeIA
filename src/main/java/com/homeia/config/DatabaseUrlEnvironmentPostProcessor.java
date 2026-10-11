package com.homeia.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte DATABASE_URL (formato SQLAlchemy, p. ej. postgresql+asyncpg://user:pass@host:5432/db)
 * a la configuración JDBC que necesita Spring. Así las credenciales viven en un solo lugar (.env)
 * y el esquema propio "homeia" se fija en la conexión.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Pattern DATABASE_URL = Pattern.compile(
            "^postgres(?:ql)?(?:\\+\\w+)?://([^:/@]+):(.*)@([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?$");

    private static final String NORMALIZED_SOURCE = "homeia-normalized-env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new HashMap<>();

        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()
                && environment.getProperty("spring.datasource.url") == null) {
            Matcher matcher = DATABASE_URL.matcher(databaseUrl.trim());
            if (!matcher.matches()) {
                throw new IllegalStateException("DATABASE_URL no tiene el formato postgresql://usuario:clave@host:puerto/base");
            }
            String username = decode(matcher.group(1));
            String password = decode(matcher.group(2));
            String host = matcher.group(3);
            String port = matcher.group(4) != null ? matcher.group(4) : "5432";
            String database = matcher.group(5);
            String query = matcher.group(6) != null ? matcher.group(6) : "";

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database
                    + "?currentSchema=homeia"
                    + (query.contains("sslmode") ? "" : "&sslmode=require")
                    + (query.isBlank() ? "" : "&" + query);

            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);
            props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        }

        // Algunas claves del .env vienen entre comillas: se normalizan aquí.
        String geminiKey = environment.getProperty("GEMINI_API_KEY");
        if (geminiKey != null) {
            props.put("GEMINI_API_KEY", geminiKey.replace("\"", "").trim());
        }

        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(NORMALIZED_SOURCE, props));
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
