package com.homeia.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aplica todas las migraciones sobre un PostgreSQL real y comprueba las tablas/columnas de las fases 4-6.
 * Requiere Docker; sin Docker la prueba se omite (no falla).
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationsIntegrationTest {

    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:17.6"))
            .withEnv("POSTGRES_PASSWORD", "test")
            .withEnv("POSTGRES_DB", "homeia")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    private String url() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/homeia";
    }

    @Test
    void aplicaTodasLasMigracionesYCreaElEsquemaEdge() throws Exception {
        Flyway.configure()
                .dataSource(url(), "postgres", "test")
                .locations("classpath:db/homeia/migration")
                .schemas("homeia")
                .defaultSchema("homeia")
                .createSchemas(true)
                .load()
                .migrate();
        try (Connection c = DriverManager.getConnection(url(), "postgres", "test")) {
            Set<String> tables = names(c.getMetaData().getTables(null, "homeia", "%", new String[]{"TABLE"}), "TABLE_NAME");
            for (String t : new String[]{"edge_nodes", "device_commands", "device_events", "device_credentials"}) {
                assertTrue(tables.contains(t), "falta la tabla " + t);
            }
            Set<String> deviceColumns = names(c.getMetaData().getColumns(null, "homeia", "devices", "%"), "COLUMN_NAME");
            for (String col : new String[]{"protocol", "capabilities_json", "network_address", "edge_id", "telemetry_json"}) {
                assertTrue(deviceColumns.contains(col), "falta la columna devices." + col);
            }
        }
    }

    private static Set<String> names(ResultSet rs, String column) throws Exception {
        Set<String> names = new HashSet<>();
        try (rs) {
            while (rs.next()) names.add(rs.getString(column));
        }
        return names;
    }
}
