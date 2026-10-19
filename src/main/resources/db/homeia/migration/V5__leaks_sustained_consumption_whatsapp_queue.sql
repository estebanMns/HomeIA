-- Bloque 2: consumo excesivo sostenido y por dispositivo, enfriamiento de alertas y cola de WhatsApp.

-- Consumo excesivo: minutos que el consumo debe mantenerse sobre el umbral antes de alertar.
ALTER TABLE automation_settings
    ADD COLUMN consumption_sustained_minutes INTEGER NOT NULL DEFAULT 10;

-- Enfriamiento: minutos sin repetir la misma alerta del mismo dispositivo, aunque ya se haya revisado.
ALTER TABLE automation_settings
    ADD COLUMN alert_cooldown_minutes INTEGER NOT NULL DEFAULT 30;

-- Umbral propio del dispositivo (NULL = usar el umbral global de la casa).
ALTER TABLE devices
    ADD COLUMN consumption_alert_watts DOUBLE PRECISION;

-- Cola de envío: los mensajes se guardan como PENDING y un proceso aparte los envía con reintentos.
ALTER TABLE whatsapp_messages
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

UPDATE whatsapp_messages SET next_attempt_at = created_at WHERE status = 'PENDING';

CREATE INDEX idx_whatsapp_messages_queue ON whatsapp_messages (status, next_attempt_at);
CREATE INDEX idx_alerts_cooldown ON alerts (home_id, device_id, type, created_at);
