-- Registro multimarca compatible con dispositivos ya existentes.
ALTER TABLE devices ADD COLUMN protocol VARCHAR(16) NOT NULL DEFAULT 'MQTT';
ALTER TABLE devices ADD COLUMN manufacturer VARCHAR(100);
ALTER TABLE devices ADD COLUMN model VARCHAR(120);
ALTER TABLE devices ADD COLUMN capabilities_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE devices ADD COLUMN network_address VARCHAR(64);

-- Provider + external_id ya identifica Tuya. Este índice permite identificadores externos
-- de otros adaptadores sin duplicarlos dentro de una misma casa.
CREATE UNIQUE INDEX IF NOT EXISTS uk_devices_home_provider_external
    ON devices (home_id, provider, external_id) WHERE external_id IS NOT NULL;

UPDATE devices SET protocol = CASE
    WHEN provider = 'TUYA' THEN 'CLOUD'
    WHEN provider = 'ZIGBEE' THEN 'ZIGBEE'
    ELSE 'MQTT' END;
