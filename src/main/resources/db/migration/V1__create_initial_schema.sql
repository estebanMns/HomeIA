-- Tabla de habitaciones
CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de dispositivos
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    connection_status VARCHAR(50) NOT NULL,
    power_state VARCHAR(50),
    last_status_change TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    brightness INTEGER,
    consumption DOUBLE PRECISION,
    critical BOOLEAN,
    presence BOOLEAN,
    last_presence_detected TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_id (room_id),
    INDEX idx_type (type),
    INDEX idx_connection_status (connection_status)
);

-- Tabla de alertas
CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    is_sent BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_id (room_id),
    INDEX idx_device_id (device_id),
    INDEX idx_alert_type (alert_type),
    INDEX idx_severity (severity),
    INDEX idx_is_sent (is_sent)
);

-- Tabla de eventos de dominio (para auditoria y replay)
CREATE TABLE domain_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_type (event_type),
    INDEX idx_occurred_at (occurred_at)
);

-- Tabla de consumo histórico (para análisis)
CREATE TABLE energy_consumption_history (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    consumption_watts DOUBLE PRECISION NOT NULL,
    measured_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_measured_at (measured_at)
);
