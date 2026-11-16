# HomeIA Multimarca y HomeIA Edge

## Estado de esta entrega

Esta implementación amplía el núcleo existente sin sustituir Tuya ni el protocolo MQTT HomeIA:

- V7 añade protocolo, fabricante, modelo, dirección LAN y capacidades al registro de dispositivos.
- `ZigbeeDeviceDriver` envía órdenes a Zigbee2MQTT mediante el broker MQTT existente.
- `MqttGateway` consume estados `zigbee2mqtt/<friendly_name>` y eventos de descubrimiento Edge.
- `ShellyDeviceDriver` y `TasmotaDeviceDriver` envían órdenes por MQTT a HomeIA Edge; el HTTP local se ejecuta dentro de la LAN y no desde Railway.
- `HomeIA Edge` descubre servicios mDNS y respuestas SSDP en la LAN, publica candidatos y ejecuta órdenes locales restringidas a IPv4 privadas.
- El inventario Zigbee extrae capacidades anunciadas por `definition.exposes` y las conserva como metadatos JSON al incorporar el dispositivo.
- Zigbee2MQTT informa los dispositivos de su coordinador al Edge mediante `zigbee2mqtt/bridge/devices`.
- `/api/discovery` permite escanear, consultar candidatos, incorporarlos con autorización y abrir temporalmente permit_join.
- El dashboard `/dashboard/integrations` presenta el estado de descubrimiento y permite incorporar candidatos compatibles.

## Requisitos de red y hardware

HomeIA Edge y el broker deben ejecutarse en un equipo Linux de la misma LAN que los dispositivos Wi-Fi. El contenedor Edge usa `network_mode: host` para que mDNS/SSDP multicast pueda llegar a la LAN. Zigbee requiere coordinador USB compatible y Zigbee2MQTT. La aplicación alojada en Railway no puede ejecutar mDNS/SSDP dentro de la LAN del usuario: el Edge local es el agente que realiza ese trabajo.

## Instalación local (Linux / Raspberry Pi / mini PC)

1. Copiar `data_zigbee_configuration.yaml` a `data/zigbee2mqtt/configuration.yaml` y reemplazar usuario/contraseña de ejemplo por credenciales locales.
2. Crear `.env` local con `MQTT_USERNAME`, `MQTT_PASSWORD` y `ZIGBEE_SERIAL_DEVICE` (ruta real del coordinador).
3. Ejecutar `docker compose -f compose.edge.yaml up -d --build`.
4. Configurar el backend local para conectar a `tcp://<IP_DEL_EDGE>:1883`, `MQTT_ENABLED=true` y las credenciales anteriores.
5. Para backend en Railway, no exponer MQTT directamente a Internet. Establecer un túnel VPN privado entre Railway/VPS y Edge o alojar el backend en la misma red privada. La configuración actual no crea ese túnel automáticamente.
6. Abrir Zigbee2MQTT en `http://<IP_DEL_EDGE>:8081` y comprobar que el coordinador esté iniciado.
7. En HomeIA, entrar a Ecosistema IoT y pulsar Buscar dispositivos.

## Variables

Backend:

- `MQTT_ENABLED=true`
- `MQTT_BROKER_URL=tcp://<IP_EDGE>:1883`
- `MQTT_USERNAME` y `MQTT_PASSWORD`
- `MQTT_CLIENT_ID=homeia-backend`

Edge/Compose:

- `MQTT_USERNAME`, `MQTT_PASSWORD`
- `ZIGBEE_SERIAL_DEVICE=/dev/serial/by-id/<coordinador>`
- `TZ=America/Bogota`

## Compatibilidad y límites actuales

- Tuya conserva el flujo de nube y su importación manual existente.
- Shelly: se soporta el endpoint RPC básico de Switch Gen2/Plus en LAN sin autenticación HTTP. Modelos antiguos o con autenticación requieren un adaptador adicional.
- Tasmota: se soporta el endpoint `/cm` en LAN sin autenticación HTTP. Equipos protegidos por contraseña necesitan credenciales por dispositivo, que aún no se administran en UI.
- Los drivers Wi-Fi necesitan que backend y Edge compartan el broker MQTT. Si el backend está en Railway, hace falta un túnel privado VPN o alojar el backend en la LAN; no se debe publicar el puerto MQTT en Internet.
- Zigbee: la importación usa `friendly_name` de Zigbee2MQTT y el driver soporta ON/OFF/TOGGLE/BRIGHTNESS. No todos los modelos exponen esos comandos; se debe validar `exposes` por modelo antes de activar cada capacidad.
- ESPHome: se detectan anuncios mDNS cuando el firmware los publica, pero no se implementó el protocolo nativo API de ESPHome. Para integrarlo ahora, configurar ESPHome con MQTT y el protocolo MQTT HomeIA, o desarrollar el adaptador nativo posterior.
- SSDP/mDNS no descubre todos los equipos Wi-Fi. Los fabricantes pueden desactivar anuncios o requerir autenticación/app propietaria.
- El descubrimiento no es emparejamiento: todo candidato debe ser incorporado por un administrador.

## Seguridad pendiente antes de producción

El broker conserva autenticación, pero el compose inicial aún usa un usuario/contraseña compartidos para backend, Edge y dispositivos legacy. Para producción deben configurarse usuarios MQTT individuales y ACL por prefijo/identidad, TLS o VPN privada, y credenciales por dispositivo. No publicar el puerto 1883 en Internet. Los drivers HTTP solo admiten IPv4 privada y no siguen redirecciones ni aceptan URLs arbitrarias.

## Fases implementadas en esta entrega

1. Núcleo y esquema multimarca: protocolo, fabricante, modelo, dirección LAN, capacidades JSON, migración V7 y selección de driver.
2. Zigbee2MQTT: driver, recepción de estados, inventario desde el bridge, extracción de capacidades, incorporación manual y apertura/cierre temporal de permit_join.
3. Wi-Fi multimarca: descubrimiento local mDNS/SSDP desde Edge y drivers básicos Shelly Gen2/Plus y Tasmota mediante órdenes MQTT hacia el agente local. Tuya permanece compatible.

La interfaz `/dashboard/integrations` incluye búsqueda, candidatos, capacidades y acciones de incorporación/permit_join. Esta entrega no sustituye las pruebas físicas por modelo.
