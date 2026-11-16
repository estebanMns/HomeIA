-- Fase 4-6: HomeIA Edge enrolado por hogar, órdenes con confirmación, historial por dispositivo
-- y credenciales de dispositivo cifradas.

-- Cada Edge pertenece a una casa y tiene su propio secreto (cifrado en reposo con HOMEIA_SECRETS_KEY).
-- Durante una rotación el secreto anterior sigue siendo válido hasta previous_valid_until.
CREATE TABLE edge_nodes (
    id                         UUID PRIMARY KEY,
    home_id                    UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    name                       VARCHAR(120) NOT NULL,
    secret_ciphertext          TEXT         NOT NULL,
    previous_secret_ciphertext TEXT,
    previous_valid_until       TIMESTAMPTZ,
    status                     VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    version                    VARCHAR(40),
    platform                   VARCHAR(120),
    zigbee_bridge_state        VARCHAR(16),
    zigbee_permit_join         BOOLEAN      NOT NULL DEFAULT FALSE,
    zigbee_permit_join_until   TIMESTAMPTZ,
    queue_depth                INTEGER      NOT NULL DEFAULT 0,
    metrics_json               TEXT         NOT NULL DEFAULT '{}',
    last_heartbeat_at          TIMESTAMPTZ,
    last_hello_at              TIMESTAMPTZ,
    rotated_at                 TIMESTAMPTZ,
    revoked_at                 TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_edge_nodes_home ON edge_nodes (home_id);

-- El dispositivo LAN se controla a través de un Edge concreto; la última telemetría normalizada
-- (temperatura, humedad, batería, contacto…) se guarda como JSON.
ALTER TABLE devices ADD COLUMN edge_id UUID REFERENCES edge_nodes (id) ON DELETE SET NULL;
ALTER TABLE devices ADD COLUMN telemetry_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE devices ADD COLUMN telemetry_at TIMESTAMPTZ;
CREATE INDEX idx_devices_edge ON devices (edge_id);

-- Orden enviada a un dispositivo. id = requestId: el Edge lo usa para deduplicar los reintentos.
CREATE TABLE device_commands (
    id            UUID PRIMARY KEY,
    home_id       UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    device_id     UUID         NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    edge_id       UUID         REFERENCES edge_nodes (id) ON DELETE SET NULL,
    command       VARCHAR(16)  NOT NULL,
    brightness    INTEGER,
    status        VARCHAR(16)  NOT NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    error         VARCHAR(500),
    requested_by  VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_sent_at  TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_device_commands_pending ON device_commands (status, last_sent_at);
CREATE INDEX idx_device_commands_device ON device_commands (device_id, created_at);

-- Historial por dispositivo: descubrimiento, importación, conexión, desconexión, órdenes y errores.
CREATE TABLE device_events (
    id          UUID PRIMARY KEY,
    home_id     UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    device_id   UUID         REFERENCES devices (id) ON DELETE CASCADE,
    edge_id     UUID         REFERENCES edge_nodes (id) ON DELETE SET NULL,
    type        VARCHAR(40)  NOT NULL,
    details     VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_events_device ON device_events (device_id, created_at);
CREATE INDEX idx_device_events_home ON device_events (home_id, created_at);

-- Credenciales locales (contraseña HTTP de Shelly/Tasmota, clave de cifrado ESPHome).
-- Solo texto cifrado con AES-256-GCM; nunca se guardan en metadatos ni se devuelven por la API.
CREATE TABLE device_credentials (
    device_id   UUID PRIMARY KEY REFERENCES devices (id) ON DELETE CASCADE,
    ciphertext  TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
