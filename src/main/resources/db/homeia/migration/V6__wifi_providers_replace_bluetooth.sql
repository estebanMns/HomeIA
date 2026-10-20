-- WiFi en lugar de Bluetooth: cada dispositivo indica por dónde se conecta y su id en esa plataforma.
--   MQTT: dispositivos propios (p. ej. ESP32) que hablan el protocolo de HomeIA con un broker.
--   TUYA: dispositivos Tuya / Smart Life, controlados por la nube de Tuya (Tuya IoT Platform).
ALTER TABLE devices
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'MQTT';

ALTER TABLE devices
    ADD COLUMN external_id VARCHAR(64);

-- Un mismo dispositivo de Tuya no se puede importar dos veces en la misma casa.
CREATE UNIQUE INDEX uk_devices_home_provider_external
    ON devices (home_id, provider, external_id)
    WHERE external_id IS NOT NULL;

-- Se retira la vinculación por Bluetooth del navegador.
DROP INDEX IF EXISTS uk_devices_home_bluetooth;
ALTER TABLE devices DROP COLUMN IF EXISTS bluetooth_id;
