# HomeIA Multimarca

HomeIA controla dispositivos de varias marcas a través de adaptadores independientes:

| Proveedor | Cómo se conecta | Dónde se ejecuta |
|---|---|---|
| Tuya / Smart Life | Nube de Tuya | Backend |
| ESP32 / MQTT HomeIA | Broker MQTT (`homeia/devices/{id}/...`) | Backend |
| Zigbee (Zigbee2MQTT) | Broker LAN + coordinador USB | HomeIA Edge |
| Shelly Gen1 y Gen2/Plus/Pro | HTTP local | HomeIA Edge |
| Tasmota | HTTP local | HomeIA Edge |
| ESPHome | API nativa (6053) | HomeIA Edge |
| WLED | API JSON local | HomeIA Edge |

- Arquitectura, protocolo e instalación del agente: [EDGE.md](EDGE.md)
- Canal seguro, usuarios MQTT, TLS/mTLS y VPN: [SEGURIDAD_MQTT.md](SEGURIDAD_MQTT.md)
- Modelos validados y checklist de pruebas físicas: [MODELOS_CERTIFICADOS.md](MODELOS_CERTIFICADOS.md)
- Lista de verificación de producción: [SEGURIDAD_PRODUCCION.md](SEGURIDAD_PRODUCCION.md)

## Flujo de uso

1. **Ecosistema IoT → HomeIA Edge → Enrolar**: copia `HOMEIA_EDGE_ID` y `HOMEIA_EDGE_SECRET` (se muestran una vez).
2. Instala el Edge en la LAN (`sudo edge/install.sh` o `compose.edge.yaml`) y conecta backend y Edge al mismo
   broker (VPN o broker en la nube con mTLS).
3. El asistente de configuración muestra el diagnóstico paso a paso de cada integración por separado: broker,
   heartbeat de cada Edge, coordinador Zigbee, permit_join, dispositivos detectados y en línea.
4. **Buscar dispositivos** (o **Permitir Zigbee 60 s**) y luego **Incorporar** cada candidato eligiendo nombre,
   tipo y habitación. Descubrir no es emparejar: nada se incorpora sin un administrador.
5. En **Dispositivos** cada equipo muestra solo los controles que soporta (capacidades), su telemetría
   (temperatura, humedad, batería, contacto, energía…), credenciales cifradas y su historial (descubrimiento,
   importación, conexión/desconexión, órdenes confirmadas, fallidas o vencidas y errores).

## Capacidades

Cada orden se valida contra las capacidades del dispositivo (anunciadas por Zigbee `exposes`/descubrimiento o, si no
hay, inferidas por tipo y proveedor) en el backend (`DeviceCapabilityPolicy`), en el Edge y en la interfaz. Por
ejemplo, el brillo solo se envía a luces regulables y un sensor nunca recibe ON/OFF.

## Límites conocidos

- SSDP/mDNS no descubre todos los equipos: hay fabricantes que no anuncian servicios. Se puede registrar o corregir la IP manualmente.
- ESPHome controla la primera luz o interruptor del firmware.
- Zigbee: los comandos dependen de lo que el modelo exponga en Zigbee2MQTT.
- Ningún modelo está validado aún con hardware real: ver [MODELOS_CERTIFICADOS.md](MODELOS_CERTIFICADOS.md).
