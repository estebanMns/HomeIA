-- Enlace entre un dispositivo registrado y su identificador Bluetooth (id del GATT/navegador).
ALTER TABLE devices ADD COLUMN bluetooth_id VARCHAR(120);

CREATE UNIQUE INDEX uk_devices_home_bluetooth
    ON devices (home_id, bluetooth_id)
    WHERE bluetooth_id IS NOT NULL;
