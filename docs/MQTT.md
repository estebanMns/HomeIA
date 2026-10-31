# Broker MQTT con Docker

Los dispositivos propios (p. ej. un ESP32) se comunican con HomeIA por MQTT. Los de Tuya / Smart Life no lo necesitan.

## Puesta en marcha

1. Instala Docker Desktop y ábrelo.
2. En el `.env` deben estar `MQTT_USERNAME` y `MQTT_PASSWORD`. Son las credenciales del broker y las usan tanto Mosquitto como el backend.
3. Levanta el broker desde la raíz del proyecto:

   ```
   docker compose -f compose.mqtt.yaml up -d
   ```

4. En el `.env`, cambia `MQTT_ENABLED=false` por `MQTT_ENABLED=true` (con `MQTT_BROKER_URL=tcp://localhost:1883`) y reinicia el backend.
5. Comprueba la conexión:
   - en el log del backend aparecen `Conectado al broker MQTT` y `Suscrito a homeia/devices/+/state`;
   - en **Estado del sistema**, el broker MQTT aparece como `CONNECTED`.

El contenedor se reinicia solo con Docker (`restart: unless-stopped`). Para cambiar la contraseña, edita el `.env` y ejecuta `docker compose -f compose.mqtt.yaml up -d --force-recreate`.

## Protocolo de los dispositivos

Cada dispositivo usa el id que HomeIA le asigna al crearlo en **Dispositivos**.

**Órdenes**: el dispositivo se suscribe a `homeia/devices/{id}/command` y recibe:

```json
{"command": "ON", "brightness": 80, "requestedAt": "2026-10-30T04:00:00Z", "requestedBy": "..."}
```

Comandos: `ON`, `OFF`, `TOGGLE`, `BRIGHTNESS` (con `brightness` 0–100) y `STATUS` (pide que reporte su estado).

**Estado**: el dispositivo publica en `homeia/devices/{id}/state`:

```json
{"powerState": "ON", "consumptionWatts": 12.5, "brightness": 80, "presence": true}
```

Todos los campos son opcionales. `presence` lo envían los sensores de presencia: es lo que usa la automatización para saber si una habitación está vacía.

Los dispositivos de la red local se conectan a `<IP de este equipo>:1883` con el mismo usuario y contraseña.

## Probar sin un dispositivo físico

Escuchar los comandos que envía HomeIA:

```
docker exec -it homeia-mosquitto mosquitto_sub -u homeia -P <contraseña> -t "homeia/devices/+/command" -v
```

Responder como si fueras el dispositivo:

```
docker exec homeia-mosquitto mosquitto_pub -u homeia -P <contraseña> -t "homeia/devices/<id>/state" -m "{\"powerState\":\"ON\",\"consumptionWatts\":60}"
```
