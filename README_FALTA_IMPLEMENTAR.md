# HomeIA — Estado de las fases 4, 5 y 6

Las fases 1-3 (núcleo multimarca, Zigbee2MQTT, Wi-Fi Shelly/Tasmota) quedaron integradas y las fases 4-6 se
implementaron en código. Lo que sigue pendiente requiere hardware, infraestructura o personas, no desarrollo.

## Fase 4 — HomeIA Edge seguro y operación remota

- [x] Enrolamiento por hogar con identidad única (`edge_nodes`), rotación con periodo de gracia y revocación inmediata.
- [x] Autenticación mutua: HMAC-SHA256 en ambos sentidos con anti-repetición; credenciales cifradas para cada Edge.
- [x] Canal privado sin exponer MQTT: broker en la nube con mTLS (`compose.broker-cloud.yaml`, `pki.sh`, soporte `ssl://` en backend y Edge) o VPN (guía WireGuard).
- [x] Heartbeat con versión, uptime, cola local, métricas y estado Zigbee; reconexión automática; cola local SQLite.
- [x] Órdenes con `requestId`, expiración, confirmación (`ack`), deduplicación en el Edge y reintentos idempotentes.
- [x] Sincronización de inventario tras reinicio del Edge (`hello` → `sync`) o reconexión del backend; inventario persistente en el Edge.
- [x] Instalador/actualizador systemd endurecido para Linux/Raspberry Pi/mini PC (`edge/install.sh`) e imagen Docker sin root.
- [x] Matriz de plataformas para multicast documentada (docs/EDGE.md).
- [ ] **Pendiente (hardware):** validar el descubrimiento multicast en cada plataforma real.

## Fase 5 — Dashboard e integración guiada

- [x] Asistente por integración (Edge, Zigbee, Shelly, Tasmota, ESPHome, WLED, MQTT, Tuya) con diagnóstico paso a paso.
- [x] Estado separado de broker, cada Edge, cada coordinador Zigbee y cada proveedor.
- [x] Controles por capacidad; acciones no soportadas ocultas en la interfaz y rechazadas por el backend.
- [x] Edición de nombre, habitación, tipo, fabricante, modelo, IP / friendly_name y capacidades; credenciales cifradas.
- [x] Historial por dispositivo (descubrimiento, importación, conexión, órdenes, errores) y actividad de la casa.
- [x] ESPHome por API nativa (aioesphomeapi, cifrado Noise).
- [x] Arquitectura de adaptadores independientes + WLED y Shelly Gen1 como nuevos adaptadores, con pruebas.

## Fase 6 — Pruebas, seguridad y producción

- [x] Pruebas unitarias Java (firma, cifrado, anti-repetición, capacidades, SSRF, reintentos, permit_join, normalizador Zigbee).
- [x] Pruebas de integración Flyway (PostgreSQL) y MQTT (Mosquitto) con Testcontainers (se omiten sin Docker).
- [x] Pruebas del Edge (pytest): adaptadores, Zigbee, deduplicación, cola, firma compartida con Java.
- [x] Usuarios MQTT separados con ACL de mínimo privilegio; TLS/mTLS; 1883 solo LAN.
- [x] Secretos por dispositivo cifrados (AES-256-GCM), nunca en metadatos ni devueltos por la API.
- [x] `permit_join` restringido (≤120 s), auditado, con alertas y cierre verificado.
- [x] Política de capacidades validada antes de cada orden.
- [x] Lectura/confirmación de estado de Shelly, Tasmota, WLED y ESPHome; normalización de sensores Zigbee.
- [x] SSRF (solo IPv4 privadas, sin redirecciones), límites de frecuencia, logs sin secretos, tamaño máximo de mensajes.
- [x] Imágenes y dependencias fijadas; Next.js actualizado a 16.4.6 (aviso GHSA-mfr8-8qww-v76h); backups, retención y métricas Prometheus.
- [ ] **Pendiente (entorno):** ejecutar `./mvnw verify` en un entorno con acceso a Maven Central (ver abajo).
- [ ] **Pendiente (hardware):** validar modelos físicos y completar docs/MODELOS_CERTIFICADOS.md.
- [ ] **Pendiente (terceros):** prueba de penetración externa antes de producción (docs/SEGURIDAD_PRODUCCION.md).

## Estado de validación de esta entrega

| Verificación | Resultado |
|---|---|
| `npx tsc --noEmit` | OK |
| `npx eslint .` | OK |
| `next build` | OK (compilando con la fuente local; en el entorno de trabajo no había acceso a Google Fonts) |
| `npm audit --omit=dev` | 0 vulnerabilidades |
| `pytest` en `edge/` | 27 pruebas OK |
| Pruebas Java sin dependencias externas (firma, cifrado, políticas) | 21 pruebas OK con JDK 21 |
| Interoperabilidad Java ↔ Python (firma y cifrado) | OK, vector compartido |
| Carga de certificados de `pki.sh` en el backend (mTLS) | OK |
| `./mvnw verify` completo | **No ejecutado**: el entorno de trabajo no tenía acceso a Maven Central. Ejecutarlo antes de desplegar. |
