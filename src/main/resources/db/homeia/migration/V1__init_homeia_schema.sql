-- Esquema propio de HomeIA. Vive en el schema "homeia" para no mezclarse
-- con las tablas de otros proyectos que comparten la misma base de datos.

CREATE TABLE homes (
    id                          UUID PRIMARY KEY,
    name                        VARCHAR(120)   NOT NULL,
    currency                    VARCHAR(3)     NOT NULL,
    electricity_price_per_kwh   NUMERIC(12, 2) NOT NULL,
    timezone                    VARCHAR(64)    NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    home_id         UUID          NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100)  NOT NULL,
    full_name       VARCHAR(120)  NOT NULL,
    role            VARCHAR(20)   NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_home ON users (home_id);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE rooms (
    id          UUID PRIMARY KEY,
    home_id     UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    location    VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_rooms_home_name UNIQUE (home_id, name)
);

CREATE TABLE devices (
    id                      UUID PRIMARY KEY,
    home_id                 UUID             NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    room_id                 UUID             REFERENCES rooms (id) ON DELETE SET NULL,
    name                    VARCHAR(120)     NOT NULL,
    type                    VARCHAR(30)      NOT NULL,
    connection_status       VARCHAR(20)      NOT NULL,
    power_state             VARCHAR(10)      NOT NULL,
    brightness              INTEGER,
    consumption_watts       DOUBLE PRECISION NOT NULL DEFAULT 0,
    critical                BOOLEAN          NOT NULL DEFAULT FALSE,
    presence                BOOLEAN,
    last_presence_at        TIMESTAMPTZ,
    last_status_change_at   TIMESTAMPTZ,
    last_seen_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_devices_home ON devices (home_id);
CREATE INDEX idx_devices_room ON devices (room_id);

CREATE TABLE energy_readings (
    id           BIGSERIAL PRIMARY KEY,
    home_id      UUID             NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    device_id    UUID             NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    watts        DOUBLE PRECISION NOT NULL,
    measured_at  TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_energy_readings_home_time ON energy_readings (home_id, measured_at);
CREATE INDEX idx_energy_readings_device_time ON energy_readings (device_id, measured_at);

CREATE TABLE presence_events (
    id           BIGSERIAL PRIMARY KEY,
    home_id      UUID        NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    room_id      UUID        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    present      BOOLEAN     NOT NULL,
    observed_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_presence_room_time ON presence_events (room_id, observed_at);

CREATE TABLE alerts (
    id                  UUID PRIMARY KEY,
    home_id             UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    room_id             UUID         REFERENCES rooms (id) ON DELETE SET NULL,
    device_id           UUID         REFERENCES devices (id) ON DELETE SET NULL,
    type                VARCHAR(40)  NOT NULL,
    severity            VARCHAR(20)  NOT NULL,
    title               VARCHAR(160) NOT NULL,
    message             TEXT         NOT NULL,
    acknowledged        BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_at     TIMESTAMPTZ,
    acknowledged_by     VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_home_time ON alerts (home_id, created_at);

CREATE TABLE whatsapp_recipients (
    id           UUID PRIMARY KEY,
    home_id      UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    phone_e164   VARCHAR(20)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_whatsapp_recipients_home_phone UNIQUE (home_id, phone_e164)
);

CREATE TABLE whatsapp_messages (
    id                   UUID PRIMARY KEY,
    home_id              UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    alert_id             UUID         REFERENCES alerts (id) ON DELETE SET NULL,
    recipient_phone      VARCHAR(20)  NOT NULL,
    body                 TEXT         NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    attempts             INTEGER      NOT NULL DEFAULT 0,
    last_error           TEXT,
    provider_message_id  VARCHAR(120),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at              TIMESTAMPTZ
);
CREATE INDEX idx_whatsapp_messages_home_time ON whatsapp_messages (home_id, created_at);

CREATE TABLE automation_settings (
    home_id                    UUID PRIMARY KEY REFERENCES homes (id) ON DELETE CASCADE,
    enabled                    BOOLEAN          NOT NULL,
    vacancy_minutes            INTEGER          NOT NULL,
    return_sensitivity_percent INTEGER          NOT NULL,
    consumption_alert_watts    DOUBLE PRECISION NOT NULL,
    leak_minutes               INTEGER          NOT NULL,
    updated_at                 TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE automation_actions (
    id            UUID PRIMARY KEY,
    home_id       UUID             NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    room_id       UUID             REFERENCES rooms (id) ON DELETE SET NULL,
    device_id     UUID             REFERENCES devices (id) ON DELETE SET NULL,
    action_type   VARCHAR(40)      NOT NULL,
    description   TEXT             NOT NULL,
    watts_avoided DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_automation_actions_home_time ON automation_actions (home_id, created_at);

CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    home_id      UUID         NOT NULL REFERENCES homes (id) ON DELETE CASCADE,
    actor        VARCHAR(255) NOT NULL,
    action       VARCHAR(80)  NOT NULL,
    entity_type  VARCHAR(40)  NOT NULL,
    entity_id    VARCHAR(64),
    details      TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_home_time ON audit_logs (home_id, created_at);
