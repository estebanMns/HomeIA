# Protocolo Bluetooth de HomeIA

La app web se conecta a los dispositivos por **Bluetooth Low Energy (GATT)** mediante la Web Bluetooth API.
Cualquier dispositivo (ESP32, nRF52, etc.) que implemente este contrato aparece en el selector de la app.

## Requisitos del navegador

- Chrome o Edge en escritorio o Android, con HTTPS (o `localhost` en desarrollo).
- Safari (iPhone/Mac) y Firefox **no** soportan Web Bluetooth. Para iPhone se necesita una app nativa.
- La búsqueda requiere un clic del usuario y muestra el selector del navegador; no hay escaneo en segundo plano.

## Identificadores

| Elemento | UUID |
|---|---|
| Servicio HomeIA | `776661c5-ac9a-4ea3-808f-f13b7fece3a4` |
| Característica de comando (escritura) | `5e3cedfc-4c0f-49b8-ae9b-085d645b9db3` |
| Característica de estado (lectura + notificación) | `b86d2ede-fbc8-46b3-926d-90338dcd912f` |

El nombre anunciado debe empezar por `HomeIA` (por ejemplo `HomeIA-Foco-Sala`) para aparecer en el filtro.

## Comando (escritura en la característica de comando)

JSON en UTF-8:

```json
{"command": "ON", "brightness": 80}
```

`command` puede ser `ON`, `OFF`, `TOGGLE`, `BRIGHTNESS` o `STATUS`. `brightness` (0–100) solo aplica a `BRIGHTNESS`.
El dispositivo debe responder actualizando su característica de estado.

## Estado (lectura y notificaciones de la característica de estado)

JSON en UTF-8:

```json
{"powerState": "ON", "consumptionWatts": 12.5, "brightness": 80, "presence": true}
```

- `powerState`: `ON` u `OFF`.
- `consumptionWatts`: consumo instantáneo en vatios.
- `brightness`: 0–100 (solo luces).
- `presence`: `true` si el sensor detecta presencia (solo sensores).

Todos los campos son opcionales excepto los que el dispositivo pueda medir. Notifica el estado cada vez que cambie.

## Integración con el backend

La app envía cada estado recibido a `POST /api/devices/{id}/state` con el mismo formato, así que el consumo,
la presencia y el estado quedan guardados igual que los que llegan por MQTT, y la automatización los usa.

Al conectar un dispositivo nuevo, la app lo registra con `POST /api/devices/bluetooth` usando su identificador
Bluetooth. Si ya existe un dispositivo con ese identificador en la casa, se vincula sin duplicarlo.

## Limitaciones conocidas

- La conexión Bluetooth solo existe mientras la página esté abierta en un navegador compatible.
  Los dispositivos que deban funcionar sin la app siguen usando MQTT.
- El apagado automático de la IA usa MQTT. Los dispositivos solo-Bluetooth no reciben comandos de la automatización
  mientras no haya una app conectada.
